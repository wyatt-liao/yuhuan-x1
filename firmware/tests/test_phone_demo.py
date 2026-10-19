import asyncio
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

spec = importlib.util.spec_from_file_location("phone_demo", Path(__file__).parents[1] / "tools/phone_demo.py")
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)


def packet(payload, offset=0, flags=0, request=1, kind=2):
    return struct.pack("<BBHII", kind, flags, request, offset, zlib.crc32(payload)) + payload


class FramingTests(unittest.TestCase):
    def test_split_resume_and_crc(self):
        frames = demo.Frames(1, 4096)
        frames.append(packet(b"abc", 4096))
        frames.append(packet(b"def", 4099, flags=1))
        self.assertTrue(frames.done)
        self.assertEqual(frames.data, b"abcdef")
        with self.assertRaises(ValueError):
            demo.Frames(1).append(packet(b"abc", 1))
        damaged = bytearray(packet(b"abc"))
        damaged[-1] ^= 1
        with self.assertRaises(ValueError):
            demo.Frames(1).append(damaged)

    def test_late_request_and_json_error_offset(self):
        frames = demo.Frames(1, 8000)
        frames.append(packet(b"old", request=2, flags=1))
        self.assertFalse(frames.done)
        frames.append(packet(b'{"ok":false}', kind=1, flags=3))
        self.assertTrue(frames.error)
        self.assertEqual(frames.offset, 0)

    def test_sync_resume_and_ack(self):
        header = bytearray(60)
        header[:4] = b"RIFF"
        header[8:12] = b"WAVE"
        struct.pack_into("<H", header, 20, 0x11)
        audio = bytes(header) + bytes(range(256))
        identity = "0000000000000001"

        class Device:
            def __init__(self):
                self.offsets = []
                self.acks = []
                self.truncate = False

            async def command(self, cmd):
                name = cmd["cmd"]
                if name == "status":
                    return {"ok": True, "wifi_token": "private"}
                if name == "list":
                    items = [] if cmd["after"] != "0000000000000000" else [{"id": identity, "bytes": len(audio)}]
                    return {"ok": True, "segments": items}
                if name == "read":
                    self.offsets.append(cmd["offset"])
                    return b"" if self.truncate else audio[cmd["offset"]:cmd["offset"] + cmd["length"]]
                if name == "ack":
                    self.acks.append(cmd)
                return {"ok": True}

        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / (identity + ".part")).write_bytes(audio[:100])
            device = Device()
            asyncio.run(demo.sync(device, directory))
            self.assertEqual(device.offsets, [100])
            self.assertEqual(len(device.acks), 1)
            self.assertEqual((directory / (identity + ".wav")).read_bytes(), audio)
            self.assertNotIn("private", (directory / "device-status.json").read_text())
            (directory / (identity + ".wav")).unlink()
            device.acks.clear()
            device.truncate = True
            asyncio.run(demo.sync(device, directory))
            self.assertEqual(device.acks, [])
            self.assertFalse((directory / (identity + ".wav")).exists())


if __name__ == "__main__":
    unittest.main()
