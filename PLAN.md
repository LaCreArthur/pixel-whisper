# PixelWhisper rebuild plan (2026-10-05, Pixel 11 Pro)

## TL;DR (Arthur)

- You get: tap the orb in any text field (or hold it while you talk), speak French, English or both, and the text lands at the cursor. Fully offline.
- Speech: the large model you chose after step 0. It keeps English words in French sentences and removes most "uh". Wait after you stop: about 1.6 s if your last phrase is short, 3.5 s for 10 s without a pause.
- No cleanup model: the small offline one changed the meaning of French sentences. I test again when a good multilingual one exists.
- Gemini Nano stays out: tested on your Pixel 11 Pro, Google blocks it for apps that work behind other apps.
- Safer: orb hides in password fields; if the field changes during dictation, the text goes to the clipboard; auto-stop on silence, call, screen-off.
- Setup: one Mac script over USB. No setup screens, no notification, and the app has no internet access.
- Deleted: screen-flash workaround, 60 s limit, long-press dismiss (now hold-to-talk), always-append-at-end, both current engines, Pixel 10 support.
- Status: step 0 done, all phone tests passed (Slack insertion is finished in step 1). Step 1 builds the app; at the end you test it for 5 minutes.

---

## Technical body (executing agent)

### 0. Decisions

- 2026-10-05, Arthur: build PixelWhisper although Gboard Rambler (Pixel 11, cloud for full features, offline = basic cleanup only [V support.google.com/gboard/answer/17468539]) and Superwhisper Android (Play, bubble + cursor insertion, paid, local/cloud unstated [V superwhisper.com/docs/get-started/android]) exist. The product is fully on-device dictation.
- Personal app, one device: Arthur's Pixel 11 Pro, Android 17 (API 37). No other phones, no store, no onboarding UI.
- Reviewed once by a fresh attack + mobile-UX agent; its accepted changes are folded in below.
- 2026-10-05, Arthur after Leg 0: speech engine = Whisper large-v3-turbo int8 (accuracy on his French/English code-switching over speed; Parakeet v3 rejected); go for Leg 1. Cleanup cut by gate G0c.

### 1. Evidence

Tags: [V] primary source read 2026-10-05 (docs, AOSP main, HF/GitHub/Maven APIs), [C] claim, [I] inference.

Platform
- ML Kit GenAI (Prompt 1.0.0-beta4, Proofreading 1.0.0-beta1, Speech Recognition 1.0.0-alpha1): inference only for "the top foreground application"; foreground services get `BACKGROUND_USE_BLOCKED` (error 30). Unchanged for Pixel 11 / nano-v4; no exemption for keyboards, accessibility or assistant apps [V developers.google.com/ml-kit/genai, updated 09-28]. Prompt API takes no audio [V].
- `AccessibilityServiceConnection` binds with `BIND_FOREGROUND_SERVICE_WHILE_AWAKE | BIND_INCLUDE_CAPABILITIES` [V AOSP]. `AudioPolicyService` lets an accessibility uid capture with `VOICE_RECOGNITION`, even during calls [V AOSP], so the source choice is load-bearing and the app must stop itself on calls. AppOps while-in-use path [I]; probed in Leg 0.
- `AccessibilityService.InputMethod` (API 33): `flagInputMethodEditor` is the only gate (no `canRetrieveWindowContent` needed) [V AMS source]. `onStartInput` can arrive with a null connection when no input was running [V AOSP `doStartInput`]. `commitText` returns void [V]; a commit triggers `onUpdateSelection` [V docs]. `getSurroundingText` may take seconds [V docs].
- `TYPE_ACCESSIBILITY_OVERLAY` needs no `SYSTEM_ALERT_WINDOW` and sits above keyguard, shade and IME layers [V WindowManagerPolicy]. Must be `FLAG_NOT_FOCUSABLE` or a tap ends the target's input [I].
- A crashed accessibility service is reconnected when the system restarts it; force-stop turns it off [V AOSP]. Restricted settings only guard APKs installed from a local or downloaded file [V EnhancedConfirmationService]; adb installs [I] exempt, and setup enables the service by adb anyway.
- sherpa-onnx Silero VAD defaults: 0.25 s min silence, 5 s max speech [V v1.13.8], too aggressive for dictation.

Pixel 11 Pro
- Android 17 / API 37, 12 GB (256 GB model) or 16 GB RAM [V store page]. CPU 1x C1-Ultra 4.11 GHz + 4x C1-Pro 3.38 + 2x C1-Pro 2.65 [C]; SME2 listed by Arm [V]. GPU PowerVR CXTP-48-1536 [C]; llama.cpp Vulkan crashes or returns wrong results on it [C #28214]; LiteRT-LM GPU decode is slower than CPU on PowerVR [C].
- Gemma 4 E2B CPU decode: 24.7 t/s on Pixel 11 Pro XL vs 10.4 on Pixel 10 [C Beebom]. No published Qwen3-0.6B numbers on Pixel 11.
- NPU: LiteRT + Google Tensor SDK Beta (G5/G6), AOT-compiled models only [V]. Google ships `litert-community/parakeet-tdt-0.6b-v3` with `parakeet_tdt_0.6b_v3_5s_f32_Google_Tensor_G6.tflite` (~1.3 GB, 08-26) and an Android speech sample (features, TDT decoding, overlapping 5 s windows) verified on Pixel 10 NPU [V]. NPU runtime artifact 404 on Maven today [V]; not proven on retail G6 [C]. LLM NPU builds exist only for stock Gemma3-1B / Gemma4-E2B; Qwen3 and custom fine-tunes "under development" [V LiteRT #8501].
- No new buttons; Android 17 adds no voice, IME or text-insertion APIs [V/C].

Models and runtimes
- Parakeet TDT 0.6B v3: 25 European languages, automatic language detection, punctuation and casing; Open ASR avg WER 6.34% (Moonshine medium-streaming 6.65%); French FLEURS 5.15%; CC-BY-4.0 [V card]. sherpa-onnx v1.13.8 (2026-09-10): Android AAR on GitHub releases and JitPack, termux-static arm64 CLI binaries [V]. Files: `huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` (`encoder.int8.onnx`, `decoder.int8.onnx`, `joiner.int8.onnx`, `tokens.txt`, ~670 MB; en/fr/de/es test wavs) [V]. Prebuilt VAD + Parakeet v3 demo APK: `huggingface.co/csukuangfj2/sherpa-onnx-apk/resolve/main/vad-asr-simulated-streaming/1.13.8/sherpa-onnx-1.13.8-arm64-v8a-simulated_streaming_asr-multi-parakeet_tdt_0.6b_v3.apk` [V per research].
- S1-mini (`superwhisper/s1-mini`, Qwen3-0.6B fine-tune, 2026-08): removes fillers and self-corrections, punctuates, formats numbers; English; does not fix misheard words; expects lowercase unpunctuated input; output about 1.3x input tokens; prompt needs the empty think block; results measured on `superwhisper/s1-mini-GGUF` `s1-mini-q4_k_m.gguf` (484 MB); Apache-2.0 with a naming clause [V card]. Community LiteRT-LM conversions omit the think block and their README shows a meaning change ("ten thirty, actually no, make it eleven" -> "11:30") [V], so they are not used.
- Precedent: a FUTO Voice Input fork runs S1-mini through pinned llama.cpp at 0.85 s median stop-to-text on an S25 Ultra [C github.com/Today20092/voice-input]. llama.cpp publishes prebuilt Android arm64 binaries [V].

Local machine
- Build is broken today: `local.properties` points to the deleted Unity 6000.3.10f1 SDK; `/Library/Java/JavaVirtualMachines/openjdk-17.jdk` is dangling [V]. Present: Homebrew `openjdk@21`, `android-platform-tools` cask, Homebrew cmake [V].
- Latest stable: AGP 9.4.1, Gradle 9.8.0, kotlinx-coroutines 1.11.0 [V].

### 2. Target architecture

```
scripts/setup-phone.sh (Mac, idempotent)
  fetch models to ~/.cache/pixelwhisper (pinned HF commit URLs, sha256 check):
    csukuangfj/sherpa-onnx-whisper-turbo turbo-encoder.int8.onnx, turbo-decoder.int8.onnx, turbo-tokens.txt; silero_vad.onnx
  JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew installDebug
  adb push models -> /sdcard/Android/data/com.pixelwhisper/files/models/   (survives install -r, Leg 0)
  adb shell pm grant com.pixelwhisper android.permission.RECORD_AUDIO
  append com.pixelwhisper/.DictationService to secure enabled_accessibility_services (keep existing entries), accessibility_enabled=1

MainActivity (plain Views; also the service's settingsActivity)
  status: mic granted, service on, models present with expected sizes
  test text field, credits (Whisper MIT OpenAI; sherpa-onnx Apache-2.0; Silero VAD MIT)

DictationService : AccessibilityService   <- the only runtime component
  Orb        custom View in a TYPE_ACCESSIBILITY_OVERLAY window, FLAG_NOT_FOCUSABLE, 48 dp.
             Visible while: connection != null && inputType != TYPE_NULL && not a password variation; also while recording/working.
             Placement: right edge, 35% height by default; snaps to the nearest edge; stored as (edge, height fraction) so rotation is safe.
  Gestures   ACTION_DOWN from idle starts recording at once (no lost first word) + haptic tick.
             Moved past touch slop before 300 ms -> drag: discard audio, move orb (drag only from idle).
             Released before 300 ms -> toggle mode, next tap stops. Held >= 300 ms -> push-to-talk, release stops.
             While recording in toggle mode, dragging the orb > 1/4 screen away cancels.
  Recorder   AudioRecord(VOICE_RECOGNITION, 16 kHz mono PCM16) -> Silero VAD (512-sample windows; min silence 0.8 s = best Whisper accuracy in Leg 0; max speech 20 s, under Whisper's 30 s window).
             ~100 ms after start: getActiveRecordingConfiguration().isClientSilenced -> "mic busy".
             Stops: user; 10 s of no speech (process normally); screen off, call (AudioManager mode listener), field change -> process, result to clipboard.
  Transcriber sherpa OfflineRecognizer, Whisper large-v3-turbo int8 (language "" = auto per segment, task transcribe, 6 threads), one worker;
             each VAD segment transcribed when it closes; on stop vad.flush() and only the tail is left; segments joined with one space.
  Inserter   field changed = any onStartInput/onFinishInput after recording start (fieldId is -1 in Compose, web fields restart with restarting=true).
             getSurroundingText(1 before) fetched off-main in parallel with the tail work; space-if-needed treats whitespace and zero-width chars as empty.
             Same field at the end -> commitText(space-if-needed + text, 1, null); no onUpdateSelection within 500 ms -> clipboard + toast.
             Different or no field -> clipboard + "Copied, paste it" toast. No ACTION_SET_TEXT, no ACTION_PASTE.
  Models     loaded when the orb first appears (never in onServiceConnected), then kept; released on onTrimMemory. Load time measured in Leg 1.
             File-size check before any native load (a bad file is a native crash try/catch cannot stop).
  Feedback   listening state within 100 ms even while models load; pulse follows input level; haptic tick at start and stop;
             spinner while working; success tick; distinct states for "didn't catch that" (no text, clipboard untouched), "mic busy", "models missing".
```

Accessibility config: `flagInputMethodEditor`, `canRetrieveWindowContent="false"` (the service cannot read the screen), no event types, `settingsActivity` = MainActivity.
Manifest: `RECORD_AUDIO` only. No `INTERNET`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS`.
Dependencies: kotlinx-coroutines-android, sherpa-onnx 1.13.8. No Compose, no androidx unless a concrete call needs it, no NDK build.
Files (~700 lines): `MainActivity.kt`, `DictationService.kt` (lifecycle, state machine, stops, insert), `OrbView.kt`, `Recorder.kt`, `Transcriber.kt`, `Models.kt`, `scripts/setup-phone.sh`.
Dev rule: never `am force-stop com.pixelwhisper` (it turns the service off); reinstall with `install -r`.

### 3. Stack

- AGP 9.4.1, Gradle wrapper 9.8.0, AGP built-in Kotlin (no compose plugin, so no separate Kotlin pin).
- compileSdk / targetSdk / minSdk 37 (confirm `ro.build.version.sdk` and the platform package name with `sdkmanager --list`).
- Standalone SDK at `~/Library/Android/sdk` (done in Leg 0: platform-tools, android-36, android-37.0, build-tools 36.0.0). `local.properties` points to it. JDK: `JAVA_HOME=/opt/homebrew/opt/openjdk@21`.
- sherpa-onnx: JitPack `com.github.k2-fsa:sherpa-onnx:v1.13.8` if it resolves the Android AAR, else the release AAR fetched by the setup script into gitignored `app/libs/`. Pick one at Leg 1 start.
- Check 16 KB page alignment of every `.so` (`zipalign -c -P 16 -v 4`); drop `useLegacyPackaging` if they pass.

### 4. Deleted (itemized for veto)

1. Moonshine dependency, bundled `assets/medium-streaming-en/` (~290 MB) and the copy-to-files step that doubled storage.
2. ML Kit Proofreading + Prompt and the two-stage polish.
3. `ForegroundProxyActivity` and its transparent theme.
4. `FloatingOrbService` (foreground service, notification, overlay-permission flow, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`). Only after the Leg 0 mic probe passes.
5. `ACTION_SET_TEXT` insertion with hint detection and append-at-end.
6. Long-press to dismiss: the orb hides outside text fields, the Accessibility switch stops everything; long-press becomes hold-to-talk.
7. The 60 s cap, replaced by the 10 s silence stop and the interruption stops.
8. Compose (BOM, compose plugin, material3, icons, lifecycle-owner plumbing) and appcompat: the orb is one circle, settings is one screen.
9. Gradle 4 GB heap, `noCompress` for model files, `useLegacyPackaging` if alignment passes.
10. `setup.sh`, replaced by `scripts/setup-phone.sh`.
11. MainActivity permission flows and launch button.
12. Support for anything below Android 17, Pixel 10 included.
13. Stale claims in `VISION.md`/`README.md` (Moonshine + Gemini Nano stack, "nobody has assembled these pieces", first-mover thesis, "Current State: empty project") rewritten with the decision in section 0.
14. The planned S1-mini/llama.cpp cleanup leg (never built; G0c failed on French).

### 5. Legs

**Leg 0. Prove every unknown on the phone (no rebuild code). Arthur: connect the Pixel 11 Pro with USB debugging, then 5 min of dictation.**
1. Toolchain: standalone SDK + JDK 21, `local.properties` fixed; the current app builds and installs.
2. Probe on the current app (~60 lines, not committed): an exported receiver in the accessibility service, triggered by `adb shell am broadcast`, with Gmail in front:
   - records 3 s with `VOICE_RECOGNITION`, logs peak level and `isClientSilenced`;
   - logs `onStartInput`/`onFinishInput`/`onUpdateSelection` with connection null-ness and EditorInfo (package, inputType, fieldId) in Gmail, Slack, Chrome (form + address bar), Messages, Keep;
   - `commitText("probe")` at the cursor and checks `onUpdateSelection`;
   - one ML Kit Prompt call (expect error 30; any success reopens Gemini Nano).
   - checks that `adb push` into `Android/data/com.pixelwhisper/files` is readable by the app.
3. ASR speed: push sherpa termux-static `sherpa-onnx-offline` + Parakeet v3 int8 to `/data/local/tmp`; decode time for 5 s and 20 s audio at 2/4/6 threads; cold load time and RSS.
4. Cleanup speed: llama.cpp prebuilt arm64 `llama-bench` + `s1-mini-q4_k_m.gguf`: prefill/decode t/s at 4/6/7 threads, load time; `grep -o sme2 /proc/cpuinfo`.
5. Arthur ear test: agent installs the Parakeet v3 demo APK; Arthur dictates 5 real messages the way he talks (fillers, corrections, any other language he uses). Agent captures the text with `uiautomator dump`, then uninstalls the demo.
6. Cleanup value, on the Mac (`brew install llama.cpp`, card's settings): the 5 transcripts + 5 hard cases (mid-number self-correction, list, email/URL, a name, a French sentence) -> before/after table for Arthur.
7. Write measured numbers and the gates below into this file.

Gates
- G0a architecture: real audio with Gmail in front, non-null connection with correct EditorInfo in the five apps, `commitText` lands at the cursor and fires `onUpdateSelection`. Fail -> stop, report the blocker, re-plan. No pre-built fallback.
- G0b speech: Arthur accepts the accuracy; 5 s tail decodes in <= 0.3 s on CPU. Speed fail -> benchmark Google's G6 NPU Parakeet with the LiteRT CLI. Accuracy fail -> `parakeet-unified-en-0.6b` or Moonshine 0.1.5.
- G0c cleanup: Arthur approves the table (no meaning change, visible gain in at least half); projected stop-to-text from measured t/s <= 1 s for a 10 s dictation and <= 3 s for 30 s. Value fail -> Leg 2 is cut. Speed fail -> check llama.cpp n-gram/prompt-lookup speculative decoding (output mostly copies input); still failing -> numbers to Arthur.

**Leg 0 results (2026-10-05; device 67171FDKX00447, Android 17 / SDK 37, Tensor G6, 16 GB, CPU has i8mm/bf16/sve2, no SME/SME2).** Artifacts in `~/.cache/pixelwhisper/` (scripts `probe.sh`, `field.sh`, `vad-asr.sh`, `whisper-bench.sh`, `s1test.sh`; audio + transcripts in `dictation/`). Probe (uncommitted): `Probe.kt` + two hooks in `TextInjectionService` + `flagInputMethodEditor`/`canRetrieveWindowContent=false` in the a11y config; installed, service enabled via adb.
- Toolchain: `brew install --cask android-commandlinetools`; sdkmanager (JAVA_HOME openjdk@21) into `~/Library/Android/sdk`: platform-tools, platforms;android-36 + android-37.0, build-tools;36.0.0. sdkmanager is deprecated in favor of the Android CLI. Termux-static sherpa binaries need NDK `libc++_shared.so` on `LD_LIBRARY_PATH`.
- G0a PASS.
  - Mic: a11y-only process (no FGS, launcher in front) is procState 5 / oom 100 / importance 125 while awake; `AudioRecord(VOICE_RECOGNITION)` 16 kHz: not silenced, real room noise (rms 43). A 141.8 s dictation was recorded this way (`dictation/session1.wav`).
  - Input: `onStartInput` with connection + `commitText` -> `onUpdateSelection` in Messages (Compose, fieldId -1), Gmail compose To (fieldId 1), Chrome omnibox, Chrome web page (google.com box), Keep search, WhatsApp search. Slack search: connection verified (inputType 0x210001), commit not run (phone locked). `getSurroundingText` 2-8 ms. Windows without an editor: `onStartInput` with null connection and inputType 0.
  - Design changes from this: (1) field identity cannot use fieldId (-1 for every Compose field; web fields switch with `restarting=true` on the same view), so any `onStartInput`/`onFinishInput` after recording start = field changed -> clipboard. (2) space-if-needed treats whitespace and zero-width chars as empty (WhatsApp's empty search field holds U+200B, selStart 1).
  - ML Kit Prompt from the a11y process: `GenAiException [ErrorCode 30] Background usage is blocked` at importance 125. Gemini Nano stays out.
  - `adb push` into `/sdcard/Android/data/com.pixelwhisper/files/models/` is readable by the app and survives `install -r`; after `install -r` the system rebinds the a11y service in 0.6 s with the setting kept. `uiautomator dump` does not unbind the service.
  - The Pixel reports the Mac's USB-C as AC power: `stay_on_while_plugged_in` needs bit 1, not 2 (restored to 0).
- Speech speed (CPU, sherpa-onnx 1.13.8): Parakeet v3 int8 t4: 5 s -> 0.26 s, 20.7 s -> 0.90 s, load ~2.4 s (warm page cache), whole 141.8 s session with VAD -> 3.3 s. Whisper large-v3-turbo int8 (csukuangfj/sherpa-onnx-whisper-turbo, encoder 675 MB + decoder 361 MB), cool phone, t6: 4.1 s -> 1.64 s, 9.85 s -> 3.51 s (t4: 2.25 s / 4.44 s). whisper.cpp v1.9.4 built with NDK r27 (static, OpenMP off, armv8.6-a+dotprod+i8mm, with and without +fp16) is pathologically slow on G6 (no result within 100 s for 4 s of audio, even at 1 thread); cause unknown, parked.
- Accuracy on Arthur's real speech: he speaks French and English and switches between and inside sentences (franglais, English tech words in French); he talked about the test instead of dictating 5 messages.
  - Parakeet, VAD min silence 0.8 s: one language per segment, so mixed segments turn French into English gibberish ("I've been to using a mote on my phrase in France").
  - Parakeet, 0.3 s: language right on most phrases; English words inside French fail ("en tech" -> "I'll take"); every segment ends with a period ("uh backgrounds." "Services."); keeps uh/euh.
  - Platform on-device `SpeechRecognizer` (works from the a11y process; fr-FR pack via `triggerModelDownload` in 8 s; `EXTRA_AUDIO_SOURCE` pipe; `EXTRA_ENABLE_LANGUAGE_SWITCH` balanced fr/en): fed faster than real time it drops most segments; at real-time pace it still drops the first segments and turns a 10 s English sentence into "English."; "motèque", "En teck". Rejected.
  - Whisper turbo on 0.8 s segments: best. Keeps English inside French ("\"background services\" dans ma phrase en français", "en tech"), drops most fillers, writes formal negation ("je n'étais pas"). On 0.3 s segments it is worse and turned a 0.5 s noise into "Thank you." Every engine wrote "te mettre donne" (Arthur said "done").
- Cleanup, S1-mini on Arthur-style input (Mac, llama.cpp 0.5.0, card prompt, greedy): English fillers + self-correction good ("So send the new build to the team at 11."); French keeps a trailing "euh" and changes meaning ("on se voit demain à dix heures non plutôt onze heures" -> "On voit demain à 11 heures."); cannot repair gibberish from a wrong language; no "donne" fix. G0c FAIL on value (meaning change in his main language): Leg 2 is cut from this plan. Speed for the record (G6, llama-bench b11429, t6): pp160 284 t/s, tg 94 t/s.
- G0b: Parakeet passes speed, Whisper turbo fails the 0.3 s tail target (1.64 s for a 4 s tail). The accuracy fallbacks above (`parakeet-unified-en-0.6b`, Moonshine) are English-only and therefore wrong for Arthur. Engine choice routed to Arthur (accurate Whisper turbo vs fast Parakeet).

**Leg 1. Raw dictation, end to end.**
- Fresh structure per section 2 without the Cleaner; delete section 4 items; setup script; one commit.
- Done when: warm stop-to-text <= 2 s when the last phrase is <= 4 s (Leg 0: 1.64 s), first dictation after model load measured and reported (logcat timings); text at the cursor in Gmail, Slack, Chrome form, Messages, Keep; checklist passes: password field hides the orb, app switch mid-dictation -> clipboard, incoming call stops recording -> clipboard, notification shade open, rotation keeps the orb on its edge, keyboard closed, another app holding the mic -> "mic busy", screen off -> stop + clipboard, 10 s silence -> stop.
- Arthur: 5 min of real use.
- Close: README, VISION, project memory updated; one commit.

**Leg 2. Cut** (G0c failed; cleanup moved to section 7).

### 6. Risks

- Accessibility mic capture or input connection fails on Android 17 -> G0a stops the plan before anything is deleted.
- Input connection null in a specific app -> clipboard path; list the apps in this file.
- Whisper hallucinates on short noise (Leg 0: a 0.5 s sound became "Thank you." on short segments) -> watch in real use; raise VAD min speech duration if it shows up.
- Whisper writes formal French negation ("je n'étais pas") -> accepted for now; initial-prompt styling is in section 7.
- ~1 GB of model resident in the accessibility process -> released on onTrimMemory; 16 GB phone.
- Native crash in sherpa -> service restarts; size checks and no loading in `onServiceConnected` limit it.
- Licences: Whisper MIT, sherpa-onnx Apache-2.0, Silero VAD MIT, credited in README and settings; weights are not redistributed (fetched by the script).

### 7. Later (not in this plan)

Live text preview while talking (VAD segments already give it); personal vocabulary and casual-French style through Whisper's initial prompt; command mode (rewrite selected text by voice); app-aware formatting from `EditorInfo`; back double-tap (Quick Tap) trigger, needs a probe; faster Whisper (whisper.cpp with dynamic audio_ctx once its G6 slowness is understood, or the encoder on the Tensor NPU via LiteRT); a multilingual cleanup model that passes G0c on French (self-corrections, "donne" -> "done"); Gemini Nano if Google lifts the foreground rule.
