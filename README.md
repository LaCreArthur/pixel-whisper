# PixelWhisper

On-device voice-to-text for Android. Tap the floating orb, speak, text appears wherever you're typing.

Moonshine Voice transcribes, Gemini Nano polishes, AccessibilityService injects. 100% local, zero cloud.

## Setup

```bash
chmod +x setup.sh && ./setup.sh
```

Handles everything: Gradle wrapper, Moonshine model download (~290MB), build, install, and accessibility service.

### Manual setup

**1. Moonshine model files:**
```bash
python3 -m venv /tmp/moonshine-env
source /tmp/moonshine-env/bin/activate
pip install moonshine-voice
python3 -m moonshine_voice.download --language en
mkdir -p app/src/main/assets/medium-streaming-en
cp ~/Library/Caches/moonshine_voice/download.moonshine.ai/model/medium-streaming-en/quantized/* \
   app/src/main/assets/medium-streaming-en/
```

**2. Build & install:**
```bash
./gradlew installDebug
```

**3. Enable Accessibility Service** (sideloaded apps need ADB bypass on Android 15):
```bash
adb shell settings put secure enabled_accessibility_services com.pixelwhisper/.TextInjectionService
```

## Usage

1. Open PixelWhisper, grant overlay + mic permissions
2. Tap **Launch PixelWhisper** — floating blue orb appears
3. Tap orb — starts recording (turns red, pulses)
4. Speak
5. Tap again — transcribes and injects text into focused field (or clipboard)
6. Long-press orb to dismiss

**Polish toggle:** Uncheck "Polish with LLM" in the main screen to get raw Moonshine output without Gemini Nano post-processing.

## How it works

```
FloatingOrbService (Compose overlay, foreground service)
    ├── TranscriptionEngine (Moonshine Voice, batch transcription)
    ├── PolishEngine (ML Kit Proofreading + Prompt API)
    └── TextInjectionService (AccessibilityService + clipboard fallback)
```

Audio is buffered during recording and batch-transcribed after you stop for maximum accuracy (not streaming). The polish step uses Gemini Nano on-device to fix ASR errors, filler words, and punctuation.

Text is appended to existing content in the focused field — it won't overwrite what's already there.

## Requirements

- Android 15+ (API 35)
- Gemini Nano support (Pixel 9+, Samsung Galaxy S24+, etc.)
- ~290MB for Moonshine model + ~100MB for Gemini Nano (auto-downloaded by Google Play Services)

## License

MIT — see [LICENSE](LICENSE).
