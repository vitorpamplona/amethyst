# Temporary File Cleanup Analysis

## Overview

Analysis of temporary file creation and cleanup patterns across the Amethyst codebase.
The goal is to identify opportunities for more aggressive cleanup — deleting temp files
as soon as they are no longer needed rather than deferring to cache cleanup.

## Temporary File Creation Sites (Android)

| Area | File | Creates | Cleanup | Status |
|------|------|---------|---------|--------|
| Image compression | `MediaCompressor.kt:94` | Temp copy via `MediaCompressorFileUtils.from()` | Deleted immediately after compression | FIXED |
| Image metadata strip | `MetadataStripper.kt:199` | `stripped_*.jpg` in cacheDir | Deleted eagerly by orchestrator | FIXED |
| Video metadata strip | `MetadataStripper.kt:238` | `stripped_video_*.mp4` in cacheDir | Deleted eagerly by orchestrator | FIXED |
| Audio metadata strip | `MetadataStripper.kt:286` | `stripped_audio_*.m4a` in cacheDir | Deleted eagerly by orchestrator | FIXED |
| MP3 metadata strip | `MetadataStripper.kt:307,363` | `mp3_input_*` + `stripped_mp3_*` | Mixed: some immediate, some orchestrator | Already OK |
| File encryption | `EncryptFiles.kt:47` | `EncryptFiles*.encrypted` in cacheDir | Deleted eagerly by orchestrator | FIXED |
| URI temp copy | `MediaCompressorFileUtils.kt:41` | Random UUID temp file | Deleted by MediaCompressor after use | FIXED |
| Video compression | `VideoCompressionHelper.kt` | Compressed video in app storage | Abandoned file deleted when larger than original | FIXED |
| Voice anonymization | `VoiceAnonymizationController.kt:85` | Distorted voice files | `deleteDistortedFiles()` explicit | Already OK |
| Video sharing | `ZoomableContentView.kt:905` | Temp video + sharable copy | Delayed GlobalScope (1 min) | Skipped (intentional) |
| Camera capture | `TakePicture.kt:244` | Camera temp file | System/caller | Skipped (system-managed) |

## Temporary File Creation Sites (Desktop)

| Area | File | Creates | Cleanup | Notes |
|------|------|---------|---------|-------|
| Clipboard paste | `ClipboardPasteHandler.kt:43` | `clipboard_*.png` | `deleteOnExit()` only | Leaks until JVM exit |
| Image compression | `DesktopMediaCompressor.kt:42` | `stripped_*.jpg` | `deleteOnExit()` only | Leaks until JVM exit |

## The Upload Pipeline

The `UploadOrchestrator` is the central cleanup coordinator. Each intermediate temp file
is now deleted as soon as the next pipeline stage produces its output:

```
1. MediaCompressorFileUtils.from()   --> temp copy of original URI
2. MediaCompressor.compress()        --> compressed file; temp copy from #1 deleted immediately
3. MetadataStripper.strip*()         --> stripped file; compressed file from #2 deleted immediately
4. (optional) EncryptFiles.encrypt() --> encrypted file; stripped file from #3 deleted immediately
5. Upload to server
6. finally: delete the last remaining intermediate
```

## What Was Fixed

### 1. MediaCompressor temp file leak (MediaCompressor.kt)
- `MediaCompressorFileUtils.from()` created a temp copy that was never deleted
- Now deleted immediately after `Compressor.compress()` produces a separate output file
- Also cleaned up on compression failure (catch block)

### 2. Eager pipeline cleanup (UploadOrchestrator.kt)
- `upload()`: compressed file deleted right after stripping produces `finalUri`
- `uploadEncrypted()`: compressed file deleted after stripping, stripped file deleted
  after encryption — only the encrypted file survives until after upload
- Cancel path also cleans up compressed file via `.also {}` block
- `finally` block now only handles the last surviving intermediate

### 3. Abandoned compressed video (VideoCompressionHelper.kt)
- When compressed video is larger than original, the original is used instead
- The abandoned compressed file was leaked — now deleted before returning

## What Was Skipped

### Desktop temp files (out of scope for this change)
- `ClipboardPasteHandler.kt` and `DesktopMediaCompressor.kt` use `deleteOnExit()`
- Files persist until JVM process exits — not ideal for a long-running desktop app
- `DesktopUploadOrchestrator.kt` uses bare `processedFile.delete()` with no error
  handling or logging, diverging from the Android `deleteTempUri` pattern
- **Reason:** User requested Android-only focus for this iteration

### Voice anonymization intermediates
- `VoiceAnonymizationController.deleteDistortedFiles()` is already reasonably aggressive
- Called explicitly by `ShortNotePostViewModel` after upload
- **Reason:** Already working well, no leak identified

### Video sharing delay
- `ZoomableContentView.kt` uses a 1-minute delay before cleanup
- **Reason:** Intentional — receiving app needs time to read the shared file

### Camera capture temp files
- `TakePicture.kt` creates temp files via camera provider
- **Reason:** Managed by the Android system/camera provider, not our responsibility

## Exception: Video Sharing

Sharing a video to other Android apps requires the temporary file to remain accessible
for at least 1 minute. The current `SHARED_VIDEO_CLEANUP_DELAY_MS` delay in
`ZoomableContentView.kt` handles this correctly and should **not** be made more aggressive.

## Manual Test Plan

### Setup

Enable `adb logcat` filtering to observe cleanup behavior:

```bash
adb logcat -s MediaCompressor:* UploadOrchestrator:* VideoCompressionHelper:* MetadataStripper:*
```

To verify temp files are actually being deleted, monitor the cache directory before and
after each test:

```bash
adb shell "ls -la /data/data/com.vitorpamplona.amethyst/cache/ | grep -E 'stripped_|EncryptFiles|mp3_input|stripped_mp3|stripped_video|stripped_audio'"
```

### Test 1: Image upload with compression

1. Open a new note compose screen
2. Attach a JPEG photo from the gallery
3. Set compression quality to Medium
4. Post the note
5. **Verify in logcat:**
   - `MediaCompressor: Image compression success` appears
   - `MediaCompressor: Failed to delete temp file` does NOT appear
   - `UploadOrchestrator: Deleted temp file` appears (for the stripped file after upload)
6. **Verify in cache dir:** No `stripped_*.jpg` files remain after upload completes

### Test 2: Image upload without compression

1. Open a new note compose screen
2. Attach a JPEG photo from the gallery
3. Set compression quality to Uncompressed
4. Post the note
5. **Verify in logcat:**
   - No `MediaCompressor` compression log appears
   - `UploadOrchestrator: Deleted temp file` appears for the stripped file
6. **Verify in cache dir:** No `stripped_*` files remain

### Test 3: Video upload with compression

1. Open a new note compose screen
2. Attach a video from the gallery
3. Set compression quality to Medium
4. Post the note
5. **Verify in logcat:**
   - `VideoCompressionHelper: Compression success` appears
   - `UploadOrchestrator: Deleted temp file` appears
6. **Verify in cache dir:** No `stripped_video_*.mp4` files remain

### Test 4: Video compression produces larger file

1. Attach a very small or already-compressed video
2. Set compression to Low quality
3. Post the note
4. **Verify in logcat:**
   - `VideoCompressionHelper: Compressed file larger than original. Using original.` appears
   - The compressed file is deleted (no orphaned file in cache)

### Test 5: Audio/voice message upload

1. Record a voice message in a note or reply
2. Send it
3. **Verify in logcat:**
   - `UploadOrchestrator: Deleted temp file` appears for the stripped audio
4. **Verify in cache dir:** No `stripped_audio_*.m4a` files remain

### Test 6: MP3 upload

1. Attach an MP3 file (with ID3 tags) from the file picker
2. Post the note
3. **Verify in logcat:**
   - `MetadataStripper: Stripped ID3 tags from MP3` appears
   - `UploadOrchestrator: Deleted temp file` appears
4. **Verify in cache dir:** No `mp3_input_*` or `stripped_mp3_*` files remain

### Test 7: Encrypted file upload (NIP-44 DM)

1. Open a DM conversation
2. Attach an image
3. Send the message (triggers encrypted upload path)
4. **Verify in logcat:**
   - Compressed file is deleted after stripping
   - Stripped file is deleted after encryption
   - Encrypted file is deleted after upload
   - Three separate `UploadOrchestrator: Deleted temp file` log lines appear
5. **Verify in cache dir:** No `stripped_*` or `EncryptFiles*` files remain

### Test 8: Upload cancellation

1. Open a new note compose screen
2. Attach a large image or video
3. Cancel the upload while compression or upload is in progress
4. **Verify in cache dir:** No temp files remain from the cancelled upload

### Test 9: Video sharing to other apps

1. Open a note with a video
2. Long-press or use the share button to share the video to another app
3. **Verify:** The receiving app successfully receives the video
4. **Verify:** After ~1 minute, the temp file in the share directory is cleaned up
5. **This test confirms the 1-minute delay was not broken by our changes**

### Test 10: Image compression failure fallback

1. Attach a GIF or SVG file (compression is skipped for these)
2. Post the note
3. **Verify:** Upload succeeds using the original file
4. **Verify in cache dir:** No orphaned temp files