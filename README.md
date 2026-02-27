# PixelWhisper

On-device voice-to-text for Android. Speak → transcribe → polish → inject into any text field.

**Stack:** Moonshine Voice (ASR) + Gemini Nano via ML Kit GenAI (polish) + AccessibilityService (injection). 100% on-device, zero cloud.

## Setup

### 1. Open in Android Studio

Open this project in Android Studio (Ladybug+). It will generate the Gradle wrapper automatically.

### 2. Download Moonshine model files

```bash
pip install moonshine-voice
python -m moonshine_voice.download --language en
```

Copy the resulting model files to `app/src/main/assets/base-en/`:
- `encoder_model.ort`
- `decoder_model_merged.ort`
- `tokenizer.bin`

### 3. Build & install

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Enable Accessibility Service (for text injection)

Sideloaded apps on Android 15 hit Enhanced Confirmation Mode. Bypass via ADB:

```bash
adb shell settings put secure enabled_accessibility_services com.pixelwhisper/.TextInjectionService
```

Or install via Play Store to avoid this.

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
- ~200MB for Moonshine model + ~100MB for Gemini Nano (auto-downloaded)
