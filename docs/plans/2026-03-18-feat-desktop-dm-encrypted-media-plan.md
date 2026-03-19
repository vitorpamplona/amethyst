---
title: "feat: Desktop DM Encrypted Media (NIP-17)"
type: feat
status: completed
date: 2026-03-18
deepened: 2026-03-18
origin: docs/brainstorms/2026-03-18-desktop-dm-encrypted-media-brainstorm.md
---

# feat: Desktop DM Encrypted Media (NIP-17)

## Enhancement Summary

**Deepened on:** 2026-03-18
**Sections enhanced:** 5 phases + security + performance + edge cases
**Research sources:** Source code analysis of all 9 key files, Android reference implementation, Blossom protocol research, existing learnings

### Key Improvements
1. Corrected `DesktopBlossomClient` — needs `ByteArray` overload (currently only accepts `File`)
2. `IAccount` interface needs `sendNip17EncryptedFile()` added (not just `DesktopIAccount`)
3. `NIP17Factory.createEncryptedFileNIP17()` already exists — plan incorrectly referenced `createFileNIP17()`
4. `DesktopMediaMetadata.compute()` reads file bytes internally — encrypted upload must avoid double-read
5. Added streaming encryption consideration for large files and memory pressure mitigation

### Critical Corrections from Source Code
- `DesktopBlossomAuth.createUploadAuth()` requires `size: Long` parameter — encrypted size, not original
- `DesktopBlossomClient.upload()` only accepts `File`, not `ByteArray` — needs overload or temp file
- `ChatMessageEncryptedFileHeaderEvent.build()` takes `cipher: AESGCM` directly — no manual key/nonce extraction needed
- `key()` and `nonce()` return tag values as parsed types (via `EncryptionKey`/`EncryptionNonce` tags)

---

## Overview

Implement full send + receive encrypted media support in desktop DM chat. Users can attach files via paperclip button or drag-and-drop, which get AES-GCM encrypted before upload to Blossom. Files are sent as `ChatMessageEncryptedFileHeaderEvent` (kind 15) wrapped in GiftWrap (NIP-59). Received encrypted media is downloaded, decrypted, and displayed inline with a lock icon overlay.

This is Phase 6 of the desktop media parity plan.

## Problem Statement / Motivation

Desktop DM chat (`ChatPane.kt`) supports text messages but has no file attachment capability. Android has a complete implementation (`ChatFileUploader` + `ChatFileSender`). Most protocol and service pieces exist on desktop already — this work wires them together with a desktop-native UX.

## Proposed Solution

Port Android's NIP-17 encrypted file flow to desktop, reusing existing protocol layer (quartz) and extending desktop services. Three workstreams: upload pipeline, send logic, and receive/display.

(see brainstorm: `docs/brainstorms/2026-03-18-desktop-dm-encrypted-media-brainstorm.md`)

---

## Implementation Phases

### Phase A: Encrypted Upload Pipeline

**Goal:** `DesktopUploadOrchestrator` gains `uploadEncrypted()` method.

**Files:**
- `desktopApp/.../service/upload/DesktopUploadOrchestrator.kt` — add `uploadEncrypted()`
- `desktopApp/.../service/upload/DesktopBlossomClient.kt` — add `ByteArray` upload overload
- `desktopApp/.../service/upload/DesktopBlossomAuth.kt` — no changes needed (already takes hash + size)
- `desktopApp/.../service/upload/DesktopMediaMetadata.kt` — no changes needed

**Implementation:**

```kotlin
// DesktopUploadOrchestrator.kt — new method
suspend fun uploadEncrypted(
    file: File,
    cipher: AESGCM,
    serverBaseUrl: String,
    signer: NostrSigner,
): EncryptedUploadResult {
    // 1. Compute pre-encryption metadata (dimensions, blurhash, mime, originalHash)
    //    NOTE: DesktopMediaMetadata.compute() reads file bytes internally for SHA256
    val metadata = DesktopMediaMetadata.compute(file)

    // 2. Read file bytes and encrypt
    val plaintext = file.readBytes()
    val encrypted = cipher.encrypt(plaintext)

    // 3. Compute SHA256 of ENCRYPTED blob (critical: not plaintext)
    val encryptedHash = sha256(encrypted).toHexKey()
    val encryptedSize = encrypted.size.toLong()

    // 4. Create Blossom auth with ENCRYPTED hash and size
    val authHeader = DesktopBlossomAuth.createUploadAuth(
        hash = encryptedHash,
        size = encryptedSize,
        alt = "Encrypted upload",
        signer = signer,
    )

    // 5. Upload encrypted blob (needs ByteArray overload on client)
    val result = client.upload(
        bytes = encrypted,
        contentType = "application/octet-stream", // encrypted blob, not original mime
        serverBaseUrl = serverBaseUrl,
        authHeader = authHeader,
    )

    return EncryptedUploadResult(
        blossom = result,
        metadata = metadata,           // pre-encryption metadata (dimensions, blurhash, mime)
        encryptedHash = encryptedHash,
        encryptedSize = encryptedSize.toInt(),
    )
}
```

```kotlin
// New data class alongside existing UploadResult
data class EncryptedUploadResult(
    val blossom: BlossomUploadResult,
    val metadata: MediaMetadata,       // original file metadata
    val encryptedHash: String,         // SHA256 of encrypted blob
    val encryptedSize: Int,            // size of encrypted blob
)
```

```kotlin
// DesktopBlossomClient.kt — add ByteArray overload
suspend fun upload(
    bytes: ByteArray,
    contentType: String,
    serverBaseUrl: String,
    authHeader: String?,
): BlossomUploadResult = withContext(Dispatchers.IO) {
    val apiUrl = serverBaseUrl.removeSuffix("/") + "/upload"
    val requestBody = bytes.toRequestBody(contentType.toMediaType())

    val requestBuilder = Request.Builder()
        .url(apiUrl)
        .put(requestBody)

    authHeader?.let { requestBuilder.addHeader("Authorization", it) }

    val response = okHttpClient.newCall(requestBuilder.build()).execute()
    response.use {
        if (!it.isSuccessful) {
            val reason = it.headers["X-Reason"] ?: it.code.toString()
            throw RuntimeException("Upload failed ($serverBaseUrl): $reason")
        }
        JsonMapper.fromJson<BlossomUploadResult>(it.body.string())
    }
}
```

### Research Insights — Phase A

**Critical gotchas (from Blossom protocol research + source code analysis):**

| Issue | Detail | Solution |
|-------|--------|----------|
| Hash mismatch | `X-SHA-256` header must match encrypted blob hash, not plaintext | Compute SHA256 after `cipher.encrypt()` |
| Content-Type | Upload encrypted blob as `application/octet-stream`, not original MIME | Server stores opaque blob |
| Auth size | `DesktopBlossomAuth.createUploadAuth(size=)` must be encrypted size | Pass `encrypted.size.toLong()` |
| Double file read | `DesktopMediaMetadata.compute()` calls `file.readBytes()` internally | Acceptable — metadata computation is separate from encryption read |
| Memory pressure | `file.readBytes()` + `cipher.encrypt()` = 2x file size in memory | For files <50MB this is fine; for larger files consider streaming (future) |

**Security considerations:**
- Generate fresh `AESGCM()` per file — never reuse key/nonce pairs (AES-GCM nonce reuse completely breaks confidentiality)
- Clear `plaintext` ByteArray after encryption (`plaintext.fill(0)`) to minimize exposure window
- Encrypted blob content type should be `application/octet-stream` to avoid leaking file type to Blossom server

---

### Phase B: DM File Attach UI in ChatPane

**Goal:** Paperclip button, thumbnail row, drag-and-drop in `ChatPane.kt`.

**Files:**
- `desktopApp/.../ui/chats/ChatPane.kt` — modify `MessageInput()` composable and `ChatPane()` for drag-drop

**Implementation:**

Modify `MessageInput()` (currently lines 527-627) — add parameters and UI elements:

```kotlin
// Updated MessageInput signature
@Composable
private fun MessageInput(
    messageText: String,
    isNip17: Boolean,
    requiresNip17: Boolean,
    canSend: Boolean,
    attachedFiles: List<File>,          // NEW
    onMessageChange: (String) -> Unit,
    onToggleNip17: () -> Unit,
    onAttachFiles: (List<File>) -> Unit, // NEW
    onRemoveFile: (Int) -> Unit,         // NEW
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        // Attachment thumbnail row (above text input)
        if (attachedFiles.isNotEmpty()) {
            AttachmentRow(
                files = attachedFiles,
                isEncrypted = isNip17,
                onRemove = onRemoveFile,
            )
            Spacer(Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Paperclip attach button (only in NIP-17 mode)
            if (isNip17) {
                IconButton(
                    onClick = { /* open JFileChooser */ },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach file",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Existing OutlinedTextField...
            // Existing Send button...
        }

        // Existing NIP-17 indicator...
    }
}
```

```kotlin
// AttachmentRow composable
@Composable
private fun AttachmentRow(
    files: List<File>,
    isEncrypted: Boolean,
    onRemove: (Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        itemsIndexed(files) { index, file ->
            Box(modifier = Modifier.size(64.dp)) {
                // Thumbnail (image preview or file icon)
                AttachmentThumbnail(file)

                // Remove button (top-right)
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
                ) {
                    Icon(Icons.Default.Close, "Remove", Modifier.size(12.dp))
                }

                // Lock icon overlay (bottom-end, only when encrypted)
                if (isEncrypted) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(2.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
```

**File picker (JFileChooser pattern from ComposeNoteDialog):**

```kotlin
// File picker helper — runs on AWT thread
private fun openFilePicker(onFilesSelected: (List<File>) -> Unit) {
    val chooser = JFileChooser().apply {
        isMultiSelectionEnabled = true
        fileFilter = FileNameExtensionFilter(
            "Media files",
            "jpg", "jpeg", "png", "gif", "webp", "mp4", "webm", "mov",
            "mp3", "ogg", "wav", "flac", "aac",
        )
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        onFilesSelected(chooser.selectedFiles.toList())
    }
}
```

**Drag-and-drop on ChatPane (wrapping the Column):**

```kotlin
// In ChatPane() — wrap the main Column with drag-drop
var isDragOver by remember { mutableStateOf(false) }

Column(
    modifier = modifier
        .fillMaxSize()
        .onExternalDrag(
            onDragStart = { isDragOver = true },
            onDragExit = { isDragOver = false },
            onDrop = { state ->
                isDragOver = false
                val files = state.dragData
                    .let { it as? DragData.FilesList }
                    ?.readFiles()
                    ?.mapNotNull { uri -> File(URI(uri)) }
                    ?: emptyList()
                // Add to attachedFiles state
                attachedFiles.addAll(files)
            },
        )
        .then(
            if (isDragOver) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            } else {
                Modifier
            }
        ),
) {
    // ... existing ChatPane content
}
```

### Research Insights — Phase B

**Compose best practices applied:**

| Pattern | Recommendation | Rationale |
|---------|---------------|-----------|
| State location | `attachedFiles` as `mutableStateListOf<File>()` in `ChatPane`, not `MessageInput` | State hoisted to parent that also handles send/upload logic |
| File picker thread | `JFileChooser` must run on EDT; use `withContext(Dispatchers.Main)` or `SwingUtilities.invokeLater` | AWT file dialogs block; Compose coroutines must not be blocked |
| Drag-drop modifier | `Modifier.onExternalDrag` from `compose.ui` | Already used in ComposeNoteDialog — consistent pattern |
| Thumbnail rendering | Use `ImageIO.read()` for image thumbnails; generic icon for audio/video | Avoid loading full-resolution images; scale down for 64dp thumbnails |
| `canSend` update | `canSend` should also be true when `attachedFiles.isNotEmpty()` even if `messageText.isEmpty()` | Allow sending file-only messages (no text required) |

**Desktop UX considerations:**
- Keyboard shortcut: Cmd+V / Ctrl+V paste should also add clipboard images to attachments (future enhancement)
- Tooltip on disabled attach button (NIP-04 mode): "Switch to NIP-17 to send files"
- Maximum attachment count: limit to 10 files to prevent UI overflow
- File size validation: warn on files >50MB before attempting upload

---

### Phase C: Send Encrypted File Event

**Goal:** Build and dispatch `ChatMessageEncryptedFileHeaderEvent` (kind 15) from upload results.

**Files:**
- `desktopApp/.../ui/chats/ChatPane.kt` — send logic in `ChatPane()` scope
- `desktopApp/.../model/DesktopIAccount.kt` — add `sendNip17EncryptedFile()`
- `commons/.../model/IAccount.kt` — add interface method

**Implementation:**

```kotlin
// IAccount.kt — add to interface (commons/commonMain)
/** Send a NIP-17 gift-wrapped encrypted file header */
suspend fun sendNip17EncryptedFile(template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>)
```

```kotlin
// DesktopIAccount.kt — implement (mirrors sendNip17PrivateMessage exactly)
override suspend fun sendNip17EncryptedFile(
    template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>,
) {
    if (!isWriteable()) return

    val result = NIP17Factory().createEncryptedFileNIP17(template, signer)

    // Optimistic local add — use the inner event
    val innerEvent = result.msg as ChatMessageEncryptedFileHeaderEvent
    addEventToChatroom(innerEvent, innerEvent.chatroomKey(pubKey))

    // Collect wraps with target relays and send
    val batch = result.wraps.map { wrap ->
        val recipientKey = wrap.recipientPubKey()
        val targetRelays = if (recipientKey != null) {
            val dmRelays = localCache.getOrCreateUser(recipientKey)
                .dmInboxRelays()?.toSet()
            dmRelays?.ifEmpty { null } ?: relayManager.connectedRelays.value
        } else {
            relayManager.connectedRelays.value
        }
        wrap to targetRelays
    }

    scope.launch { dmSendTracker.sendBatch(batch) }
}
```

```kotlin
// ChatPane.kt — send handler for encrypted files
// In ChatPane composable scope, after upload completes:
private suspend fun sendEncryptedFiles(
    uploads: List<Pair<EncryptedUploadResult, AESGCM>>,
    roomKey: ChatroomKey,
    account: IAccount,
    cacheProvider: ICacheProvider,
) {
    val recipients = roomKey.users.map { cacheProvider.getOrCreateUser(it).toPTag() }

    for ((result, cipher) in uploads) {
        val template = ChatMessageEncryptedFileHeaderEvent.build(
            to = recipients,
            url = result.blossom.url,
            cipher = cipher,                           // passes algo, key, nonce automatically
            mimeType = result.metadata.mimeType,
            hash = result.encryptedHash,               // hash of encrypted blob
            size = result.encryptedSize,
            dimension = result.metadata.width?.let { w ->
                result.metadata.height?.let { h -> DimensionTag(w, h) }
            },
            blurhash = result.metadata.blurhash,
            originalHash = result.metadata.sha256,     // hash of original plaintext
        )
        account.sendNip17EncryptedFile(template)
    }
}
```

**Full send flow in ChatPane (upload + send):**

```kotlin
// In ChatPane composable — triggered by Send button when attachedFiles.isNotEmpty()
scope.launch {
    val orchestrator = DesktopUploadOrchestrator()
    val server = /* user's default Blossom server from kind 10063 */
    val uploads = mutableListOf<Pair<EncryptedUploadResult, AESGCM>>()

    for (file in attachedFiles) {
        val cipher = AESGCM()  // fresh cipher per file
        try {
            val result = orchestrator.uploadEncrypted(file, cipher, server, account.signer)
            uploads.add(result to cipher)
        } catch (e: Exception) {
            // Show error, keep remaining files for retry
            println("Upload failed for ${file.name}: ${e.message}")
        }
    }

    if (uploads.isNotEmpty()) {
        sendEncryptedFiles(uploads, roomKey, account, cacheProvider)
        attachedFiles.clear()

        // Also send text message if present
        if (messageState.canSend) {
            messageState.send()
            messageState.clear()
        }
    }
}
```

### Research Insights — Phase C

**Correctness checks from source code:**

| Verified | Detail |
|----------|--------|
| `NIP17Factory.createEncryptedFileNIP17()` | Exists at `NIP17Factory.kt:86-97` — takes `EventTemplate<ChatMessageEncryptedFileHeaderEvent>` |
| `ChatMessageEncryptedFileHeaderEvent.build()` | Takes `cipher: AESGCM` directly — auto-extracts algo/key/nonce via `encryptionAlgo(cipher.name())`, `encryptionKey(cipher.keyBytes)`, `encryptionNonce(cipher.nonce)` |
| Android pattern | `Account.sendNip17EncryptedFile()` at line 1612 calls `NIP17Factory().createEncryptedFileNIP17(template, signer)` then `broadcastPrivately(wraps)` |
| Desktop pattern | `DesktopIAccount.sendNip17PrivateMessage()` at line 128 — same structure, replace `createMessageNIP17` with `createEncryptedFileNIP17` |

**Concurrency considerations:**
- Upload files sequentially (not parallel) to avoid memory pressure from multiple concurrent encryptions
- Use `supervisorScope` if you want one failed upload to not cancel others
- Send events can be parallelized (each is independent after upload)

**Interface change impact:**
- Adding `sendNip17EncryptedFile` to `IAccount` requires implementation in Android's `Account.kt` too — but it already has it as a non-override method. Just add `override` keyword.

---

### Phase D: Receive & Display Encrypted Media

**Goal:** Encrypted media in received DMs renders inline with lock icon.

**Files:**
- `desktopApp/.../ui/chats/ChatPane.kt` — enhance `ChatFileAttachment()` (line 442)
- `desktopApp/.../service/media/EncryptedMediaService.kt` — already exists, enhance with caching

**Current state:** `ChatFileAttachment` is called at line 442 but its implementation is minimal or placeholder. The event is already detected as `ChatMessageEncryptedFileHeaderEvent`.

**Implementation:**

```kotlin
@Composable
private fun ChatFileAttachment(event: ChatMessageEncryptedFileHeaderEvent) {
    // Parse cipher params from event tags
    val url = event.url()
    val keyBytes = event.key()     // returns ByteArray? (parsed from EncryptionKey tag)
    val nonceBytes = event.nonce() // returns ByteArray? (parsed from EncryptionNonce tag)
    val mimeType = event.mimeType()
    val blurhashStr = event.blurhash()

    if (url.isNullOrEmpty() || keyBytes == null || nonceBytes == null) {
        // Missing encryption params — show error
        EncryptedFileError("Missing encryption data")
        return
    }

    // Async download + decrypt with proper state management
    var decryptionState by remember(event.id) {
        mutableStateOf<DecryptionState>(DecryptionState.Loading)
    }

    LaunchedEffect(event.id) {
        decryptionState = try {
            val bytes = EncryptedMediaService.downloadAndDecrypt(url, keyBytes, nonceBytes)
            DecryptionState.Success(bytes)
        } catch (e: Exception) {
            DecryptionState.Error(e.message ?: "Decryption failed")
        }
    }

    Box(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .heightIn(max = 300.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        when (val state = decryptionState) {
            is DecryptionState.Loading -> {
                // Blurhash placeholder or shimmer
                if (blurhashStr != null) {
                    BlurhashPlaceholder(
                        blurhash = blurhashStr,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                    )
                }
            }

            is DecryptionState.Success -> {
                DecryptedMediaContent(
                    bytes = state.bytes,
                    mimeType = mimeType,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is DecryptionState.Error -> {
                EncryptedFileError(state.message)
            }
        }

        // Lock icon overlay (always visible)
        Icon(
            Icons.Default.Lock,
            contentDescription = "End-to-end encrypted",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(20.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    RoundedCornerShape(4.dp),
                )
                .padding(2.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private sealed class DecryptionState {
    data object Loading : DecryptionState()
    data class Success(val bytes: ByteArray) : DecryptionState()
    data class Error(val message: String) : DecryptionState()
}
```

```kotlin
// DecryptedMediaContent — render based on MIME type
@Composable
private fun DecryptedMediaContent(
    bytes: ByteArray,
    mimeType: String?,
    modifier: Modifier = Modifier,
) {
    when {
        mimeType?.startsWith("image/") == true -> {
            val bitmap = remember(bytes) {
                org.jetbrains.skia.Image.makeFromEncoded(bytes)
                    .toComposeImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = "Encrypted image",
                contentScale = ContentScale.Fit,
                modifier = modifier,
            )
        }
        mimeType?.startsWith("video/") == true -> {
            // Show video thumbnail or play button
            // Full video playback requires writing decrypted bytes to temp file for VLC
            VideoFilePlaceholder(bytes.size, modifier)
        }
        mimeType?.startsWith("audio/") == true -> {
            AudioFilePlaceholder(bytes.size, modifier)
        }
        else -> {
            GenericFilePlaceholder(mimeType, bytes.size, modifier)
        }
    }
}
```

### Research Insights — Phase D

**Compose state management:**
- Use `LaunchedEffect(event.id)` not `produceState` — better control over loading/error states via sealed class
- `remember(event.id)` keys state to the event, preventing re-download on recomposition
- `remember(bytes)` for Skia bitmap conversion prevents recreating bitmap every recomposition

**Performance considerations:**

| Concern | Mitigation |
|---------|------------|
| Re-download on scroll | Add in-memory LRU cache to `EncryptedMediaService` keyed by URL |
| Large decrypted images in memory | Scale down to max display size (300dp) before caching |
| Bitmap creation from bytes | `org.jetbrains.skia.Image.makeFromEncoded()` is efficient for JVM |
| Video/audio playback | Requires writing decrypted bytes to temp file (VLC needs file path). Use `File.createTempFile()` with `.deleteOnExit()` |

**Add caching to EncryptedMediaService:**

```kotlin
object EncryptedMediaService {
    private val httpClient = OkHttpClient()
    private val cache = LruCache<String, ByteArray>(maxSize = 20) // ~20 decrypted files

    suspend fun downloadAndDecrypt(
        url: String,
        keyBytes: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        cache.get(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val encryptedBytes = response.use {
                if (!it.isSuccessful) throw RuntimeException("Download failed: ${it.code}")
                it.body.bytes()
            }

            val cipher = AESGCM(keyBytes, nonce)
            val decrypted = cipher.decrypt(encryptedBytes)
            cache.put(url, decrypted)
            decrypted
        }
    }
}
```

**Security note:** Cached decrypted bytes are in JVM heap memory. This is acceptable for a desktop app (no shared memory concerns). Consider cache eviction on app minimize if paranoid.

---

### Phase E: Error Handling & Edge Cases

**Files:** Across all modified files above.

| Scenario | Handling | Implementation |
|----------|----------|----------------|
| Upload fails mid-way | Show error snackbar, keep file in attachment row for retry | Catch in upload loop, skip failed file, continue others |
| Network disconnect during upload | Catch `IOException`, show "Upload failed — check connection" | OkHttp throws on network failure |
| Decryption fails (wrong key) | Show "Could not decrypt" placeholder, no crash | `DecryptionState.Error` sealed class variant |
| Blossom server unreachable | Fallback to next server in user's kind 10063 list | Query `BlossomServersEvent` for alternatives |
| Large file (>10MB) | Show progress indicator during encrypt + upload | Extend `DesktopUploadTracker` for encrypted uploads |
| Unsupported MIME type | Show generic file icon with filename + size | `GenericFilePlaceholder` composable |
| No Blossom servers configured | Disable attach button, show tooltip | Check server list before enabling button |
| NIP-04 mode active | Attach button hidden (encrypted files are NIP-17 only) | `if (isNip17)` guard on paperclip button |
| Corrupt encrypted blob | `AESGCM.decrypt()` throws `AEADBadTagException` | Catch specifically, show "File corrupted or tampered" |
| Duplicate upload (same file) | Each send generates new cipher — different encrypted blob | Intentional: no deduplication for privacy |
| Rapid send taps | Disable send button during upload/send | `isUploading` state flag |

### Research Insights — Phase E

**Error hierarchy (from AESGCM source):**
- `AESGCM.decrypt()` uses JCE `Cipher` on JVM — throws `AEADBadTagException` for wrong key (not generic exception)
- `AESGCM.decryptOrNull()` exists — returns null instead of throwing. Prefer this for UI code.

**Security edge cases:**
- Never log encryption keys, nonces, or decrypted content
- Temp files for video playback must be deleted after use (`deleteOnExit()` + explicit delete on composable disposal)
- Don't show detailed error messages that could leak cipher state ("wrong key" is fine, "key was X but expected Y" is not)

---

## Technical Considerations

### Security
- New `AESGCM()` cipher per file (random key + nonce) — never reuse
- Encrypt before hashing — Blossom auth scoped to encrypted blob
- Plaintext never leaves device unencrypted
- GiftWrap ensures only recipients can see the kind 15 event
- Zero `plaintext` ByteArray after encryption to minimize exposure window
- Upload content type is `application/octet-stream` (doesn't leak file type to server)
- Include `["server", "domain"]` tag in kind 24242 auth to prevent replay attacks

### Performance
- Encryption runs on `Dispatchers.IO` (non-blocking)
- Sequential file upload (not parallel) to limit memory to 2x single file size
- In-memory LRU cache for decrypted media (20 entries) avoids re-downloading
- `org.jetbrains.skia.Image.makeFromEncoded()` for efficient bitmap creation
- Large files (>50MB): warn user before upload; consider streaming in future

### Architecture
- No new modules — extends existing desktop services
- Protocol layer (quartz) unchanged — fully reuses existing events/ciphers
- Follows Android patterns for consistency across platforms
- `IAccount` interface gains one new method; Android already has implementation (add `override`)

---

## Acceptance Criteria

- [x] Paperclip attach button visible in DM chat input (NIP-17 mode only)
- [x] File picker opens, supports image/video/audio selection
- [x] Selected files show as thumbnails above input with X to remove
- [x] Drag-and-drop files onto chat area adds to attachments (visual drop indicator)
- [x] Send encrypts files with AES-GCM before upload to Blossom
- [x] Kind 15 `ChatMessageEncryptedFileHeaderEvent` sent wrapped in GiftWrap
- [x] Lock icon overlay visible on attachment thumbnails before send
- [x] Received encrypted media downloads, decrypts, displays inline
- [x] Lock icon overlay on received encrypted media in chat bubbles
- [x] Wrong key / failed decryption shows error state, no crash
- [x] Upload progress indicator during encrypt + upload
- [x] No attach button when in NIP-04 mode
- [x] `canSend` true when files attached (even without text)
- [x] Send button disabled during active upload

## Test Plan (from Phase 6 testing plan)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| 6.1 | DM file attach | Open DM → click paperclip → select file | File thumbnail appears above input with lock indicator |
| 6.2 | Send encrypted | Attach file → send | File uploads encrypted to Blossom, kind 15 event in GiftWrap sent |
| 6.3 | Receive encrypted | Receive DM with encrypted file (from Android) | File downloads, decrypts, displays in bubble with lock icon |
| 6.4 | Wrong key | View encrypted media where key doesn't match | "Could not decrypt" placeholder, no crash |
| 6.5 | Drag-drop attach | Drag image onto DM chat | File appears in attachment row with lock icon |
| 6.6 | Multiple files | Attach 3 files → send | All 3 upload encrypted, each gets own kind 15 event |
| 6.7 | Large file | Attach 15MB image → send | Progress shown, upload succeeds |
| 6.8 | NIP-04 mode | Toggle to NIP-04 → check attach button | Attach button hidden/disabled |

## Dependencies & Risks

| Dependency | Risk | Mitigation |
|-----------|------|------------|
| Blossom server availability | Upload fails | Retry + fallback to alternate servers from kind 10063 |
| VLC for video playback | Encrypted video won't play without VLC | Show "Download" button for video; image display works regardless |
| Android client for cross-platform test | Can't verify interop | Use Android emulator or second account |
| `DesktopBlossomClient` ByteArray overload | New method needed | Simple addition — uses OkHttp `ByteArray.toRequestBody()` |
| `IAccount` interface change | Requires Android-side `override` keyword | Android already has the method, just not `override` |

## Sources & References

### Origin
- **Brainstorm document:** [docs/brainstorms/2026-03-18-desktop-dm-encrypted-media-brainstorm.md](docs/brainstorms/2026-03-18-desktop-dm-encrypted-media-brainstorm.md) — Key decisions: inline attach button UX, drag-drop support, lock icon overlay, full send+receive scope

### Internal References (verified from source code)
- Android `ChatFileUploader.justUploadNIP17()`: `amethyst/.../upload/ChatFileUploader.kt:39-80`
- Android `ChatFileSender.sendNIP17()`: `amethyst/.../upload/ChatFileSender.kt:46-71`
- Android `Account.sendNip17EncryptedFile()`: `amethyst/.../model/Account.kt:1612-1617`
- Desktop `ChatPane.kt`: `desktopApp/.../ui/chats/ChatPane.kt` (628 lines)
- Desktop `DesktopUploadOrchestrator`: `desktopApp/.../service/upload/DesktopUploadOrchestrator.kt` (78 lines)
- Desktop `DesktopBlossomClient`: `desktopApp/.../service/upload/DesktopBlossomClient.kt` (74 lines)
- Desktop `DesktopBlossomAuth`: `desktopApp/.../service/upload/DesktopBlossomAuth.kt` (43 lines)
- Desktop `DesktopMediaMetadata`: `desktopApp/.../service/upload/DesktopMediaMetadata.kt` (89 lines)
- Desktop `EncryptedMediaService`: `desktopApp/.../service/media/EncryptedMediaService.kt` (57 lines)
- Desktop `DesktopIAccount`: `desktopApp/.../model/DesktopIAccount.kt`
- Desktop `ComposeNoteDialog` (drag-drop pattern): `desktopApp/.../ui/ComposeNoteDialog.kt`
- Commons `IAccount` interface: `commons/.../model/IAccount.kt:106-113`
- Quartz `ChatMessageEncryptedFileHeaderEvent.build()`: `quartz/.../nip17Dm/files/ChatMessageEncryptedFileHeaderEvent.kt:80-110`
- Quartz `NIP17Factory.createEncryptedFileNIP17()`: `quartz/.../nip17Dm/NIP17Factory.kt:86-97`
- Quartz `AESGCM`: `quartz/.../utils/ciphers/AESGCM.kt`
- Blossom protocol research: `docs/brainstorms/2026-03-16-blossom-protocol-research.md`

### Gotchas (from learnings research + source verification)
- Hash encrypted blob, not plaintext, for Blossom auth `x` tag and `X-SHA-256` header
- New AESGCM cipher per file — never reuse key/nonce pair (nonce reuse breaks AES-GCM completely)
- Include `["server", "domain"]` tag in kind 24242 auth to prevent replay
- NIP-04 does not support encrypted file headers — hide attach button in NIP-04 mode
- `DesktopBlossomClient.upload()` only accepts `File` — needs `ByteArray` overload for encrypted blobs
- `DesktopBlossomAuth.createUploadAuth()` requires `size: Long` — must be encrypted blob size
- Content-Type for encrypted upload must be `application/octet-stream`, not original MIME
- `AESGCM.decryptOrNull()` exists — prefer over `decrypt()` for UI code (no exception on wrong key)
