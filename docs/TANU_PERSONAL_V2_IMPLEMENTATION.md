# TANU Personal v2 — Production Implementation Plan

This branch follows the TANU Personal development path supplied on 2026-08-19.

## Product position
TANU — AI Conversation Assistant

Central promise: **Every conversation becomes an action.**

Core flow:
Meeting / Call / Voice Note / Imported Recording → TANU AI → Transcript → MOM → Action Items → Reminders → WhatsApp / Email follow-up.

## Production architecture
- Kotlin
- Jetpack Compose
- MVVM / Clean Architecture
- Room
- WorkManager
- Foreground Services
- Android Keystore
- Hilt
- Android 10+
- Latest Google Play required target API at release time

## Phase 1 MVP boundary
Build first:
- Login / local profile foundation
- Home
- Local database
- Microphone recording using foreground service
- Import MP3/M4A/WAV/AAC/MP4 audio
- Meeting history
- Participants and guests without TANU ID
- Multilingual transcription architecture via `TranscriptionProvider`
- Speaker diarization/renaming architecture
- English MOM
- Structured JSON MOM output
- Action items
- Action reminders
- Search
- WhatsApp/email/copy/PDF sharing
- Recording consent screen
- Offline-first recording and processing queue
- Audio retention choices
- Security foundations

## Do not build into MVP
- Hidden call recording
- Accessibility-based WhatsApp call recording
- Root/device-specific call hacks
- Automatic personal WhatsApp sending
- Enterprise admin
- CRM integrations
- Full on-device LLM

## Call assistance roadmap
Phase 2: Floating Assistant + OS-permitted cellular call assist.
Phase 3: TANU Call using VoIP/WebRTC/SIP with controlled audio and consent announcement.
Phase 4: TANU virtual business number.
Phase 5: WhatsApp Business API follow-up.
Phase 6: Free / Pro / Pro+ subscriptions.

## Sprint order
1. Android project → Login → Home → Room
2. Recording → Foreground service → Recording UI → secure audio storage
3. Upload/provider abstraction → Transcription → Transcript UI
4. Structured JSON MOM → MOM UI
5. Participants → Actions → reminders
6. WhatsApp/email/PDF/copy share
7. Floating TANU bubble
8. Cellular call state assist
9. WhatsApp notification assist
10. Search/history/action dashboard
11. Billing/free-pro limits
12. Security/privacy/analytics/crash handling/Play beta

## Migration from TANU 1.4 prototype
Keep and port concepts, not the old Java UI architecture:
- TANU branding and palette
- Foreground recording reliability
- Compressed local audio
- Background/screen-off recording strategy
- Audio retention policy
- Guest participant behavior
- Local Whisper-compatible provider work

Rebuild in Kotlin/Compose:
- UI/navigation
- data layer
- domain models
- structured MOM JSON
- action dashboard
- reminders
- search
- provider abstraction
- overlay/call-assist modules

## Core domain models
User, Participant, Meeting, MeetingParticipant, TranscriptSegment, MOM, ActionItem.

Meeting status:
RECORDING / UPLOADING / TRANSCRIBING / GENERATING_MOM / READY / FAILED.

## Offline-first rule
Recording always succeeds locally independent of connectivity.
Any cloud/provider work is queued with WorkManager when connectivity returns.
Local transcription providers may execute immediately without network.

## Storage rule
Use compressed recordings. MOM and transcript remain until user deletion. Audio retention is user-configurable; default may be delete-after-successful-MOM.

## Security rule
Use Android Keystore, encrypted local storage where appropriate, HTTPS for network modules, no API keys embedded in the APK, secure tokens, signed requests, and explicit user/account/recording deletion controls.
