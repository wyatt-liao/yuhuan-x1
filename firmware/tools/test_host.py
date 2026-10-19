#!/usr/bin/env python3
"""Run portable recording tests without an ESP board; optionally verify WAV via FFmpeg."""
import argparse
import pathlib
import shutil
import subprocess
import tempfile
import math
import struct
import wave

ROOT = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--build-dir', type=pathlib.Path)
args = p.parse_args()
build = (args.build_dir or pathlib.Path(tempfile.mkdtemp(prefix='yuhuan-host-'))).resolve()
build.mkdir(parents=True, exist_ok=True)
core = ROOT / 'components/yh_core'
subprocess.run(['c++', '-std=c++17', '-Wall', '-Wextra', '-Werror', '-O1', '-g',
                '-fsanitize=address,undefined', '-fno-sanitize-recover=all',
                '-I'+str(core/'include'), str(core/'codec.cpp'), str(core/'store.cpp'),
                str(ROOT/'tests/core_test.cpp'), '-o', str(build/'core_test')], check=True)
subprocess.run([str(build/'core_test'), str(build/'fixtures')], check=True)
ffmpeg = shutil.which('ffmpeg')
if ffmpeg:
    subprocess.run([ffmpeg, '-v', 'error', '-y', '-i', str(build/'fixtures/fixture.wav'),
                    '-c:a', 'pcm_s16le', str(build/'fixture-decoded.wav')], check=True)
    with wave.open(str(build/'fixture-decoded.wav'), 'rb') as decoded:
        assert decoded.getframerate() == 16000 and decoded.getnchannels() == 1
        raw = decoded.readframes(decoded.getnframes())
    pcm = struct.unpack('<'+'h'*(len(raw)//2), raw)[505:-505]
    assert len(pcm) > 1000
    sine = [math.sin(2*math.pi*440*i/16000) for i in range(len(pcm))]
    cosine = [math.cos(2*math.pi*440*i/16000) for i in range(len(pcm))]
    a = 2*sum(x*s for x,s in zip(pcm,sine))/len(pcm)
    b = 2*sum(x*c for x,c in zip(pcm,cosine))/len(pcm)
    signal = [a*s+b*c for s,c in zip(sine,cosine)]
    snr = 10*math.log10(sum(x*x for x in signal)/sum((x-y)**2 for x,y in zip(pcm,signal)))
    assert 8500 < math.hypot(a,b) < 11000 and snr > 20, (a,b,snr)
    print(f'PASS independent FFmpeg WAV decoding, 440Hz tone SNR {snr:.1f} dB')
else:
    print('SKIP independent FFmpeg decoding: ffmpeg is not installed')
