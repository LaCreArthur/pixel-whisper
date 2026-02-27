# PixelWhisper — Vision

> Superwhisper for Android. On-device voice-to-text that's faster and smarter than typing, injected directly into any app.

## Why

Superwhisper proved the concept on Mac: a floating always-available mic that turns speech into polished text and drops it wherever your cursor is. No app switching, no copy-paste dance, no cloud latency. It's the best way to get thoughts into text.

But it doesn't exist on Android. And as of February 2026, the stack to build it is sitting right there: Moonshine Voice (beats Whisper Large-v3 on mobile), Gemini Nano via ML Kit (runs on the Tensor G5 NPU in <300ms), and AccessibilityService for injection into any text field. The entire pipeline runs on-device with zero cloud dependency.

Nobody has assembled these pieces yet. First mover on "Superwhisper for Android" is a real product position — not a side project, a category.

## For Whom

**V1: Arthur.** A Pixel 10 owner who thinks faster than he types and wants voice input that doesn't suck. The bar is: faster than thumb-typing, cleaner output than raw dictation, works in any app without switching context.

**Beyond V1: Android power users** who live in text — writers, developers, professionals who send 50+ messages a day. People who've seen Superwhisper demos and thought "I wish that existed on my phone." The Pixel/Android enthusiast crowd that cares about on-device AI and privacy.

## What Success Looks Like

**V1 success is binary:** Arthur uses it daily on his Pixel 10 instead of typing. If he reaches for the orb before reaching for the keyboard in Gmail, Slack, and Notes — it worked.

## Scope

### In (V1 — one-night build)

- Floating mic orb overlay (tap to start, tap to stop)
- Moonshine Voice transcription (start/stop, not streaming)
- Gemini Nano polish via ML Kit GenAI (grammar, punctuation, filler removal)
- AccessibilityService injection into focused text field
- Clipboard fallback + toast when no text field is focused
- Overlay + Accessibility permission flows
- Pixel 10 / API 35+ only

### Out (explicitly not V1)

- Streaming/real-time transcription
- Multiple modes (dictation, writing, code)
- Custom vocabulary or prompt customization
- Speaker diarization
- Settings UI or configuration
- Waveform visualization (nice-to-have, not blocking)
- Play Store distribution
- Any device other than Pixel 10

### Open Questions

- Moonshine Android dependency: Maven artifact or vendored from GitHub release?
- ML Kit GenAI Proofreading API vs Prompt API: use one or chain both?
- AccessibilityService reliability across apps (especially Chrome, Slack webviews)

## Current State

Empty project. No code written yet. This is hour zero.

## Constraints

- **Timeline:** One night. 5-6 hours with AI agent assistance.
- **Device:** Pixel 10 with Tensor G5, Android 15+ (API 35).
- **Runtime:** 100% on-device. No cloud APIs, no network dependency.
- **Tech stack locked:** Kotlin, Jetpack Compose, Moonshine Voice, ML Kit GenAI, AccessibilityService.
- **Distribution:** Sideloaded APK. No Play Store concerns for V1.

## North Star

Ship V1. Use it. If it replaces typing for real — that's product-market fit signal for the best voice-to-text app on Android. The 2026 on-device AI stack makes this possible at a quality level that didn't exist 6 months ago. First mover advantage is real and the window is open.
