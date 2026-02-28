# PixelWhisper — Project Index

type: android-app
path: /Users/bretzelstudio/projects/pixel-whisper
keywords: [voice, speech, transcription, android, moonshine, gemini-nano, accessibility, overlay, superwhisper]

## Key Documents
- `VISION.md` — product vision and scope [vision, goals, scope, mvp]
- `README.md` — setup instructions and architecture overview [setup, build, install]
- `setup.sh` — automated setup script [automation, build, deploy]

## Source Files
- `app/src/main/java/com/pixelwhisper/MainActivity.kt` — permission flows + launch [permissions, overlay, mic, accessibility]
- `app/src/main/java/com/pixelwhisper/FloatingOrbService.kt` — compose overlay + pipeline orchestration [service, overlay, compose, orb]
- `app/src/main/java/com/pixelwhisper/TranscriptionEngine.kt` — moonshine voice wrapper [asr, transcription, audio, moonshine]
- `app/src/main/java/com/pixelwhisper/PolishEngine.kt` — ml kit genai proofreading + prompt [polish, grammar, gemini-nano, mlkit]
- `app/src/main/java/com/pixelwhisper/TextInjectionService.kt` — accessibility service text injection [injection, accessibility, clipboard]

## Build Config
- `gradle/libs.versions.toml` — version catalog [dependencies, versions]
- `app/build.gradle.kts` — app module config [build, dependencies]
- `app/src/main/AndroidManifest.xml` — permissions + service declarations [manifest, permissions]
- `local.properties` — SDK path (points to Unity's Android SDK, not committed)

## Resources
- `app/src/main/res/xml/accessibility_service_config.xml` — accessibility service config
- `app/src/main/assets/medium-streaming-en/` — moonshine ONNX model files (gitignored, ~290MB)
