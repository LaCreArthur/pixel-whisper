# PixelWhisper

On-device voice-to-text for Android. Speak → transcribe → polish → inject into any text field.

**Stack:** Moonshine Voice (ASR) + Gemini Nano via ML Kit GenAI (polish) + AccessibilityService (injection). 100% on-device, zero cloud.

## Setup

### Quick setup

```bash
chmod +x setup.sh && ./setup.sh
```

This handles everything: Gradle wrapper, model download, build, install, and accessibility setup.

### Manual setup

**1. Gradle wrapper** (if not present):
```bash
gradle wrapper --gradle-version 8.11.1
```

**2. Moonshine model files** (already included if you ran setup.sh):
```bash
python3 -m venv /tmp/moonshine-env
source /tmp/moonshine-env/bin/activate
pip install moonshine-voice
python3 -m moonshine_voice.download --language en
cp ~/Library/Caches/moonshine_voice/download.moonshine.ai/model/medium-streaming-en/quantized/* \
   app/src/main/assets/medium-streaming-en/
```

**3. Build & install:**
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

**4. Enable Accessibility Service** (sideloaded apps need ADB bypass on Android 15):
```bash
adb shell settings put secure enabled_accessibility_services com.pixelwhisper/.TextInjectionService
```

## Usage

1. Open PixelWhisper → grant overlay + mic permissions
2. Tap **Launch PixelWhisper** → floating blue orb appears
3. Tap orb → starts recording (orb turns red, pulses)
4. Speak freely
5. Tap orb again → transcribes → polishes → injects text
6. If a text field is focused → text appears there
7. If nothing is focused → copied to clipboard

## Architecture

```
FloatingOrbService (Compose overlay + foreground service)
    ├── TranscriptionEngine (Moonshine Voice + AudioRecord)
    ├── PolishEngine (ML Kit Proofreading + Prompt API)
    └── TextInjectionService (AccessibilityService)
```

## Requirements

- Pixel 10 (or any device with Gemini Nano support)
- Android 15 (API 35)
- ~290MB for Moonshine medium-streaming model + ~100MB for Gemini Nano (auto-downloaded)
