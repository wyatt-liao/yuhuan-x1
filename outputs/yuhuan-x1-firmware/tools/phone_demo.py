#!/usr/bin/env python3
"""Local phone-backend emulator. No cloud service or API keys required."""
import argparse
import asyncio
import json
import os
from pathlib import Path
import struct
import time
import urllib.request
import zlib

SERVICE = "8d4a0001-5c61-4d3d-a34b-e271e6fa1001"
CONTROL = "8d4a0002-5c61-4d3d-a34b-e271e6fa1001"
DATA = "8d4a0003-5c61-4d3d-a34b-e271e6fa1001"


class Frames:
    """One serialized request at a time, exact offsets and per-packet CRC."""
    def __init__(self, request, offset=0):
        self.request = request
        self.offset = offset
        self.data = bytearray()
        self.kind = None
        self.error = False
        self.done = False

    def append(self, packet):
        if len(packet) < 12:
            raise ValueError("short BLE frame")
        kind, flags, request, offset, checksum = struct.unpack("<BBHII", packet[:12])
        payload = packet[12:]
        if request != self.request:
            return  # Ignore delayed frames belonging to an expired request.
        if kind not in (1, 2) or self.done:
            raise ValueError("invalid frame kind/state")
        if self.kind is None:
            self.kind = kind
            if kind == 1:
                self.offset = 0  # JSON errors use their own zero-based offset.
        if kind != self.kind or offset != self.offset + len(self.data):
            raise ValueError("BLE stream offset mismatch; retry chunk")
        if zlib.crc32(payload) & 0xFFFFFFFF != checksum:
            raise ValueError("BLE frame CRC mismatch; retry chunk")
        self.data.extend(payload)
        self.error |= bool(flags & 2)
        self.done = bool(flags & 1)


class Ble:
    def __init__(self, address=None):
        self.address = address
        self.client = None
        self.frames = None
        self.future = None
        self.request_id = 0

    async def __aenter__(self):
        from bleak import BleakClient, BleakScanner
        if self.address:
            device = await BleakScanner.find_device_by_address(self.address, timeout=15)
        else:
            device = await BleakScanner.find_device_by_filter(
                lambda d, a: SERVICE in [u.lower() for u in a.service_uuids], timeout=15)
        if not device:
            raise RuntimeError("Yuhuan X1 not found")
        self.client = BleakClient(device)
        await self.client.connect()
        # macOS initiates pairing when the encrypted control characteristic is written.
        await self.client.start_notify(DATA, self.notify)
        return self

    async def __aexit__(self, *_):
        if self.client:
            await self.client.disconnect()

    def notify(self, _, packet):
        if not self.future or self.future.done():
            return
        try:
            self.frames.append(packet)
            if self.frames.done:
                self.future.set_result(self.frames)
        except Exception as error:
            self.future.set_exception(error)

    async def command(self, command):
        self.request_id = self.request_id % 65535 + 1
        obj = dict(command, req=self.request_id)
        self.frames = Frames(self.request_id, obj.get("offset", 0))
        self.future = asyncio.get_running_loop().create_future()
        body = (json.dumps(obj, separators=(",", ":"), ensure_ascii=True) + "\n").encode()
        if len(body) > 513:
            raise ValueError("command exceeds 512-byte JSON limit")
        mtu = max(23, min(self.client.mtu_size, 247))
        for offset in range(0, len(body), mtu - 3):
            await self.client.write_gatt_char(CONTROL, body[offset:offset + mtu - 3], response=True)
        frames = await asyncio.wait_for(self.future, timeout=45)
        if frames.kind == 2:
            return bytes(frames.data)
        result = json.loads(frames.data)
        if frames.error or not result.get("ok"):
            raise RuntimeError(result.get("error", "device rejected command"))
        return result


class Http:
    def __init__(self, base, token):
        self.base = base.rstrip("/")
        self.token = token

    def request(self, path, body=None, headers=None):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.base + path, data=data, headers={
            "Authorization": "Bearer " + self.token,
            "Content-Type": "application/json", **(headers or {})})
        return urllib.request.urlopen(request, timeout=45)

    async def command(self, command):
        def run():
            with self.request("/v1/control", command) as response:
                result = json.load(response)
                if not result.get("ok"):
                    raise RuntimeError(result.get("error", "device rejected command"))
                return result
        return await asyncio.to_thread(run)

    async def download(self, item, partial, offset):
        def run():
            headers = {"Range": f"bytes={offset}-"} if offset else {}
            with self.request("/v1/audio?id=" + item["id"], headers=headers) as response:
                if offset and response.status != 206:
                    raise RuntimeError("device did not honor resume offset")
                with partial.open("ab" if offset else "wb") as file:
                    while True:
                        data = response.read(65536)
                        if not data:
                            break
                        file.write(data)
                    file.flush()
                    os.fsync(file.fileno())
        await asyncio.to_thread(run)


def validate_wav(path, size):
    if path.stat().st_size != size:
        raise ValueError("download size mismatch")
    with path.open("rb") as file:
        header = file.read(60)
    if len(header) != 60 or header[:4] != b"RIFF" or header[8:12] != b"WAVE" or struct.unpack_from("<H", header, 20)[0] != 0x11:
        raise ValueError("invalid IMA ADPCM WAV")


async def sync(transport, directory):
    directory.mkdir(parents=True, exist_ok=True)
    await transport.command({"cmd": "time", "epoch_ms": int(time.time() * 1000)})
    status = await transport.command({"cmd": "status"})
    # Avoid saving the Wi-Fi token in the recording library or debug manifest.
    status.pop("wifi_token", None)
    (directory / "device-status.json").write_text(json.dumps(status, ensure_ascii=False, indent=2))
    after = "0000000000000000"
    while True:
        result = await transport.command({"cmd": "list", "after": after, "limit": 16})
        items = result["segments"]
        if not items:
            break
        for item in items:
            identity = item["id"]
            if len(identity) != 16 or any(c not in "0123456789abcdef" for c in identity):
                raise ValueError("invalid segment id")
            final = directory / (identity + ".wav")
            partial = directory / (identity + ".part")
            try:
                if final.exists():
                    validate_wav(final, item["bytes"])
                else:
                    offset = partial.stat().st_size if partial.exists() else 0
                    if offset > item["bytes"]:
                        partial.unlink()
                        offset = 0
                    if isinstance(transport, Http):
                        if offset < item["bytes"]:
                            await transport.download(item, partial, offset)
                    else:
                        with partial.open("ab") as file:
                            while offset < item["bytes"]:
                                data = await transport.command({"cmd": "read", "id": identity,
                                    "offset": offset, "length": min(4096, item["bytes"] - offset)})
                                if not isinstance(data, bytes) or not data or len(data) > item["bytes"] - offset:
                                    raise ValueError("invalid read response")
                                file.write(data)
                                file.flush()
                                os.fsync(file.fileno())
                                offset += len(data)
                    validate_wav(partial, item["bytes"])
                    partial.replace(final)
                await transport.command({"cmd": "ack", "id": identity, "bytes": item["bytes"]})
                (directory / (identity + ".json")).write_text(json.dumps(item, indent=2))
                print("saved", final.name, item["bytes"], "bytes", flush=True)
            except Exception as error:
                print("pending", identity, str(error), flush=True)
        after = items[-1]["id"]
    marks = await transport.command({"cmd": "marks"})
    (directory / "marks.json").write_text(json.dumps(marks, ensure_ascii=False, indent=2))


async def run(args):
    while True:
        try:
            if args.transport == "ble":
                async with Ble(args.address) as transport:
                    if args.command:
                        print(json.dumps(await transport.command(json.loads(args.command)), indent=2))
                    else:
                        await sync(transport, args.out)
            else:
                transport = Http(args.url, args.token)
                if args.command:
                    print(json.dumps(await transport.command(json.loads(args.command)), indent=2))
                else:
                    await sync(transport, args.out)
        except Exception:
            if not args.watch:
                raise
            import traceback
            traceback.print_exc()
        if not args.watch:
            return
        await asyncio.sleep(30)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("transport", choices=["ble", "http"])
    parser.add_argument("--address", help="optional BLE address/UUID; otherwise discover X1")
    parser.add_argument("--url", default="http://192.168.1.2", help="device LAN address")
    parser.add_argument("--token", default="", help="token read over paired/encrypted BLE")
    parser.add_argument("--out", type=Path, default=Path("recordings"))
    parser.add_argument("--command", help="send one JSON command instead of syncing")
    parser.add_argument("--watch", action="store_true", help="reconnect/sync every 30 seconds while this program runs")
    asyncio.run(run(parser.parse_args()))
