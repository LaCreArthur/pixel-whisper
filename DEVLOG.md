# PixelWhisper Dev Log

## Feb 27 2026 — First Build & Run Session

### Build Fixes Applied
1. **Removed `kotlin-android` plugin** — AGP 9.0 has built-in Kotlin, applying old plugin throws error
2. **Kept `kotlin-compose` plugin** — still required for Compose compiler
3. **compileSdk 36** — required by deps (core-ktx 1.17.0, activity-compose 1.12.4)
4. **Compose BOM 2026.02.00** — the `.01` version was hallucinated
5. **Removed `AccessibilityNodeInfo.recycle()`** — removed at API 35
6. **3-arg `startForeground()`** — API 35 requires `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE`
7. **ML Kit API signatures** (verified via `javap` decompilation of actual JARs):
   - `Generation.getClient()` — no args (not `getClient(context)`)
   - `ProofreadingResult.results.firstOrNull()?.text` — not `.correctedText`
   - `GenerateContentResponse.candidates.firstOrNull()?.text` — not `.text` on response directly
8. **`useLegacyPackaging = true`** — Moonshine .so files have 4KB ELF alignment, Pixel 10 needs 16KB
9. **Adaptive icon** — AGP 9 enforces `android:icon` on `<application>`
10. **Replace `kotlinOptions`** with `kotlin { compilerOptions { } }` inside android block

### Runtime Issues Discovered
- **Prompt API BACKGROUND_USE_BLOCKED**: FloatingOrbService is a foreground service but AICore requires a visible Activity. Added `ForegroundProxyActivity` (transparent, excludeFromRecents) that launches briefly during polish step.
- **ForegroundProxyActivity + Theme.NoDisplay = crash**: `Theme.NoDisplay` requires `finish()` before `onResume()`. Switched to custom `Theme.Transparent`.
- **ForegroundProxyActivity steals focus**: Dismissed proxy BEFORE injecting, with 150ms delay.
- **Empty transcripts**: `onLineCompleted` only fires after silence gap. Added `onLineTextChanged` → `currentPartial` capture for tap-to-stop use case. Still seeing empty transcripts after reinstall — debug logging added but untested.

### Versions Confirmed Real
- AGP 9.0.1, Kotlin 2.3.10 (built into AGP 9), Gradle 9.3.1 — all real
- Compose BOM 2026.02.01 — hallucinated (latest stable: 2026.02.00)
- Moonshine 0.0.48 — latest on Maven Central. 0.0.49 tagged on GitHub but not published.

### Files Modified
- `app/build.gradle.kts` — plugins, compileSdk, kotlinOptions, useLegacyPackaging
- `build.gradle.kts` — removed kotlin-android plugin
- `gradle/libs.versions.toml` — composeBom fix
- `AndroidManifest.xml` — android:icon, ForegroundProxyActivity
- `TextInjectionService.kt` — removed .recycle() calls
- `FloatingOrbService.kt` — 3-arg startForeground, lifecycle symmetry, ForegroundProxy integration, combinedClickable for long-press dismiss
- `PolishEngine.kt` — fixed all ML Kit API calls
- `TranscriptionEngine.kt` — added partial transcript capture + debug logging
- `MainActivity.kt` — removed unused ServiceInfo import
- NEW: `ForegroundProxyActivity.kt` — transparent proxy for AICore foreground status
- NEW: `res/mipmap-anydpi-v26/ic_launcher.xml` — adaptive icon
- NEW: `res/values/themes.xml` — Theme.Transparent style
