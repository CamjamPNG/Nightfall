#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#
# Regenerates `docs/samples/`: renders each clip through the app's own mixer and encodes it to Opus.
#
# Run from anywhere. This is documentation tooling, not part of the build gate: it needs a JVM and
# `ffmpeg`, and `./gradlew :core:test` is what notices clips that no longer match the engine
# (`SampleClipTest`), so a machine that only runs the gate never has to encode anything.
set -euo pipefail

cd "$(dirname "$0")/.."

# Opus in Ogg at 64 kbps joint stereo. Measured on the quietest clip (`rain-on-tent`), against the
# render it came from: Opus at 64k keeps the integrated level within 0.6 LU and costs 141 kB, while MP3
# at 96k moves it 2.1 LU and costs 241 kB, and MP3 at 128k still moves it 1.4 LU for 321 kB. A sample
# whose level is wrong by two decibels cannot honestly demonstrate an engine that trims every source
# to one loudness, so the format that keeps the level wins; it is also the smallest.
BITRATE="${NIGHTFALL_SAMPLE_BITRATE:-64k}"
WAV_DIR="core/build/samples"
OUT_DIR="docs/samples"

command -v ffmpeg >/dev/null || {
    echo "ffmpeg is required to encode the samples" >&2
    exit 1
}

./gradlew --console=plain :core:renderSamples

mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR"/*.ogg

printf '%-30s %8s %10s %9s %9s %9s\n' clip seconds kB kbps peak_dBFS LUFS
total=0
for wav in "$WAV_DIR"/*.wav; do
    id="$(basename "$wav" .wav)"
    ogg="$OUT_DIR/$id.ogg"
    # One stereo 48 kHz Opus stream: the mixer's own rate, its own two channels, no resampling and no
    # metadata. `-vbr on` is Opus's default and the reason a quiet passage costs fewer bytes.
    ffmpeg -hide_banner -loglevel error -y -i "$wav" \
        -map_metadata -1 -c:a libopus -b:a "$BITRATE" -vbr on -ac 2 -ar 48000 "$ogg"
    bytes="$(stat -c %s "$ogg")"
    total=$((total + bytes))
    duration="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$ogg")"
    kbps="$(awk -v b="$bytes" -v d="$duration" 'BEGIN { printf "%.1f", b * 8 / d / 1000 }')"
    stats="$(ffmpeg -hide_banner -nostats -i "$ogg" -af "ebur128=peak=true" -f null - 2>&1 |
        awk '/Integrated loudness:/{i=1} i && /I:/{loud=$2} /True peak:/{p=1} p && /Peak:/{peak=$2; exit} END{printf "%.2f %.2f", peak, loud}')"
    printf '%-30s %8.2f %10.1f %9s %9s %9s\n' \
        "$id" "$duration" "$(awk -v b="$bytes" 'BEGIN{printf "%.1f", b/1024}')" "$kbps" \
        "${stats% *}" "${stats#* }"
done
printf 'total: %d bytes (%.2f MB)\n' "$total" "$(awk -v b="$total" 'BEGIN{printf "%.3f", b/1048576}')"
