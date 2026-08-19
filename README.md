# TANU Personal v2.2.1

TANU is a local-first Android AI conversation assistant for meetings, voice notes and imported recordings.

## Current production flow

- reliable foreground microphone recording with screen-off support
- compressed AAC/M4A master audio
- hardware noise suppression, echo cancellation and gain control where supported
- 20-second rolling PCM work chunks with 1-second overlap
- speech gating before transcription
- bundled multilingual Whisper for on-device transcription
- participant names and custom vocabulary for recognition context
- embedded Qwen3 0.6B via llama.cpp for private on-device meeting understanding
- deterministic local MOM fallback if the embedded LLM cannot run
- optional OpenAI final-MOM enhancement when a personal API key is connected
- English MOM with summary, decisions, actions, owners, dates and follow-ups
- Room meeting history, actions, people and search
- WhatsApp, email, text and PDF sharing
- audio auto-retention independent from permanent MOM/transcript retention
- floating TANU quick-access bubble
- action reminders

## Models shipped in the APK

The production APK contains exactly two AI model files:

1. `ggml-tiny-q5_1.bin` — multilingual Whisper speech recognition.
2. `Qwen3-0.6B-Q4_K_M.gguf` — local meeting understanding/MOM enhancement.

Both are active runtime dependencies. Legacy models, FastServer code, encoded source bundles, old backend prototypes, old previews and legacy build workflows have been removed from the v2.2.1 production branch.

## Build and tests

The only release workflow is `.github/workflows/build-v221-hardening.yml`.

Before creating the APK it verifies repository cleanup, exact model count/hashes, recorder and wake-lock requirements, unit tests, a simulated four-hour rolling-recording stress test, speech-gate tests, transcript-overlap tests, Android lint, Kotlin/native compilation and final APK contents.

Output artifact: `TANU-Personal-v2.2.1-Hardened.apk`.

## Device support

- Android 11 (API 30) or newer
- 64-bit ARM (`arm64-v8a`)
- microphone required for live recording
- internet is optional and only needed for OpenAI mode/sharing that requires network access
