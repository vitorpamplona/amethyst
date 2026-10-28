#!/usr/bin/env bash
# Builds kdroidFilter's macOS engine with the probe driver and plays the reported video every which way.
set -u
cd "$(dirname "$0")"
work=$(mktemp -d)
curl -sSfL -o "$work/NativeVideoPlayer.swift" \
  https://raw.githubusercontent.com/kdroidFilter/ComposeMediaPlayer/67ae1dce6ae4924de19bd3b0b96d66c4758dc921/mediaplayer/src/jvmMain/native/macos/NativeVideoPlayer.swift
swiftc -O "$work/NativeVideoPlayer.swift" main.swift -o "$work/probe" || exit 1

URL=https://cdn.nostrcheck.me/caa572115318508a273a8fdd7bad3d66bfcb4bc7856684edcae83343205d5fe3.mp4
media="$work/media"; mkdir -p "$media"
curl -sSfL -o "$media/orig.mp4" "$URL"
ffmpeg -v error -y -i "$media/orig.mp4" -c copy -movflags +faststart "$media/faststart.mp4"
ffmpeg -v error -y -i "$media/orig.mp4" -c:v libx264 -profile:v high -bf 0 -c:a copy "$media/nobframes.mp4"
ffmpeg -v error -y -i "$media/orig.mp4" -c:v copy -c:a aac -profile:a aac_low "$media/aaclc.mp4"
ffmpeg -v error -y -i "$media/orig.mp4" -c:v copy -an "$media/noaudio.mp4"
ffmpeg -v error -y -i "$media/orig.mp4" -c:v libx264 -profile:v high -bf 2 -c:a aac "$media/reencoded.mp4"
ls -la "$media"

python3 range_server.py 8765 "$media" 2> "$work/server.log" &
server=$!
sleep 1

uris=("$URL")
for f in orig reencoded; do
  uris+=("$media/$f.mp4" "http://127.0.0.1:8765/$f.mp4")
done
"$work/probe" "${uris[@]}"
status=$?
kill $server
echo "--- server requests"
cat "$work/server.log"
exit $status
