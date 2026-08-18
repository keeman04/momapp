# TANU Android 1.0 Offline Lite

TANU = Transcription, Actions, Notes & Updates.

This Android build is designed around a strict local-first core:

- local account signup/login
- guest participants do not need TANU IDs
- compressed Opus/AAC meeting recording
- bundled multilingual Whisper model
- on-device speech-to-English transcription/translation
- English-only MOM generation
- decisions/action/pending-item extraction
- local meeting history/search
- approve MOM and automatically delete audio
- share approved MOM through Android's system share sheet
- no INTERNET permission in the TANU APK

## Storage strategy

The app bundles the quantized multilingual Whisper tiny Q5_1 model (~31 MiB). Meeting audio remains compressed and is deleted when the MOM is approved. TANU Lite does not bundle a large language model; its MOM engine is deterministic and local to minimize installation size.

## Build

GitHub Actions builds an installable arm64-v8a debug APK named:

`TANU-1.0-offline-arm64.apk`

The build workflow fetches whisper.cpp v1.9.1 and the verified quantized model during CI, then packages both into the APK.

## Device support

- Android 8.0 (API 26) or newer
- 64-bit ARM (`arm64-v8a`) Android phone
- microphone required
