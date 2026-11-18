#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Pack ffmpeg's Opus output into the NFOP container the app reads.

`NFOP`: magic, version, channel count, sample rate (u16), total samples (u32), packet count (u32),
table length in bytes (u32), codec configuration length (u32), then the codec configuration (the Opus
identification header, which `MediaFormat` wants as `csd-0`), then one `(start sample, byte length)`
pair of u32 per packet, then the Opus packets back to back. Sample counts are in the file's own rate, which is 24 kHz, so the mixer
does not have to resample or guess. Opus itself decodes packets at 48 kHz; `OpusPackets.outputRate`
carries that, and a cursor averages the pairs back down to the container's rate.

Used by `tools/prepare_audio.py` and runnable on one file:

    python3 tools/nfop.py encode recording.pcm recording.nfop
    python3 tools/nfop.py verify recording.nfop
"""
import argparse
import pathlib
import struct
import subprocess
import sys
import tempfile

RATE = 24000
OPUS_RATE = 48000
MIN_SECONDS = 180
BITRATE = "48k"

# Frame duration in milliseconds for every TOC config, then the frame count for each code.
FRAME_MS = (
    [10, 20, 40, 60] * 3 + [10, 20] * 2 + [2.5, 5, 10, 20] * 4
)


def packet_samples(packet: bytes) -> int:
    """Samples at 24 kHz in one Opus packet, read from its table-of-contents byte."""
    toc = packet[0]
    config = toc >> 3
    code = toc & 3
    frames = [1, 2, 2, packet[1] & 0x3F][code]
    return int(round(FRAME_MS[config] * frames * RATE / 1000))


def ogg_packets(path: pathlib.Path):
    data = path.read_bytes()
    at = 0
    packet = bytearray()
    while at < len(data):
        assert data[at:at + 4] == b"OggS", "not an Ogg page"
        segments = data[at + 26]
        table = data[at + 27:at + 27 + segments]
        body = at + 27 + segments
        for size in table:
            packet += data[body:body + size]
            body += size
            if size < 255:
                yield bytes(packet)
                packet = bytearray()
        at = body
    assert not packet, "a packet ran past the last page"


def encode(pcm: pathlib.Path, target: pathlib.Path) -> dict:
    head = b""
    with tempfile.TemporaryDirectory() as work:
        ogg = pathlib.Path(work) / "encoded.ogg"
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(RATE), "-ac", "1",
             "-i", str(pcm), "-c:a", "libopus", "-b:a", BITRATE, "-frame_duration", "20",
             "-ar", str(RATE), "-ac", "1", "-application", "audio", str(ogg)],
            check=True,
        )
        stream = list(ogg_packets(ogg))
        head, packets = stream[0], stream[2:]  # OpusHead is the decoder's configuration
    starts, lengths, offset = [], [], 0
    for packet in packets:
        starts.append(offset)
        lengths.append(len(packet))
        offset += packet_samples(packet)
    total = offset
    table = b"".join(struct.pack("<II", s, n) for s, n in zip(starts, lengths))
    header = struct.pack("<IBBHIIII", 0x50464F4E, 1, 1, RATE, total, len(packets), len(table), len(head))
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("wb") as out:
        out.write(header)
        out.write(head)
        out.write(table)
        for packet in packets:
            out.write(packet)
    seconds = total / RATE
    assert seconds >= MIN_SECONDS, f"{target.name} runs {seconds:.1f} seconds"
    return {"packets": len(packets), "samples": total, "seconds": seconds, "bytes": target.stat().st_size}


def parse(data: bytes) -> dict:
    magic, version, channels, rate, total, count, table_bytes, head_bytes = struct.unpack(
        "<IBBHIIII", data[:24]
    )
    assert magic == 0x50464F4E, "not an NFOP file"
    assert version == 1 and channels == 1 and rate == RATE, "unexpected NFOP header"
    head = data[24:24 + head_bytes]
    assert head.startswith(b"OpusHead"), "NFOP has no Opus identification header"
    packets, offset = [], 24 + head_bytes + table_bytes
    for i in range(count):
        start, length = struct.unpack(
            "<II", data[24 + head_bytes + i * 8: 32 + head_bytes + i * 8]
        )
        packets.append((start, data[offset:offset + length]))
        offset += length
    assert offset == len(data), "NFOP length disagrees with its table"
    return {"total": total, "packets": packets, "head": head}


def read(path: pathlib.Path) -> dict:
    return parse(path.read_bytes())


def verify_bytes(data: bytes) -> dict:
    parsed = parse(data)
    position = 0
    for start, packet in parsed["packets"]:
        assert start == position, f"packet at sample {start} but {position} was expected"
        position += packet_samples(packet)
    assert position == parsed["total"], "samples in packets disagree with the header"
    assert position >= MIN_SECONDS * RATE, "NFOP file is shorter than three minutes"
    return {"seconds": position / RATE, "packets": len(parsed["packets"]), "head": parsed["head"]}


def verify(path: pathlib.Path) -> dict:
    return verify_bytes(path.read_bytes())


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["encode", "verify"])
    parser.add_argument("source", type=pathlib.Path)
    parser.add_argument("target", type=pathlib.Path, nargs="?")
    args = parser.parse_args()
    if args.action == "encode":
        assert args.target is not None, "encode needs a target path"
        print(encode(args.source, args.target), file=sys.stderr)
    else:
        print(verify(args.source), file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
