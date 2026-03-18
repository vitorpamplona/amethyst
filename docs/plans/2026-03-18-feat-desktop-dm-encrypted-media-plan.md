---
title: "feat: Desktop DM Encrypted Media (NIP-17)"
type: feat
status: active
date: 2026-03-18
origin: docs/brainstorms/2026-03-18-desktop-dm-encrypted-media-brainstorm.md
---

# feat: Desktop DM Encrypted Media (NIP-17)

## Overview

Implement full send + receive encrypted media support in desktop DM chat. Users can attach files via paperclip button or drag-and-drop, which get AES-GCM encrypted before upload to Blossom. Files are sent as `ChatMessageEncryptedFileHeaderEvent` (kind 15) wrapped in GiftWrap (NIP-59). Received encrypted media is downloaded, decrypted, and displayed inline with a lock icon overlay.

This is Phase 6 of the desktop media parity plan.

## Problem Statement / Motivation

Desktop DM chat (`ChatPane.kt`) supports text messages but has no file attachment capability. Android has a complete implementation (`ChatFileUploader` + `ChatFileSender`). Most protocol and service pieces exist on desktop already — this work wires them together with a desktop-native UX.

## Proposed Solution

Port Android's NIP-17 encrypted file flow to desktop, reusing existing protocol layer (quartz) and extending desktop services. Three workstreams: upload pipeline, send logic, and receive/display.

(see brainstorm: `docs/brainstorms/2026-03-18-desktop-dm-encrypted-media-brainstorm.md`)

## Implementation Phases

### Phase A: Encrypted Upload Pipeline

**Goal:** `DesktopUploadOrchestrator` gains `uploadEncrypted()` method.

**Files:**
- `desktopApp/.../service/upload/DesktopUploadOrchestrator.kt` — add `uploadEncrypted()`
- `desktopApp/.../service/upload/DesktopBlossomClient.kt` — may need raw bytes upload variant
- `desktopApp/.../service/upload/DesktopBlossomAuth.kt` — verify auth uses encrypted blob hash

**Implementation:**

```kotlin
// DesktopUploadOrchestrator.kt — new method
suspend fun uploadEncrypted(
    file: File,
    cipher: AESGCM,
    server: String,
    signer: NostrSigner,
    tracker: DesktopUploadTracker? = null
): UploadResult {
    // 1. Read file bytes
    val plaintext = file.readBytes()

    // 2. Compute pre-encryption metadata (dimensions, blurhash, mime, originalHash)
    val metadata = DesktopMediaMetadata.compute(file)

    // 3. Encrypt
    val encrypted = cipher.encrypt(plaintext)

    // 4. Compute SHA256 of ENCRYPTED blob (critical: not plaintext)
    val encryptedHash = sha256Hex(encrypted)

    // 5. Create Blossom auth with encrypted hash
    val auth = DesktopBlossomAuth.createUploadAuth(
        hash = encryptedHash,
        signer = signer
    )

    // 6. Upload encrypted blob
    val result = DesktopBlossomClient.upload(
        bytes = encrypted,
        hash = encryptedHash,
        auth = auth,
        server = server,
        tracker = tracker
    )

    // 7. Return result with both hashes
    return UploadResult(result, metadata, originalHash = metadata.sha256)
}
```

**Reference:** Android's `ChatFileUploader.justUploadNIP17()` at `amethyst/.../upload/ChatFileUploader.kt:39-80`

**Critical gotcha:** Hash the encrypted blob, not plaintext. The `X-SHA-256` header and kind 24242 `x` tag must match the encrypted bytes.

---

### Phase B: DM File Attach UI in ChatPane

**Goal:** Paperclip button, thumbnail row, drag-and-drop in `ChatPane.kt`.

**Files:**
- `desktopApp/.../ui/chats/ChatPane.kt` — modify `MessageInput()` composable

**Implementation:**

Add to `MessageInput()` (currently at line ~527):

1. **State:** `attachedFiles: MutableList<File>` tracked in `ChatNewMessageState` or local state
2. **Paperclip button:** Icon button left of text field, opens `JFileChooser` (same pattern as `ComposeNoteDialog.kt`)
3. **Thumbnail row:** Above the text input, shows attached file thumbnails with X remove button
4. **Drag-and-drop:** `Modifier.onExternalDrag` on the chat pane area (same pattern as `ComposeNoteDialog.kt`)
5. **Encryption indicator:** Small lock icon badge on attachment thumbnails when in NIP-17 mode

**UI Layout (from brainstorm):**
```
┌─────────────────────────────────┐
│  Chat messages...               │
│                                 │
│  ┌─────┐ ┌─────┐               │
│  │thumb│ │thumb│  (attachments) │
│  └──x──┘ └──x──┘               │
│ 📎 [Type a message...    ] [→] │
└─────────────────────────────────┘
```

**Reference:** `ComposeNoteDialog.kt` drag-drop pattern (line ~182-194 for `MediaAttachmentRow`, line ~254-303 for upload pipeline)

---

### Phase C: Send Encrypted File Event

**Goal:** Build and dispatch `ChatMessageEncryptedFileHeaderEvent` (kind 15) from upload results.

**Files:**
- `desktopApp/.../ui/chats/ChatPane.kt` — send logic in `MessageInput()`
- `desktopApp/.../model/DesktopIAccount.kt` — add `sendNip17EncryptedFile()`

**Implementation:**

```kotlin
// In ChatPane.kt send handler — after upload completes
fun sendEncryptedFiles(
    uploads: List<Pair<UploadResult, AESGCM>>,
    recipients: List<PTag>,
    account: DesktopIAccount
) {
    for ((result, cipher) in uploads) {
        val eventTemplate = ChatMessageEncryptedFileHeaderEvent.build(
            to = recipients,
            url = result.blossom.url,
            cipher = cipher,
            mimeType = result.metadata.mimeType,
            hash = result.metadata.encryptedHash,
            size = result.metadata.encryptedSize,
            dimension = result.metadata.dimension,
            blurhash = result.metadata.blurhash,
            originalHash = result.metadata.originalHash
        )
        account.sendNip17EncryptedFile(eventTemplate)
    }
}
```

```kotlin
// DesktopIAccount.kt — new method (mirrors sendNip17PrivateMessage pattern)
suspend fun sendNip17EncryptedFile(
    eventTemplate: ChatMessageEncryptedFileHeaderEvent
) {
    // Same GiftWrap flow as sendNip17PrivateMessage()
    val wraps = NIP17Factory().createFileNIP17(
        event = eventTemplate,
        signer = signer
    )
    // Optimistically add to local chatroom
    // Send wraps to recipient DM inbox relays
    sendGiftWraps(wraps)
}
```

**Reference:** Android's `ChatFileSender.sendNIP17()` at `amethyst/.../upload/ChatFileSender.kt:46-71`

---

### Phase D: Receive & Display Encrypted Media

**Goal:** Encrypted media in received DMs renders inline with lock icon.

**Files:**
- `desktopApp/.../ui/chats/ChatPane.kt` — `ChatFileAttachment()` composable (line ~442)
- `desktopApp/.../service/media/EncryptedMediaService.kt` — already exists
- Potentially `commons/.../ui/chat/ChatMessageCompose.kt` if shared rendering needed

**Implementation:**

`ChatFileAttachment()` already exists at line 442 of ChatPane.kt. Enhance it:

1. **Extract cipher params:** Parse `key()`, `nonce()`, `mimeType()`, `url()` from `ChatMessageEncryptedFileHeaderEvent`
2. **Download & decrypt:** Call `EncryptedMediaService.downloadAndDecrypt(url, keyBytes, nonce)`
3. **Display:** Render decrypted bytes as image/video/audio based on MIME type
4. **Lock overlay:** `Box` with `Icon(Icons.Outlined.Lock)` in corner, semi-transparent background
5. **Loading state:** Blurhash placeholder while downloading/decrypting
6. **Error state:** If decryption fails, show "Could not decrypt file" with retry option

```kotlin
@Composable
fun ChatFileAttachment(
    event: ChatMessageEncryptedFileHeaderEvent,
    account: DesktopIAccount
) {
    val decryptedBytes by produceState<ByteArray?>(null) {
        val keyHex = event.key() ?: return@produceState
        val nonceHex = event.nonce() ?: return@produceState
        val url = event.url() ?: return@produceState
        value = try {
            EncryptedMediaService.downloadAndDecrypt(url, keyHex, nonceHex)
        } catch (e: Exception) {
            null // error state
        }
    }

    Box {
        when {
            decryptedBytes != null -> {
                // Render based on mimeType
                DecryptedMediaContent(decryptedBytes!!, event.mimeType())
            }
            else -> {
                // Blurhash placeholder or error
                EncryptedFilePlaceholder(event.blurhash())
            }
        }
        // Lock icon overlay
        Icon(
            Icons.Outlined.Lock,
            modifier = Modifier.align(Alignment.BottomEnd).size(20.dp)
        )
    }
}
```

---

### Phase E: Error Handling & Edge Cases

**Files:** Across all modified files above.

| Scenario | Handling |
|----------|----------|
| Upload fails mid-way | Show error toast, keep file in attachment row for retry |
| Network disconnect during upload | Catch IOException, show "Upload failed — check connection" |
| Decryption fails (wrong key) | Show "Could not decrypt" placeholder, no crash |
| Blossom server unreachable | Fallback to next server in user's kind 10063 list |
| Large file (>10MB) | Show progress indicator during encrypt + upload |
| Unsupported MIME type | Show generic file icon with filename + size |
| No Blossom servers configured | Disable attach button, show tooltip "Configure media servers in Settings" |
| NIP-04 mode active | Attach button hidden or disabled (encrypted files are NIP-17 only) |

## Technical Considerations

### Security
- New `AESGCM()` cipher per file (random key + nonce) — never reuse
- Encrypt before hashing — Blossom auth scoped to encrypted blob
- Plaintext never leaves device unencrypted
- GiftWrap ensures only recipients can see the kind 15 event

### Performance
- Encryption runs on `Dispatchers.IO` (non-blocking)
- Large files: stream encrypt if needed (current AESGCM works on byte arrays — fine for <50MB)
- Decrypted media cached in memory (LRU) to avoid re-downloading

### Architecture
- No new modules — extends existing desktop services
- Protocol layer (quartz) unchanged — fully reuses existing events/ciphers
- Follows Android patterns for consistency across platforms

## Acceptance Criteria

- [ ] Paperclip attach button visible in DM chat input (NIP-17 mode only)
- [ ] File picker opens, supports image/video/audio selection
- [ ] Selected files show as thumbnails above input with X to remove
- [ ] Drag-and-drop files onto chat area adds to attachments
- [ ] Send encrypts files with AES-GCM before upload to Blossom
- [ ] Kind 15 `ChatMessageEncryptedFileHeaderEvent` sent wrapped in GiftWrap
- [ ] Encryption indicator (lock icon) visible on attachments before send
- [ ] Received encrypted media downloads, decrypts, displays inline
- [ ] Lock icon overlay on received encrypted media in chat bubbles
- [ ] Wrong key / failed decryption shows error state, no crash
- [ ] Upload progress indicator during encrypt + upload
- [ ] No attach button when in NIP-04 mode

## Test Plan (from Phase 6 testing plan)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| 6.1 | DM file attach | Open DM → click paperclip → select file | File thumbnail appears above input with lock indicator |
| 6.2 | Send encrypted | Attach file → send | File uploads encrypted to Blossom, kind 15 event in GiftWrap |
| 6.3 | Receive encrypted | Receive DM with encrypted file (from Android) | File downloads, decrypts, displays in bubble with lock icon |
| 6.4 | Wrong key | View encrypted media where key doesn't match | "Could not decrypt" placeholder, no crash |

## Dependencies & Risks

| Dependency | Risk | Mitigation |
|-----------|------|------------|
| Blossom server availability | Upload fails | Retry + fallback to alternate servers |
| VLC for video playback | Encrypted video won't play without VLC | Graceful fallback for non-VLC systems |
| Android client for cross-platform test | Can't verify interop | Use Android emulator or second account |
| `DesktopBlossomClient` raw bytes upload | May only support File, not ByteArray | Add overload if needed |

## Sources & References

### Origin
- **Brainstorm document:** [docs/brainstorms/2026-03-18-desktop-dm-encrypted-media-brainstorm.md](docs/brainstorms/2026-03-18-desktop-dm-encrypted-media-brainstorm.md) — Key decisions: inline attach button UX, drag-drop support, lock icon overlay, full send+receive scope

### Internal References
- Android ChatFileUploader: `amethyst/.../upload/ChatFileUploader.kt:39-80`
- Android ChatFileSender: `amethyst/.../upload/ChatFileSender.kt:46-71`
- Desktop ChatPane: `desktopApp/.../ui/chats/ChatPane.kt`
- Desktop UploadOrchestrator: `desktopApp/.../service/upload/DesktopUploadOrchestrator.kt`
- Desktop EncryptedMediaService: `desktopApp/.../service/media/EncryptedMediaService.kt`
- Desktop ComposeNoteDialog (drag-drop pattern): `desktopApp/.../ui/ComposeNoteDialog.kt`
- Quartz ChatMessageEncryptedFileHeaderEvent: `quartz/.../nip17Dm/files/ChatMessageEncryptedFileHeaderEvent.kt`
- Quartz AESGCM: `quartz/.../utils/ciphers/AESGCM.kt`
- Blossom protocol research: `docs/brainstorms/2026-03-16-blossom-protocol-research.md`

### Gotchas (from learnings research)
- Hash encrypted blob, not plaintext, for Blossom auth `x` tag and `X-SHA-256` header
- New AESGCM cipher per file — never reuse key/nonce pair
- Include `["server", "domain"]` tag in kind 24242 auth to prevent replay
- NIP-04 does not support encrypted file headers — hide attach button in NIP-04 mode
