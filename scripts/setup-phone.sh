#!/usr/bin/env bash
# Sets up PixelWhisper on the phone connected by USB: build and install, speech models,
# mic permission, accessibility service. Safe to re-run. Usage: scripts/setup-phone.sh
# (set ANDROID_SERIAL when more than one device is connected).
set -euo pipefail
cd "$(dirname "$0")/.."

[ -x "${JAVA_HOME:-}/bin/java" ] || export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
CACHE="$HOME/.cache/pixelwhisper/models"
PKG=com.pixelwhisper
SERVICE=$PKG/.DictationService
REMOTE=/sdcard/Android/data/$PKG/files/models
WHISPER=https://huggingface.co/csukuangfj/sherpa-onnx-whisper-turbo/resolve/2ca6ff69fc878651b770880507669577ac41c2ff
SHERPA=https://github.com/k2-fsa/sherpa-onnx/releases/download

# fetch URL SHA256 DEST: download once, then only verify.
fetch() {
  if [ -f "$3" ] && echo "$2  $3" | shasum -a 256 -c --status; then return; fi
  echo "Downloading $(basename "$3")"
  curl -fL --retry 3 -o "$3.part" "$1"
  echo "$2  $3.part" | shasum -a 256 -c --status || { echo "Checksum mismatch: $1" >&2; exit 1; }
  mv "$3.part" "$3"
}

mkdir -p "$CACHE" app/libs
fetch $SHERPA/v1.13.8/sherpa-onnx-1.13.8.aar 633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96 app/libs/sherpa-onnx-1.13.8.aar
fetch $WHISPER/turbo-encoder.int8.onnx b02dcdf54f348741e93fe732b67d933c8dcb6735655f710640143081db38878b "$CACHE/turbo-encoder.int8.onnx"
fetch $WHISPER/turbo-decoder.int8.onnx 20accd02388482eb3a46bd615631adfdc85e1eb2c7db9ea3f02a40ffe6b81547 "$CACHE/turbo-decoder.int8.onnx"
fetch $WHISPER/turbo-tokens.txt b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126 "$CACHE/turbo-tokens.txt"
fetch $SHERPA/asr-models/silero_vad.onnx 9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6 "$CACHE/silero_vad.onnx"

./gradlew -q installDebug

adb shell mkdir -p $REMOTE
adb push --sync "$CACHE"/turbo-encoder.int8.onnx "$CACHE"/turbo-decoder.int8.onnx "$CACHE"/turbo-tokens.txt "$CACHE"/silero_vad.onnx $REMOTE/
adb shell pm grant $PKG android.permission.RECORD_AUDIO

# Enable the service and keep every other enabled accessibility service (drops only stale PixelWhisper entries).
cur=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
[ "$cur" = null ] && cur=
others=$(echo "$cur" | tr ':' '\n' | grep -v -e '^$' -e "^$PKG/" || true)
new=$(printf '%s\n%s\n' "$others" "$SERVICE" | grep -v '^$' | paste -sd: -)
[ "$new" = "$cur" ] || adb shell settings put secure enabled_accessibility_services "$new"
adb shell settings put secure accessibility_enabled 1
echo "Done. Focus any text field: the mic orb appears on the right edge."
