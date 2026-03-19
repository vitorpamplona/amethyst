# Brainstorm: Desktop DM Encrypted Media (Phase 6)

**Date:** 2026-03-18
**Status:** Ready for planning
**Branch:** `feat/desktop-media`

## What We're Building

Full send + receive encrypted media support in desktop DM chat (NIP-17). Users can attach files in DMs, which get encrypted (AES-GCM) before upload to Blossom, sent as `ChatMessageEncryptedFileHeaderEvent` (kind 15) wrapped in GiftWrap. Received encrypted media is downloaded, decrypted, and displayed inline with a lock icon overlay.

## Why This Approach

The protocol layer is complete in quartz (NIP-17, NIP-44, AESGCM, ChatMessageEncryptedFileHeaderEvent). Android has the full flow implemented. Desktop already has `EncryptedMediaService.downloadAndDecrypt()` and `DesktopUploadOrchestrator` (unencrypted only). The work is essentially wiring up the existing pieces with a desktop-native UX.

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Scope | Full send + receive | Both sides needed for complete DM file sharing |
| Attach UX | Inline paperclip button | Matches desktop chat conventions; thumbnails above text input |
| Drag & drop | Yes, in addition to button | Desktop-native interaction; ComposeNoteDialog already has this pattern |
| Encryption indicator | Lock icon overlay | Small lock on corner of encrypted media in chat bubbles |
| Upload encryption | AES-GCM via AESGCM class | Matches Android's ChatFileUploader.justUploadNIP17() pattern |
| Event type | Kind 15 (ChatMessageEncryptedFileHeaderEvent) | NIP-17 standard for encrypted file metadata |

## Architecture Overview

### Send Flow (Desktop)
```
User attaches file → thumbnail preview above input
  → Click send
  → Generate AES-GCM cipher (key + nonce)
  → DesktopUploadOrchestrator.uploadEncrypted(cipher, file)
    → Encrypt file bytes with cipher
    → Upload encrypted blob to Blossom server
  → Build ChatMessageEncryptedFileHeaderEvent (kind 15)
    → URL, encryption algo/key/nonce, file metadata
  → Wrap in GiftWrap (NIP-59) for each recipient
  → Send to relays
```

### Receive Flow (Desktop)
```
Receive GiftWrap → unwrap → ChatMessageEncryptedFileHeaderEvent
  → Extract URL, encryption key, nonce from tags
  → EncryptedMediaService.downloadAndDecrypt(url, key, nonce)
  → Display decrypted media inline in chat bubble
  → Lock icon overlay on media thumbnail
```

## Existing Code to Reuse

| Component | Location | Action |
|-----------|----------|--------|
| AESGCM cipher | `quartz/utils/ciphers/AESGCM.kt` | Reuse as-is |
| ChatMessageEncryptedFileHeaderEvent | `quartz/nip17Dm/files/` | Reuse as-is |
| NIP17Factory (GiftWrap) | `quartz/nip17Dm/NIP17Factory.kt` | Reuse as-is |
| EncryptedMediaService | `desktopApp/service/media/EncryptedMediaService.kt` | Extend for UI integration |
| DesktopUploadOrchestrator | `desktopApp/service/upload/DesktopUploadOrchestrator.kt` | Add `uploadEncrypted()` |
| ChatPane | `desktopApp/ui/chats/ChatPane.kt` | Add attach button, thumbnails, drag-drop |
| ChatMessageCompose | `commons/ui/chat/ChatMessageCompose.kt` | Add encrypted media display |
| Android ChatFileUploader | `amethyst/chats/privateDM/send/upload/` | Reference pattern |

## New Code Needed

| Component | Location | Purpose |
|-----------|----------|---------|
| `uploadEncrypted()` | DesktopUploadOrchestrator | Encrypt file with AESGCM before Blossom upload |
| DM file attach UI | ChatPane.kt | Paperclip button, thumbnail row, drag-drop zone |
| DM file send logic | ChatPane.kt or new helper | Build kind 15 event from upload result + cipher |
| Encrypted media renderer | ChatMessageCompose or new composable | Download, decrypt, display with lock overlay |
| `sendNip17EncryptedFile()` | DesktopIAccount.kt | Bridge to relay manager for sending |

## Open Questions

None — all key decisions resolved through brainstorm dialogue.

## Test Cases (from testing plan)

| # | Test | Expected |
|---|------|----------|
| 6.1 | DM file attach | Attach button visible, encryption indicator shown |
| 6.2 | Send encrypted | File uploads encrypted to Blossom, kind 15 event sent |
| 6.3 | Receive encrypted | Encrypted file downloads, decrypts, displays in bubble |
| 6.4 | Wrong key | Decryption fails gracefully (no crash, error state) |
