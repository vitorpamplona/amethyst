# Temporary File Cleanup Analysis

## Overview

Analysis of temporary file creation and cleanup patterns across the Amethyst codebase.
The goal is to identify opportunities for more aggressive cleanup — deleting temp files
as soon as they are no longer needed rather than deferring to cache cleanup.

## Temporary File Creation Sites (Android)

| Area | File | Creates | Cleanup | Notes |
|------|------|---------|---------|-------|
| Image compression | `MediaCompressor.kt:94` | Temp copy via `MediaCompressorFileUtils.from()` | UploadOrchestrator `finally` | Deferred to cache cleanup |
| Image metadata strip | `MetadataStripper.kt:199` | `stripped_*.jpg` in cacheDir | UploadOrchestrator `finally` | Deferred to cache cleanup |
| Video metadata strip | `MetadataStripper.kt:238` | `stripped_video_*.mp4` in cacheDir | UploadOrchestrator `finally` | Deferred to cache cleanup |
| Audio metadata strip | `MetadataStripper.kt:286` | `stripped_audio_*.m4a` in cacheDir | UploadOrchestrator `finally` | Deferred to cache cleanup |
| MP3 metadata strip | `MetadataStripper.kt:307,363` | `mp3_input_*` + `stripped_mp3_*` | Mixed: some immediate, some orchestrator | MP3 input file cleaned inline |
| File encryption | `EncryptFiles.kt:47` | `EncryptFiles*.encrypted` in cacheDir | UploadOrchestrator `finally` | Deferred to cache cleanup |
| URI temp copy | `MediaCompressorFileUtils.kt:41` | Random UUID temp file | Caller-dependent | No self-cleanup |
| Voice anonymization | `VoiceAnonymizationController.kt:85` | Distorted voice files | `deleteDistortedFiles()` explicit | Cleaned by controller |
| Video sharing | `ZoomableContentView.kt:905` | Temp video + sharable copy | Delayed GlobalScope (1 min) | Intentional delay for sharing |
| Camera capture | `TakePicture.kt:244` | Camera temp file | System/caller | Managed by camera provider |

## Temporary File Creation Sites (Desktop)

| Area | File | Creates | Cleanup | Notes |
|------|------|---------|---------|-------|
| Clipboard paste | `ClipboardPasteHandler.kt:43` | `clipboard_*.png` | `deleteOnExit()` only | Leaks until JVM exit |
| Image compression | `DesktopMediaCompressor.kt:42` | `stripped_*.jpg` | `deleteOnExit()` only | Leaks until JVM exit |

## The Upload Pipeline

The `UploadOrchestrator` is the central cleanup coordinator. Its `finally` blocks call
`deleteTempUri()` to delete intermediate files after upload completes. The pipeline stages:

```
1. MediaCompressorFileUtils.from()  --> temp copy of original URI
2. MediaCompressor.compress()       --> compressed file (may replace #1)
3. MetadataStripper.strip*()        --> stripped file (new temp)
4. (optional) EncryptFiles.encrypt() --> encrypted file (new temp)
5. Upload to server
6. finally: delete all intermediates via deleteTempUri()
```

Each stage may produce a new temp file. Currently, all intermediates accumulate and are
only cleaned up in the `finally` block after the upload succeeds or fails.

## Cleanup Patterns in Use

### 1. Chained Deletion (UploadOrchestrator)
- Most common pattern
- `finally` blocks at `L374-379` (upload) and `L410-415` (uploadEncrypted)
- Deletes all intermediate temp files after pipeline completes
- Checks `tempUri != originalUri` to avoid deleting user's original file

### 2. Delayed Deletion (Video Sharing)
- `ZoomableContentView.kt:953-960`
- Uses `GlobalScope.launch(Dispatchers.IO)` with 1 minute delay
- Intentional: allows recipient app time to read the shared file
- **Exception: this delay is necessary and should not be shortened**

### 3. Explicit Cleanup Calls (Voice)
- `VoiceAnonymizationController.deleteDistortedFiles()` at `L108-123`
- Called explicitly by `ShortNotePostViewModel` at `L1336`
- Good pattern: controller owns its temp files

### 4. deleteOnExit() (Desktop)
- Used by `ClipboardPasteHandler` and `DesktopMediaCompressor`
- Files persist until JVM process exits
- Not ideal for long-running desktop app

## Opportunities for More Aggressive Cleanup

### 1. Pipeline Stage Cleanup (UploadOrchestrator)

**Current:** All intermediates deleted in `finally` after upload.

**Proposed:** Delete each intermediate as soon as the next stage produces output.

Example: after `MetadataStripper` produces a stripped file, delete the compressed file
from the previous stage immediately, rather than keeping it alive through upload + finally.

### 2. Desktop Temp Files

**Current:** `deleteOnExit()` — files persist for entire app session.

**Proposed:** Delete immediately after upload completes, matching the Android
`UploadOrchestrator` pattern via `DesktopUploadOrchestrator`.

### 3. MediaCompressorFileUtils Intermediate

**Current:** `from()` creates a temp copy at `L41`. If compression produces a *different*
file, the original temp copy is orphaned until `UploadOrchestrator.finally`.

**Proposed:** `MediaCompressor` should delete the `from()` temp file immediately after
compression succeeds (when the compressed file differs from the input).

### 4. Voice Anonymization Intermediates

**Current:** `deleteDistortedFiles()` called after upload.

**Proposed:** Already reasonably aggressive. Could delete each intermediate distortion
result as soon as the next processing step completes, if there are multiple stages.

## Exception: Video Sharing

Sharing a video to other Android apps requires the temporary file to remain accessible
for at least 1 minute. The current `SHARED_VIDEO_CLEANUP_DELAY_MS` delay in
`ZoomableContentView.kt` handles this correctly and should **not** be made more aggressive.