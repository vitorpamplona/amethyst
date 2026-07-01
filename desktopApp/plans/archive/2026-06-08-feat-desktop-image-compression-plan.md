---
title: "feat: Desktop Image Compression"
type: feat
status: active
date: 2026-06-08
origin: docs/brainstorms/2026-06-08-desktop-image-compression-brainstorm.md
deepened: 2026-06-08
---

# Desktop Image Compression

> **Status:** shipped — `ImageReencoder` + sniffer + `CompressionException` (commons), `ImageCompressionStore` + `CompressionPreviewDialog` + settings (desktop), wired into `ComposeNoteDialog` and the `amy` CLI.
> _Audited 2026-06-30._

## Enhancement Summary

**Deepened on:** 2026-06-08
**Review agents used:** architecture-strategist, performance-oracle,
security-sentinel, code-simplicity-reviewer, pattern-recognition-specialist,
best-practices-researcher, amy-expert, kotlin-multiplatform,
kotlin-coroutines-structured-concurrency, data-integrity-guardian,
compose-expert.

### Showstoppers found by review (forced scope cuts)

1. **No pure-Java WebP encoder exists in 2026.** `org.sejda.imageio:webp-imageio` is JNI-based and abandoned (last release 2017); TwelveMonkeys' WebP plugin is decode-only (wiki confirms). Conclusion: **WebP encoding dropped from v1**. JPEG-only output. WebP encode revisited if/when a usable pure-Java encoder appears or JNI is accepted.
2. **TwelveMonkeys has NO HEIC plugin.** Issue [#976](https://github.com/haraldk/TwelveMonkeys/issues/976) (2024) remains open. The earlier exploration mis-named `imageio-heif`; that plugin does not exist. Pure-Java HEIC decode is not viable. Conclusion: **HEIC input dropped from v1**. Input formats limited to JPEG / PNG / GIF / WebP / SVG / BMP / TIFF (the latter two via TwelveMonkeys' actual real plugins, low priority). HEIC support deferred to a JNI-libheif follow-up.

### Key correctness fixes from review

1. **Pre-decode pixel-count guard was broken as written.** `Thumbnails.of(file)` decodes internally — the 50 MP guard cannot fire in time. Fix: stream header dims via `ImageReader.getWidth(0)/getHeight(0)`, then call `Thumbnails.of(bufferedImage)` after a guarded `reader.read(0, paramWithSubsampling)`. (perf + sec)
2. **EXIF leak on fail-loud bypass.** "Send Original" must still run `stripExif` when source is JPEG and `keepExif=false`. Plan now resolves the brainstorm's Open Question #3 as YES. (data-integrity + sec)
3. **`stripExif` is JPEG-only.** For non-JPEG fall-back-to-original paths, EXIF cannot be stripped — surface this honestly in the failure dialog ("Send original — metadata may be present" vs JPEG's "Send original — EXIF stripped"). (sec)
4. **ICC profile loss on iPhone Display P3 photos.** Stock ImageIO JPEG writer drops ICC profile → visible ~5–8 ΔE desaturation on greens/blues. Phase 1 acceptance now requires ICC preservation. (perf)
5. **Settings storage pattern mismatch.** Plan said "mirror customFeeds StateFlow auto-save in setter" but that pattern lives in `SearchHistoryStore.kt` and `FeedDefinitionRepository`, NOT in `DesktopPreferences` (which is plain getter/setter). Introduce a dedicated `ImageCompressionStore` class. (patterns)
6. **`CompositionLocal` for cross-cutting settings.** 5+ read sites justify `LocalCompressionSettings = staticCompositionLocalOf<ImageCompressionStore>()` provided once at `App()` level. (patterns)
7. **Compose component choices.** Replace `DropdownMenu` for quality with `SingleChoiceSegmentedButtonRow` (codebase convention: see `TorSettingsSection.kt:95`). Replace `AlertDialog` for failure dialog with `Dialog { Surface }` (AlertDialog state-isolation trap from `custom-feeds-alertdialog.md`). Use `FilterChip` for the per-post override pill (matches `AccountSwitcherDropdown.kt:111`). Use `.collectAsState()` (NOT `collectAsStateWithLifecycle`, which is Android-only) and never read `.value` inside composition. (compose-expert)
8. **Coroutines / cancellation.** `Thumbnails.of` is blocking and not cancellable mid-decode. Wrap with `ensureActive()` between stages, run on `Dispatchers.Default.limitedParallelism(1)` (CPU-bound + serial), and put cleanup behind `NonCancellable` on exception paths. Wait on user choice via `CompletableDeferred<UserChoice>`. (coroutines)
9. **AWT headless mode.** `-Djava.awt.headless=true` must be set on CLI + tests before any ImageIO touch — defensive against macOS Dock-bounce / GUI-thread side effects from future transitive plugins. (amy)
10. **Temp dir on Linux tmpfs.** `java.io.tmpdir` defaults to `/tmp`, a tmpfs sized at ~50 % of RAM. Big decoded buffers there are an OOM-by-tmpfs risk on small VMs. Use `~/.amethyst/tmp/` (created mode 0700) — SSD-backed, single-user, no symlink race. Add boot-time orphan sweep (>24 h). (perf + sec + data-integrity)
11. **Phase serialization.** P4/P5/P6/P7/P8 all edit `ComposeNoteDialog`. Original "parallel" claim was unrealistic. New estimate: ~7 working days serialized. (architecture)
12. **Architecture: split responsibilities.** `MediaCompressor` retains its single EXIF-strip op; new `ImageReencoder` object handles re-encode + downscale + sniff. Returns `sealed ReencodeResult` (`Reencoded(File)` | `PassThrough(reason)` | `Refused(CompressionException)`) so the orchestrator collapses to one `when`. (architecture)
13. **Modern JPEG quality values.** 2014 Android values (q=40/50/80/90) are perceptually obsolete on 2026 displays. New presets use 0.65 / 0.75 / 0.90. (research)
14. **JFR `ImageCompressionEvent`** for memory/perf profiling on JDK 21 — ~10 lines, queryable via `jfr view`. (research)
15. **Supply chain pinning.** Add `verification-metadata.xml` for the two new deps; OWASP dep-check in CI; monitor TwelveMonkeys releases. (sec)

### Scope cuts from review (simplicity)

- **WebP output** dropped (no library exists)
- **HEIC input** dropped (no library exists)
- **5 quality presets → 3** (LOW, MEDIUM, DESKTOP_HIGH). The Android "Uncompressed (640px @ q=90)" quirk goes away — confusing on desktop. "High (640@80)" gets folded into "Medium" with modernized quality numbers. The brainstorm's preset choice was based on the assumption Android parity = value; review challenged this and we're cutting.
- **TIFF/BMP input** dropped (vanishingly rare on Nostr; fail-loud "format not supported" if encountered)
- **Before/After compare dialog (Phase 7)** dropped from v1 (YAGNI per simplicity; Compose state-isolation trap per memory; users have lived without on mobile for years). Moved to Future Considerations.
- **`OutputCodec` enum + setting** dropped (only JPEG remains)
- **Dedicated `Compressing` tracker state** simplified to inline text on existing `Uploading` state

### Carried-forward decisions that survived review

- Pipeline lives in `commons/src/jvmMain/` (KMP placement validated; AWT-loaded by existing code; CLI compatible after headless fix)
- Per-post override scope = whole post (no per-image override)
- Strip-EXIF by default + toggle (always-apply on bypass path too)
- Fail-loud + confirm-to-bypass on compression failure (but as `Dialog { Surface }`, not `AlertDialog`)
- Clipboard paste integrated; no PNG round-trip; filename `paste-YYYYMMDD-HHMMSS.jpg`
- Animated GIF / WebP / SVG pass through byte-identical (sniffer + `ReencodeResult.PassThrough`)
- AVIF input refused with "format not supported" (sniffer detects ftyp box)
- 50 MP `InputTooLarge` guard kept (cheap + critical against memory bombs)
- Sealed `CompressionException` hierarchy kept (`initCause` fix for chain preservation)

---

## Overview

Desktop currently uploads raw image bytes — a 4K screenshot or large
camera JPEG ships at full size to Blossom servers and across every relay.
This plan adds a JPEG-only re-encode/downscale pipeline shared between
desktop and the Amy CLI, with 3 quality presets (Low / Medium / Desktop
High), per-post override, EXIF-strip-by-default, clipboard-paste
integration, and a fail-loud failure dialog. WebP encoding and HEIC
input are out of scope for v1 (no pure-Java implementation exists in
2026). Android keeps its existing Zelory-based pipeline unchanged.

(see brainstorm: [docs/brainstorms/2026-06-08-desktop-image-compression-brainstorm.md](../brainstorms/2026-06-08-desktop-image-compression-brainstorm.md))

## Problem Statement

Today's desktop upload flow has no compression:

```kotlin
// desktopApp/.../ComposeNoteDialog.kt:430
orchestrator.upload(file, alt, serverBaseUrl, signer, stripExif = true)

// commons/.../UploadOrchestrator.kt:45-86
suspend fun upload(file, alt, serverBaseUrl, signer, stripExif = true): UploadResult {
    val processedFile = if (stripExif) MediaCompressor.stripExif(file) else file
    val metadata = MediaMetadataReader.compute(processedFile)
    // ... auth, upload, delete
}
```

`MediaCompressor.stripExif()` is JPEG-only and uses `deleteOnExit()`
which leaks temp files in long-running desktop sessions
(`docs/temp-file-cleanup-analysis.md:25-31`).

User symptoms:
1. Large JPEGs stall on slow uplinks.
2. Pasted screenshots upload as huge PNGs.
3. EXIF (including GPS) ships untouched on any non-JPEG.
4. Multi-image posts give no progress signal during compression.

## Proposed Solution

Introduce a JPEG-only re-encode pipeline in `commons/src/jvmMain/` built
on **Thumbnailator 0.4.21**, called from `UploadOrchestrator.upload()`
ahead of EXIF strip. Settings live in a new `ImageCompressionStore`
(StateFlow-backed wrapper over `DesktopPreferences`), surfaced through a
`LocalCompressionSettings` CompositionLocal, edited in a new "Media"
section of Desktop Settings. The compose dialog reads the default and
lets the user override per-post via a `FilterChip` + `DropdownMenu`.
Clipboard paste flows through the same pipeline (no PNG temp
round-trip). Failures surface in a `Dialog { Surface }` with per-file
"Send Original" or "Cancel" controls — and "Send Original" still strips
EXIF when source is JPEG.

### Why this approach

- **Pipeline in `commons/jvmMain`** — Amy CLI can reuse via the existing `UploadOrchestrator` it already calls from `DmCommands.kt:163`. Android keeps Zelory (separately-reviewable migration later).
- **JPEG-only v1** — forced by the lack of a pure-Java WebP encoder, but also simpler and universally compatible.
- **3 presets with modernized quality values** — Low (640 @ q=0.65), Medium (640 @ q=0.75), Desktop High (1920 @ q=0.90). 2014 Android values would produce visibly bad results on 2026 displays.
- **Thumbnailator** — pure-Java, MIT, progressive bilinear scaling, mature; widely used in JVM CMSes (Confluence, JIRA).
- **Strip EXIF by default** — privacy-first; always applies including the fail-loud "Send Original" bypass for JPEGs.
- **Fail loudly, not silently** — Android silently passes original on error; desktop surfaces a dialog so the user knowingly chooses.
- **`ImageReencoder` separate from `MediaCompressor`** — SRP. `MediaCompressor` stays a one-method utility (EXIF strip); `ImageReencoder` handles re-encode + downscale + sniff + format policy.

## Current State Audit

### What already exists (reuse, don't duplicate)

| Component | Location | Status |
|---|---|---|
| `MediaCompressor.stripExif()` | `commons/src/jvmMain/.../service/upload/MediaCompressor.kt:28-50` | Unchanged. Fix `deleteOnExit()` leak. |
| `UploadOrchestrator.upload()` | `commons/.../service/upload/UploadOrchestrator.kt:45-86` | Modify — add `quality` param, slot `ImageReencoder` ahead of EXIF strip, eager temp cleanup |
| `MediaMetadataReader.compute()` | `commons/.../service/upload/MediaMetadata.kt` | Use as-is |
| `ClipboardPasteHandler` | `desktopApp/.../ui/media/ClipboardPasteHandler.kt:43` | Modify — return `BufferedImage`, no PNG round-trip, no `deleteOnExit()` |
| `MediaAttachmentRow` | `desktopApp/.../ui/media/MediaAttachmentRow.kt` | Unchanged (quality chip lives next to `ServerSelector`, not here) |
| `MediaServerSettings` | `desktopApp/.../ui/settings/MediaServerSettings.kt:70-98` | Template shape only — `ImageCompressionSettings` follows it |
| `SearchHistoryStore` | `desktopApp/.../SearchHistoryStore.kt` | **Canonical pattern** — `ImageCompressionStore` mirrors this |
| `DesktopUploadTracker` | `desktopApp/.../service/upload/DesktopUploadTracker.kt:27-53` | Reuse — append "Processing N/M" to existing `Uploading` text rather than new state |
| `DesktopPreferences` | `desktopApp/.../DesktopPreferences.kt` | Add 2 plain keys (read/written by `ImageCompressionStore`) |
| `ComposeNoteDialog:326-350` | `desktopApp/.../ui/ComposeNoteDialog.kt` | Add `QualitySelectorChip` next to `ServerSelector` in the existing options row |
| `LocalDesktopCache`, `LocalRelayManager` etc. | `desktopApp/.../Main.kt` (App scope) | Pattern reference — `LocalCompressionSettings` joins this set |
| Material3 `AlertDialog` | `ForceLogoutDialog`, `TorSettingsDialog` | Used for trivial confirms only |
| Material3 `Dialog { Surface }` | `FeedBuilderDialog.kt` | Used for richer content (failure dialog follows this) |
| Material3 `SingleChoiceSegmentedButtonRow` | `TorSettingsSection.kt:95`, `FeedBuilderDialog.kt:191` | Used for ordered enum settings (quality preset uses this) |
| `FilterChip` + anchored `DropdownMenu` | `AccountSwitcherDropdown.kt:111`, `DeckSidebar.kt:402` | Pattern for per-post override pill |
| `MaterialSymbols` (icons) | `commons/commonMain/.../icons/symbols/MaterialSymbols.kt` | New codepoints require font regen via `./tools/material-symbols-subset/subset.sh` |

### Known gotchas the plan must fix

1. **`deleteOnExit()` leaks** in `MediaCompressor.kt:44` and `ClipboardPasteHandler.kt:43`. Eager delete in `try/finally` (with `NonCancellable` on cancel path).
2. **AlertDialog state isolation** — `LaunchedEffect` and `produceState` don't fire inside `AlertDialog.text`. All effects hoisted to parent; dialogs receive plain values.
3. **`Thumbnails.of(file)` decodes internally** — bypasses our pixel-count guard. We do the read ourselves with `ImageReader.getWidth(0)` first.
4. **CLI AWT init** — `-Djava.awt.headless=true` set on CLI `application{}` block, CLI `Main.kt` top, and `:commons:jvmTest` JVM args.
5. **ICC profile preservation** — read ICC via `IIOMetadata`, embed in output via APP2 marker.
6. **macOS Display P3** — see #5. Without ICC preservation, P3 photos visibly desaturate.
7. **Multi-image batch on Linux** — temp dir = `~/.amethyst/tmp/`, not `/tmp` (tmpfs).
8. **Sniffer for animated WebP** must check `VP8X` flag byte (bit 1) in addition to `ANIM` chunk.

## Technical Approach

### Architecture

```
ComposeNoteDialog (existing)
  ├─ activeQuality = LocalCompressionSettings.current.quality.collectAsState()
  ├─ perPostOverride: CompressionQuality?  (resets on dispose AND on send)
  └─ Send (rememberCoroutineScope, dialog-scoped):
       for each (idx, file) in attachedFiles:
         tracker.update(Uploading(file = "...", subtext = "Processing $idx/$total"))
         try {
           result = ImageReencoder.reencode(file, quality, keepExif)   // suspend
         } catch (CompressionException) {
           failures += FailedAttachment(file, exception, file.length())
           continue
         }
         when (result) {
           is ReencodeResult.Reencoded -> orchestrator.upload(result.file, alt, ...)
           is ReencodeResult.PassThrough -> orchestrator.upload(file, alt, ...)
           is ReencodeResult.Refused -> impossible (thrown above)
         }
       if (failures.isNotEmpty()) {
         val choice = CompletableDeferred<UserChoice>()
         pendingFailureDialog.value = FailureDialogState(failures, choice)
         when (choice.await()) {
           SendOriginal -> for (f in failures) orchestrator.upload(maybeStripExif(f), ...)
           Cancel       -> return
         }
       }
       perPostOverride = null
```

```
ImageReencoder (commons/.../service/upload/ImageReencoder.kt) [new]
  sealed class ReencodeResult {
    data class Reencoded(val tempFile: File) : ReencodeResult()
    data class PassThrough(val reason: PassReason) : ReencodeResult()
    // PassReason: Animated, Svg, BelowThreshold, UncompressedPreset
  }
  // Refused throws CompressionException instead of returning Refused.
  // Keeps the "happy path" branch-free in the orchestrator.

  suspend fun reencode(
    source: File,
    quality: CompressionQuality,
  ): ReencodeResult = withContext(compressionDispatcher) {
    ensureActive()
    val format = ImageFormatSniffer.sniff(source)
    when (format) {
      is GifAnimated, AnimatedWebP, Svg -> return@withContext PassThrough(Animated|Svg)
      is Avif -> throw CompressionException.UnsupportedFormat("avif")
      else -> { /* re-encode */ }
    }
    val (w, h) = readHeaderDims(source, format) ?: throw EncodeFailed(...)
    if (w.toLong() * h > MAX_INPUT_PIXELS) throw InputTooLarge(w.toLong() * h)
    if (w <= quality.maxDim && h <= quality.maxDim &&
        format is Jpeg && quality == LOW) {
      // tiny shortcut: source already smaller than target — re-encode for q
    }
    ensureActive()
    val img = decodeWithSubsampling(source, w, h, quality.maxDim)
    ensureActive()
    val temp = createTempFile()
    try {
      Thumbnails.of(img)
        .size(quality.maxDim, quality.maxDim)
        .outputFormat("jpg")
        .outputQuality(quality.jpegQuality)
        .useExifOrientation(true)
        .imageType(BufferedImage.TYPE_INT_RGB)
        // ICC: copy from input metadata; embed in output APP2 marker
        .toFile(temp)
      ReencodeResult.Reencoded(temp)
    } catch (t: Throwable) {
      withContext(NonCancellable) { temp.delete() }
      throw if (t is CompressionException) t else EncodeFailed(t)
    }
  }

  private val compressionDispatcher = Dispatchers.Default.limitedParallelism(1)
```

```
UploadOrchestrator.upload() (modified)

  suspend fun upload(
    file: File, alt: String?, serverBaseUrl: String, signer: NostrSigner,
    stripExif: Boolean = true,
    quality: CompressionQuality = CompressionQuality.DEFAULT,
  ): UploadResult {
    val reencodeResult = ImageReencoder.reencode(file, quality)
    val finalFile = when (reencodeResult) {
      is Reencoded -> reencodeResult.tempFile.also { temp = it }
      is PassThrough -> {
        if (stripExif && file.name.endsWith(".jpg|.jpeg")) {
          MediaCompressor.stripExif(file).also { temp = it.takeIf { it != file } }
        } else {
          file
        }
      }
    }
    try {
      val metadata = MediaMetadataReader.compute(finalFile)
      val auth = BlossomAuth.createUploadAuth(metadata.sha256, metadata.size, alt ?: ..., signer)
      val result = client.upload(finalFile, metadata.mimeType, serverBaseUrl, auth)
      return UploadResult(result, metadata)
    } finally {
      withContext(NonCancellable) { temp?.delete() }
    }
  }
```

### Data model

```kotlin
// commons/src/jvmMain/.../service/upload/CompressionQuality.kt
enum class CompressionQuality(
    val displayName: String,
    val maxDim: Int,
    val jpegQuality: Float,
) {
    LOW("Low",                   640,  0.65f),
    MEDIUM("Medium",             640,  0.75f),
    DESKTOP_HIGH("Desktop High", 1920, 0.90f),
    ;
    companion object { val DEFAULT = DESKTOP_HIGH }
}

// commons/src/jvmMain/.../service/upload/ImageFormat.kt
sealed class ImageFormat {
    object Jpeg : ImageFormat()
    object Png  : ImageFormat()
    object Bmp  : ImageFormat()
    object Tiff : ImageFormat()
    data class Gif(val animated: Boolean) : ImageFormat()
    data class WebP(val animated: Boolean) : ImageFormat()
    object Svg  : ImageFormat()
    object Avif : ImageFormat()                  // refused
    data class Unknown(val mime: String) : ImageFormat()
}

// commons/src/jvmMain/.../service/upload/CompressionException.kt
sealed class CompressionException(message: String, cause: Throwable? = null)
    : Exception(message, cause) {
    init { if (cause != null) initCause(cause) }   // chain preservation
    class UnsupportedFormat(format: String) :
        CompressionException("Format not supported: $format")
    class InputTooLarge(pixels: Long) :
        CompressionException("$pixels pixels exceeds 50 MP limit")
    class EncodeFailed(cause: Throwable) :
        CompressionException("Image encode failed: ${cause.message}", cause)
}
```

```kotlin
// desktopApp/src/jvmMain/.../service/ImageCompressionStore.kt
class ImageCompressionStore(private val prefs: DesktopPreferences) {
    private val _quality = MutableStateFlow(prefs.imageQualityRaw)
    val quality: StateFlow<CompressionQuality> = _quality.asStateFlow()
    fun setQuality(q: CompressionQuality) {
        _quality.value = q
        prefs.imageQualityRaw = q
    }
    private val _stripExif = MutableStateFlow(prefs.stripExifOnUpload)
    val stripExif: StateFlow<Boolean> = _stripExif.asStateFlow()
    fun setStripExif(v: Boolean) {
        _stripExif.value = v
        prefs.stripExifOnUpload = v
    }
}

// desktopApp/src/jvmMain/.../Main.kt — alongside existing CompositionLocals
val LocalCompressionSettings = staticCompositionLocalOf<ImageCompressionStore> {
    error("LocalCompressionSettings not provided")
}
```

### Concurrency model

- **Dispatcher**: `private val compressionDispatcher = Dispatchers.Default.limitedParallelism(1)` — CPU-bound; serial enforced regardless of caller.
- **Cancellation**: `ensureActive()` at format-sniff, post-header, post-decode boundaries. Pure ImageIO `read(...)` and Thumbnailator are blocking and non-interruptible mid-frame; we accept up to ~1 s of "ghost work" after cancel (mitigated by per-stage `ensureActive()` and `MAX_INPUT_PIXELS` cap).
- **Cleanup on cancel**: `try { ... } catch (t) { withContext(NonCancellable) { temp.delete() }; throw t }`. Plain `File.delete()` is non-suspending so a plain `finally` would also work; `NonCancellable` is belt-and-braces.
- **Failure dialog await**: `CompletableDeferred<UserChoice>` in dialog-scoped state, completed by Confirm/Cancel button callbacks. Cancellation-aware. Pattern from `compose-side-effects` skill.
- **Lifetime**: dialog-scoped. Closing `ComposeNoteDialog` cancels compress + upload. Background-completion (the March 2026 plan's "app-scoped upload") is out of scope for v1.

### Pre-decode guard (memory-bomb defense, perf optimization)

```kotlin
private fun decodeWithSubsampling(
    file: File, w: Int, h: Int, targetMaxDim: Int,
): BufferedImage = ImageIO.createImageInputStream(file).use { iis ->
    val reader = ImageIO.getImageReaders(iis).next().apply { input = iis }
    val pixels = w.toLong() * h
    if (pixels > MAX_INPUT_PIXELS) throw InputTooLarge(pixels)
    val downscaleRatio = ceil(
        sqrt(pixels.toDouble() / (targetMaxDim.toDouble().pow(2)))
    ).toInt().coerceAtLeast(1)
    val param = reader.defaultReadParam.apply {
        setSourceSubsampling(downscaleRatio, downscaleRatio, 0, 0)
    }
    try { reader.read(0, param) } finally { reader.dispose() }
}
```

This is the critical bit. Subsampling at decode time means a 50 MP source
decodes at ~2 MP for the 1920px target — 25× less heap, 5–10× faster.

### Color management (ICC preservation)

```kotlin
// pseudocode — concretize in Phase 1
val srcIcc: ICC_Profile? = readIccFromMetadata(file)
val out = Thumbnails.of(img).size(...).outputQuality(q).asBufferedImage()
val writer = ImageIO.getImageWritersByMIMEType("image/jpeg").next()
val meta = writer.getDefaultImageMetadata(...)
srcIcc?.let { embedAsApp2(meta, it) }   // ICC marker is multi-segment APP2
writer.write(meta, IIOImage(out, null, null), null)
```

Verify on a Display P3 fixture (iPhone photo) that re-encoded output
shows the same color profile in `exiftool -ICC_Profile:all`. Without
this, green grass / blue sky shift visibly.

### Threat model (new section per security review)

This feature only processes images the user **explicitly attached**
(file picker, drag-drop, clipboard paste). It MUST NOT be reused for
parsing inbound network bytes (e.g., previewing relay-fetched images)
without re-review — TwelveMonkeys pure-Java parsers haven't been
publicly fuzzed at scale. If a future feature wants to use this
pipeline on inbound bytes, add an AFL/Jazzer fuzz pass first.

### Dependency Changes

```toml
# gradle/libs.versions.toml — additions
[versions]
thumbnailator = "0.4.21"

[libraries]
thumbnailator = { module = "net.coobird:thumbnailator", version.ref = "thumbnailator" }
```

```kotlin
// commons/build.gradle.kts — jvmMain sourceSet
implementation(libs.thumbnailator)
```

```xml
// gradle/verification-metadata.xml — pin SHA-256 of thumbnailator-0.4.21.jar
```

```kotlin
// cli/build.gradle.kts — defensive headless
application {
    applicationDefaultJvmArgs = (applicationDefaultJvmArgs ?: emptyList()) +
        listOf("-Djava.awt.headless=true")
}

// commons/build.gradle.kts — jvmTest task config
tasks.named<Test>("jvmTest") {
    jvmArgs("-Djava.awt.headless=true")
}
```

**One** new dependency. TwelveMonkeys, sejda webp-imageio, imageio-heif
all dropped per review findings. JNI count: 0.

### File placement summary

| File | Status | Location |
|---|---|---|
| `MediaCompressor.kt` | Modify (fix `deleteOnExit`) | `commons/src/jvmMain/.../service/upload/` |
| `UploadOrchestrator.kt` | Modify (add `quality` param + reencode call + eager cleanup) | `commons/src/jvmMain/.../service/upload/` |
| `CompressionQuality.kt` | **New** | same package |
| `ImageFormat.kt` + `ImageFormatSniffer.kt` | **New** | same package |
| `CompressionException.kt` | **New** | same package |
| `ImageReencoder.kt` | **New** | same package |
| `ImageReencoderTest.kt` + `ImageFormatSnifferTest.kt` + `UploadOrchestratorTest.kt` | **New** | `commons/src/jvmTest/...` |
| `DesktopPreferences.kt` | Modify (add 2 simple keys) | `desktopApp/.../` |
| `ImageCompressionStore.kt` | **New** | `desktopApp/.../service/` |
| `LocalCompressionSettings` declaration in `Main.kt` | Add CompositionLocal | `desktopApp/.../` |
| `ImageCompressionSettings.kt` (composable) | **New** | `desktopApp/.../ui/settings/` |
| `RelaySettingsScreen` integration in `Main.kt:1648` | Modify | `desktopApp/.../` |
| `QualitySelectorChip.kt` (composable) | **New** | `desktopApp/.../ui/media/` |
| `ComposeNoteDialog.kt:326-350` | Modify (chip placement + per-post override + send-loop + failure flow) | `desktopApp/.../ui/` |
| `CompressionFailureDialog.kt` | **New** (`Dialog { Surface }`, not `AlertDialog`) | `desktopApp/.../ui/media/` |
| `ClipboardPasteHandler.kt` | Modify (return `BufferedImage`, no PNG temp, no `deleteOnExit`) | `desktopApp/.../ui/media/` |
| `cli/build.gradle.kts` | Modify (headless arg) | `cli/` |
| `cli/Main.kt` | Modify (headless set before any class load) | `cli/` |
| `commons/build.gradle.kts` jvmTest | Modify (headless arg) | `commons/` |
| `tools/material-symbols-subset/...` | Run regen if any new codepoint introduced | — |

## Implementation Phases

Total estimate: **7 working days serialized**. P3/P4/P5/P6/P7 all edit `ComposeNoteDialog` — parallelization not realistic.

### Phase 0 — Dependency + headless + smoke test (0.5 d)

| # | Task | File |
|---|---|---|
| 0.1 | Add Thumbnailator 0.4.21 to libs.versions.toml + verification metadata + commons jvmMain | `gradle/libs.versions.toml`, `commons/build.gradle.kts`, `gradle/verification-metadata.xml` |
| 0.2 | Add `-Djava.awt.headless=true` to CLI `application{}` block + `cli/Main.kt` first line + `commons:jvmTest` jvmArgs | `cli/build.gradle.kts`, `cli/Main.kt`, `commons/build.gradle.kts` |
| 0.3 | Smoke test: encode a 4032×3024 JPEG through Thumbnailator @ q=0.9, assert dims/bytes | `commons/src/jvmTest/.../smoke/CompressionSmokeTest.kt` |
| 0.4 | Smoke test: decode + re-encode a Display P3 sample JPEG, assert ICC profile present in output | same |
| 0.5 | Run `:commons:compileKotlinJvm` and `:cli:assemble` and `:desktopApp:compileKotlin` — confirm everything compiles | n/a |

**Gate:** if ICC preservation can't work in JVM 21 stock ImageIO writer, document the gap and accept color shift on P3 sources (downgraded acceptance criterion).

### Phase 1 — Core re-encode pipeline (2 d)

| # | Task | File |
|---|---|---|
| 1.1 | `CompressionQuality` enum (3 presets + `DEFAULT = DESKTOP_HIGH`) | `commons/.../upload/CompressionQuality.kt` |
| 1.2 | `ImageFormat` sealed class + `ImageFormatSniffer` (magic bytes for JPEG/PNG/GIF/WebP/SVG/BMP/TIFF/AVIF; GIF NETSCAPE2.0 + WebP VP8X bit-1 for animation) | `commons/.../upload/ImageFormat.kt`, `ImageFormatSniffer.kt` |
| 1.3 | `CompressionException` sealed hierarchy with `initCause` chain | `commons/.../upload/CompressionException.kt` |
| 1.4 | `ImageReencoder` object: `reencode(File, CompressionQuality): ReencodeResult` | `commons/.../upload/ImageReencoder.kt` |
| 1.5 | Pre-decode pixel guard via `ImageReader.getWidth(0)/getHeight(0)` + `setSourceSubsampling` | same |
| 1.6 | ICC profile preservation: read source ICC via `IIOMetadata`, embed in output APP2 marker | same |
| 1.7 | `compressionDispatcher = Dispatchers.Default.limitedParallelism(1)` + `ensureActive()` between stages | same |
| 1.8 | `MAX_INPUT_PIXELS = 50_000_000` constant (tunable via system property) | same |
| 1.9 | Use `~/.amethyst/tmp/` (created mode 0700) for temp files. Helper `amethystTempDir(): Path` resolving once at process start | `commons/.../upload/AmethystTempDir.kt` (new) |
| 1.10 | Boot-time orphan sweep: on first call, delete `amethyst_compress_*` and `amethyst_paste_*` older than 24 h | same |
| 1.11 | Modify `UploadOrchestrator.upload(quality = DEFAULT)` — call `ImageReencoder.reencode`, branch on `ReencodeResult`, eager `try/finally` cleanup (no `deleteOnExit`) | `commons/.../upload/UploadOrchestrator.kt` |
| 1.12 | Remove `deleteOnExit()` from `MediaCompressor.stripExif()` — caller (`UploadOrchestrator`) owns cleanup | `commons/.../upload/MediaCompressor.kt` |
| 1.13 | Unit tests: 3 presets × monotonic file size + correct max-dim | `ImageReencoderTest.kt` |
| 1.14 | Unit tests: never-upscale (320×240 → DESKTOP_HIGH dims unchanged) | same |
| 1.15 | Unit tests: 51 MP synthetic input → `InputTooLarge` | same |
| 1.16 | Unit tests: animated GIF → `ReencodeResult.PassThrough(Animated)`; orchestrator uploads original bytes | `UploadOrchestratorTest.kt` |
| 1.17 | Unit tests: animated WebP via VP8X bit-1 → PassThrough | same |
| 1.18 | Unit tests: SVG → PassThrough | same |
| 1.19 | Unit tests: AVIF (ftyp box) → `UnsupportedFormat` | same |
| 1.20 | Unit tests: `MediaMetadataReader.compute()` runs on the actually-uploaded file in all 3 orchestrator branches; `metadata.hash == sha256(uploadedBody)` | `UploadOrchestratorTest.kt` |
| 1.21 | Unit tests: ICC profile present in output for Display P3 input | `ImageReencoderTest.kt` |
| 1.22 | Unit tests: no `/tmp/amethyst_*` files after upload completion | `UploadOrchestratorTest.kt` |

**Deliverable:** all compression logic shipped & tested in commons. No desktop UI changes yet.

### Phase 2 — Settings storage + CompositionLocal (0.5 d)

| # | Task | File |
|---|---|---|
| 2.1 | Add 2 plain keys to `DesktopPreferences`: `KEY_IMAGE_QUALITY` (default `"DESKTOP_HIGH"`), `KEY_IMAGE_STRIP_EXIF` (default `true`). Plain getters/setters; enum round-trips via `name`. | `desktopApp/.../DesktopPreferences.kt` |
| 2.2 | New `ImageCompressionStore(prefs)` class mirroring `SearchHistoryStore.kt` — wraps prefs in MutableStateFlow, exposes read-only StateFlow, setter writes both | `desktopApp/.../service/ImageCompressionStore.kt` |
| 2.3 | New `val LocalCompressionSettings = staticCompositionLocalOf<ImageCompressionStore>()` | `desktopApp/.../Main.kt` |
| 2.4 | Provide `LocalCompressionSettings` at `App()` level alongside existing CompositionLocals; instantiate once | same |
| 2.5 | Unit test: round-trip each setting through a `Preferences.userNodeForTesting()`-equivalent mock | `ImageCompressionStoreTest.kt` |

### Phase 3 — Settings UI (0.75 d)

| # | Task | File |
|---|---|---|
| 3.1 | New `ImageCompressionSettings()` composable: titled section with quality `SingleChoiceSegmentedButtonRow` (Low / Medium / Desktop High) + EXIF `Switch`. Reads from `LocalCompressionSettings.current.quality.collectAsState()` and `.stripExif.collectAsState()`. | `desktopApp/.../ui/settings/ImageCompressionSettings.kt` |
| 3.2 | Help text under each control. EXIF: "Removes camera, GPS, timestamp before upload. Turn off only if you want photo metadata preserved." | same |
| 3.3 | Note explaining default applies unless overridden per-post in compose | same |
| 3.4 | Integrate into `RelaySettingsScreen` / Settings flow (Main.kt:1648 region) | `desktopApp/.../Main.kt` |
| 3.5 | Manual UI smoke: launch desktop, open settings, change each control, restart app, confirm persisted | n/a |

### Phase 4 — ComposeNoteDialog wiring + per-post chip (1.25 d)

| # | Task | File |
|---|---|---|
| 4.1 | New `QualitySelectorChip` composable: `FilterChip(selected = override != null, label = "Quality: $activeQuality")` + anchored `DropdownMenu` with 3 items + "Reset to default" row | `desktopApp/.../ui/media/QualitySelectorChip.kt` |
| 4.2 | In `ComposeNoteDialog`, hold `var perPostOverride by remember { mutableStateOf<CompressionQuality?>(null) }` | `desktopApp/.../ui/ComposeNoteDialog.kt` |
| 4.3 | Place `QualitySelectorChip` in the existing options row (line 326-350) alongside `ServerSelector` (NOT inside `MediaAttachmentRow`) | same |
| 4.4 | Pass `activeQuality` to each `orchestrator.upload(...)` call (line 430 region) | same |
| 4.5 | Reset `perPostOverride = null` after successful send | same |
| 4.6 | `DisposableEffect(Unit) { onDispose { perPostOverride = null } }` for paranoid reset on dialog dismissal (catches the case where dialog state is hoisted in parent rather than re-instantiated) | same |
| 4.7 | `LaunchedEffect` on `attachedFiles.size` — sync the chip's `activeQuality` value display | same |
| 4.8 | Manual test: attach JPEG, choose Low, send → file shrinks; open compose again → control reads Desktop High | n/a |

### Phase 5 — Batch progress (inline) (0.25 d)

| # | Task | File |
|---|---|---|
| 5.1 | In `ComposeNoteDialog` send loop, before each `reencode` + `upload`, update tracker text to `"Processing $idx/$total: $filename"`. Reuse existing `Uploading` state — do NOT introduce a new state class | `desktopApp/.../ui/ComposeNoteDialog.kt`, `DesktopUploadTracker.kt` |
| 5.2 | Manual test: 3-image post shows progress 1/3 → 2/3 → 3/3 | n/a |

### Phase 6 — Clipboard paste integration (0.5 d)

| # | Task | File |
|---|---|---|
| 6.1 | Refactor `ClipboardPasteHandler.getClipboardFiles()` to return `ClipboardImage(image: BufferedImage, suggestedName: String)` for image flavor, in addition to existing file flavor | `desktopApp/.../ui/media/ClipboardPasteHandler.kt` |
| 6.2 | Remove `deleteOnExit()` and the PNG temp roundtrip — paste returns the BufferedImage directly | same |
| 6.3 | Add `ImageReencoder.reencode(BufferedImage, filenameHint, quality): ReencodeResult` overload | `commons/.../upload/ImageReencoder.kt` |
| 6.4 | In `ComposeNoteDialog` paste handler, route `ClipboardImage` through the BufferedImage overload | `ComposeNoteDialog.kt` |
| 6.5 | Suggested filename: `paste-${ISO8601_BASIC}.jpg` | `ClipboardPasteHandler.kt` |
| 6.6 | Manual test: paste screenshot, send, verify resulting upload's mime is `image/jpeg` and dims look right | n/a |

### Phase 7 — Fail-loud confirm dialog (0.75 d)

| # | Task | File |
|---|---|---|
| 7.1 | New `CompressionFailureDialog` using `Dialog { Surface { Column { Text + LazyColumn(failures) + Row(buttons) } } }` (NOT `AlertDialog` — content needs scroll + lifecycle for future per-file actions) | `desktopApp/.../ui/media/CompressionFailureDialog.kt` |
| 7.2 | `data class FailedAttachment(file: File, exception: CompressionException, originalBytes: Long, sourceFormat: ImageFormat)` | same |
| 7.3 | Per-failure row: filename + "could not be compressed: ${e.message}" + "original size: X MB". Show "EXIF will be stripped" tag if source is JPEG and `stripExif=true`; "metadata may be present" if source is non-JPEG | same |
| 7.4 | Confirm button label: `"Send Original" + (failures.size)`; Cancel: `"Cancel Post"` | same |
| 7.5 | In `ComposeNoteDialog`, `var pendingFailureDialog by remember { mutableStateOf<FailureDialogState?>(null) }`. On send-loop completion with non-empty failures, create state with `CompletableDeferred<UserChoice>`; `choice.await()` suspends; button callbacks call `choice.complete(...)` | `ComposeNoteDialog.kt` |
| 7.6 | "Send Original" path: for each failed attachment, if `stripExif=true` AND source is JPEG, run `MediaCompressor.stripExif(file)` first, then `orchestrator.upload(...)`. For non-JPEG, upload raw (with the dialog text being honest about that) | same |
| 7.7 | Map exception types to friendly labels: `InputTooLarge` → "larger than 50 megapixels"; `UnsupportedFormat` → "format not supported (e.g., AVIF)"; `EncodeFailed` → "encoder failed (${cause})" | `CompressionFailureDialog.kt` |
| 7.8 | Unit test: 2 failed + 3 succeeded → dialog renders 2 entries; on confirm, all 5 events publish (2 with stripExif applied for JPEG, 3 with compressed bytes) | `ComposeNoteDialogTest.kt` (if existing pattern allows) |

### Phase 8 — Manual QA + edge cases (1 d)

| # | Task |
|---|---|
| 8.1 | 10 MB JPEG @ Desktop High → < 2 MB output |
| 8.2 | Same JPEG @ Low → < 200 KB output |
| 8.3 | 320×240 source @ DESKTOP_HIGH → dims unchanged, re-encoded at q=0.9 |
| 8.4 | Animated GIF → byte-identical pass-through (sha256 match against original) |
| 8.5 | Animated WebP (with `ANIM` chunk + VP8X bit-1) → pass-through |
| 8.6 | SVG → pass-through |
| 8.7 | AVIF → `CompressionFailureDialog` with "format not supported" |
| 8.8 | 51 MP synthetic PNG → `CompressionFailureDialog` with "larger than 50 megapixels" |
| 8.9 | Corrupt JPEG → `CompressionFailureDialog` with encoder error |
| 8.10 | Display P3 iPhone JPEG → output retains ICC profile (`exiftool -ICC_Profile:all` shows non-empty) |
| 8.11 | Paste screenshot → upload succeeds; filename `paste-…jpg`; mime `image/jpeg` |
| 8.12 | 3-image post → progress shows 1/3, 2/3, 3/3 |
| 8.13 | Per-post override applies to all 3 attachments |
| 8.14 | Per-post override resets after send |
| 8.15 | Per-post override resets on dialog dismiss without send |
| 8.16 | No `~/.amethyst/tmp/amethyst_*` files remain after a 5-upload session + close |
| 8.17 | Boot sweep deletes a manually-planted orphan > 24 h old; leaves a newer one |
| 8.18 | Mid-send dialog dismissal cancels remaining attachments; no orphan temps |
| 8.19 | `./gradlew :amethyst:compileDebugKotlin` passes (Android unbroken) |
| 8.20 | `./gradlew :cli:assemble` passes (CLI unbroken) |
| 8.21 | `./gradlew spotlessApply` produces no diff |
| 8.22 | Pre-commit hooks pass (no `--no-verify`) |

### Phase Dependency Graph

```
P0 ─→ P1 ─→ P2 ─→ P3 ─→ P4 ─→ P5 ─→ P6 ─→ P7 ─→ P8
        ↘ (P1.20 sha-correctness covers P7's bypass path)
```

P3 through P7 all touch `ComposeNoteDialog` and/or its options row, so
they serialize. Total realistic: **7 working days for one engineer.**

## Alternative Approaches Considered

| Approach | Why Rejected |
|---|---|
| Migrate Android off Zelory now | Two-year-stable behavior; risk:reward bad. Separate plan later. |
| imgscalr instead of Thumbnailator | Worse downscale quality at high ratios. |
| `javax.imageio.ImageIO` + `Graphics2D`, no new dep | Hand-rolling multi-pass progressive scaling = more code we own. |
| JNI libwebp for WebP output | Bundles native libs per OS. Defer until users actually ask. |
| JNI libheif for HEIC input | Same — defer; users can convert client-side. |
| sejda webp-imageio | JNI under the hood + abandoned (last release 2017). |
| TwelveMonkeys `imageio-heif` | Doesn't exist. |
| Auto-codec detection per relay | Premature; codec is JPEG-only in v1. |
| Smart-auto (size-based) compression | Rejected at brainstorm — predictability over magic. |
| NIP-78 sync of settings | Heavier; deferred. |
| 5 quality presets (incl. Android-parity "Uncompressed 640@90") | Confusing on desktop; review forced cut. |
| Drag-drop pre-encode preview | Out of scope per brainstorm Q9. |
| Per-image override | Power-user; v1 is per-post. |
| Compare dialog v1 | YAGNI per simplicity; Compose state-isolation trap. Moved to Future. |
| `Compressing` tracker state class | Cosmetic; inline text on existing `Uploading` works. |
| `AlertDialog` for failure dialog | State-isolation trap; switched to `Dialog { Surface }`. |
| `DropdownMenu` for quality preset | Codebase prefers `SingleChoiceSegmentedButtonRow` for ordered enum settings. |
| Polluting `MediaAttachmentRow` with post-level options | Conflates "attachments list" with "post-level config." Use the existing options row. |

## System-Wide Impact

### Interaction Graph

```
User clicks Send in ComposeNoteDialog (dialog-scoped coroutineScope)
  ├─ for each (idx, file):
  │    update tracker text "Processing idx/total: name"
  │    val r = ImageReencoder.reencode(file, activeQuality)  [compressionDispatcher]
  │       ├─ ensureActive()
  │       ├─ sniffer → ImageFormat
  │       ├─ if Animated/Svg → PassThrough; if Avif → throw UnsupportedFormat
  │       ├─ ImageReader.getWidth(0)/getHeight(0) (pre-decode)
  │       ├─ if pixels > MAX → throw InputTooLarge
  │       ├─ reader.read(0, paramWithSubsampling)  [memory-bounded]
  │       ├─ ensureActive()
  │       ├─ Thumbnails.of(img).size(maxDim).outputQuality(q).useExifOrientation(true).toFile(temp)
  │       └─ embed ICC → Reencoded(temp)
  │    catch CompressionException → failures += FailedAttachment(...)
  │    if Reencoded → orchestrator.upload(temp, alt, ...); finally cleanup temp
  │    if PassThrough → orchestrator.upload(file, alt, ..., stripExif=true)
  ├─ if failures.isNotEmpty():
  │    pendingFailureDialog = FailureDialogState(failures, CompletableDeferred())
  │    when (choice.await()):
  │      SendOriginal → for each failed: maybeStripExif → orchestrator.upload
  │      Cancel       → return
  └─ perPostOverride = null
```

### Error & Failure Propagation

| Source | Where caught | User-visible behavior |
|---|---|---|
| `InputTooLarge` | At header-dim guard, pre-decode | `CompressionFailureDialog` shows the file, user can send original (still EXIF-stripped if JPEG) |
| `UnsupportedFormat` | At sniffer | same dialog with "format not supported" |
| `EncodeFailed` | At encode step (Thumbnailator throws) | same dialog with `${e.cause.message}` |
| `IOException` (disk full mid-temp-write) | Bubbles up as `EncodeFailed(cause)` | same dialog |
| `OutOfMemoryError` | Should be impossible (pre-decode pixel guard + subsampling). If it does happen, JVM state is undefined — exception escapes; user sees a generic "send failed" toast (existing path) | n/a |
| Blossom upload `IOException` | In `client.upload` (unchanged) | Existing upload error toast |
| Dialog dismiss mid-send | `coroutineScope.cancel()` → `ensureActive()` throws → cleanup in `finally`/`NonCancellable` | Tracker resets |

### State Lifecycle Risks

| Risk | Mitigation |
|---|---|
| Temp files orphaned on JVM crash | Boot-time sweep of `~/.amethyst/tmp/amethyst_*` > 24 h old. Plus eager `try { } finally { withContext(NonCancellable) { temp.delete() } }` for normal exit. |
| Compression mid-flight when user cancels | `ensureActive()` between stages catches cancellation; cleanup via `NonCancellable`. Up to ~1 s of ghost work for in-progress decode/encode is acceptable. |
| Per-post override leaks across posts | Reset on send AND in `DisposableEffect.onDispose` when dialog leaves composition. |
| Compare-dialog cache file orphan | Moot — Compare dialog dropped from v1. |
| `Uploading` text stuck after failure | `ComposeNoteDialog` send-loop always resets tracker in a `finally`. |

### API Surface Parity

| Surface | Before | After |
|---|---|---|
| `UploadOrchestrator.upload` | `(file, alt, server, signer, stripExif=true)` | `(file, alt, server, signer, stripExif=true, quality=DEFAULT)` — backward-compatible default |
| `MediaCompressor` | EXIF strip only | Unchanged (re-encode lives in new `ImageReencoder`) |
| `ImageReencoder` | n/a | **New** — `reencode(File, quality)` + `reencode(BufferedImage, name, quality)` |
| `CompressionQuality` | n/a | **New** — 3-value enum |
| `CompressionException` | n/a | **New** — sealed with `initCause` |
| Failure mode | Silent fallback on Android | **New** on desktop: fail-loud + confirm-to-bypass |

### Integration Test Scenarios

1. JPEG round-trip: 10 MB JPEG → compressed → uploaded → other client renders correctly.
2. Multi-image mixed: 1 PNG + 1 animated GIF + 1 SVG → progress 1/3, 2/3, 3/3; PNG re-encoded; GIF + SVG pass through.
3. AVIF rejection: drop AVIF → failure dialog → user confirms Send Original → raw AVIF uploads.
4. Per-post override applies to all: 3 photos, override Low → all 3 land at 640px / q=0.65.
5. Override reset: post sent → re-open compose → reads Desktop High.
6. Override reset on dismiss: open compose, set Low, dismiss without send, re-open → reads Desktop High.
7. Settings persistence: change default to Low + EXIF off → restart app → defaults persist.
8. No temp leak: 5 uploads + 3 cancels + 2 pastes → `~/.amethyst/tmp/` empty after close.
9. Boot sweep: manually plant 30-h-old file, launch → sweep removes it; plant 1-h-old file → sweep keeps it.
10. Display P3 retention: iPhone P3 JPEG → uploaded file shows ICC in `exiftool`.
11. CLI parity (deferred): `amy upload` reuses pipeline — separate plan; future consideration.
12. Animated GIF byte identity: download from Blossom → sha256 == original.

## Acceptance Criteria

### Functional

- [ ] Desktop Settings → Media section with: Quality (`SingleChoiceSegmentedButtonRow` of Low / Medium / Desktop High), Strip EXIF (`Switch`, default on).
- [ ] Compose dialog shows current default quality + lets user override per-post via `FilterChip` + `DropdownMenu` in the existing options row.
- [ ] Per-post override applies to ALL attachments on the post.
- [ ] Per-post override resets after send AND on dialog dismissal.
- [ ] 3 quality presets:
  - LOW: max-dim 640, JPEG q=0.65
  - MEDIUM: max-dim 640, JPEG q=0.75
  - DESKTOP_HIGH: max-dim 1920, JPEG q=0.90 (default)
- [ ] Never upscale — source < preset max-dim is not resized; re-encoded at preset quality.
- [ ] Output is always JPEG (mime `image/jpeg`).
- [ ] HEIC inputs trigger `UnsupportedFormat` → failure dialog (HEIC out of v1).
- [ ] AVIF inputs trigger `UnsupportedFormat` → failure dialog.
- [ ] JPEG / PNG / WebP (static) inputs re-encode.
- [ ] Animated GIF, animated WebP (VP8X bit-1 + ANIM chunk), SVG pass through byte-identical.
- [ ] Inputs > 50 MP trigger `InputTooLarge` → failure dialog.
- [ ] Clipboard paste routes through pipeline; filename `paste-YYYYMMDD-HHMMSS.jpg`.
- [ ] Multi-image posts show "Processing N/M" progress.
- [ ] EXIF stripped by default on JPEG output (re-encode naturally drops; pass-through path invokes `stripExif`).
- [ ] "Keep EXIF" toggle preserves EXIF on JPEG pass-through only (compressed outputs have no EXIF by design).
- [ ] Compression failure → `CompressionFailureDialog` with per-file row + Send Original / Cancel buttons.
- [ ] "Send Original" path applies `stripExif` for JPEG sources when EXIF toggle is on. Non-JPEG sources upload raw and the dialog text says so.
- [ ] Settings persist in `DesktopPreferences` across JVM restarts.
- [ ] CLI: `amy dm send-file` continues to work (uses default `CompressionQuality.DEFAULT` via orchestrator default param).

### Non-functional

- [ ] 4032×3024 JPEG compresses end-to-end in < 1 s on 2020+ Apple Silicon.
- [ ] 4032×3024 JPEG at Desktop High → < 2 MB output.
- [ ] Pure-Java; no JNI. Total dep footprint addition ≤ 1 MB (Thumbnailator only).
- [ ] No `deleteOnExit()` in new/modified code paths.
- [ ] No silent compression failures (every failure surfaces a dialog or is a guarded pass-through).
- [ ] Memory: peak heap during compression ≤ ~250 MB for a 50 MP source (with subsampling decode).
- [ ] Display P3 ICC profile preserved on output.
- [ ] CLI runs with `-Djava.awt.headless=true`; no AWT GUI thread spawned.
- [ ] Linux: temp files land in `~/.amethyst/tmp/` (mode 0700), not `/tmp` (tmpfs).

### Quality Gates

- [ ] `./gradlew :commons:compileKotlinJvm` passes
- [ ] `./gradlew :commons:jvmTest --tests "*Compression*"` passes
- [ ] `./gradlew :commons:jvmTest --tests "*UploadOrchestrator*"` passes
- [ ] `./gradlew :commons:jvmTest --tests "*ImageReencoder*"` passes
- [ ] `./gradlew :commons:jvmTest --tests "*ImageFormatSniffer*"` passes
- [ ] `./gradlew :commons:jvmTest --tests "*ImageCompressionStore*"` passes
- [ ] `./gradlew :desktopApp:compileKotlin` passes
- [ ] `./gradlew :amethyst:compileDebugKotlin` passes (Android unbroken)
- [ ] `./gradlew :cli:assemble` passes
- [ ] `./gradlew spotlessApply` produces no diff
- [ ] Pre-commit hooks pass
- [ ] Manual QA in Phase 8 fully green

## Success Metrics

- **Bandwidth saved per upload:** ≥ 70 % size reduction on typical 10 MB JPEG at Desktop High; ≥ 90 % at Medium. Recorded via JFR `ImageCompressionEvent` (input size, output size, peak heap, duration, codec, preset). Queryable via `jfr view ImageCompressionEvent <recording>`.
- **Time-to-upload improvement:** ≥ 50 % faster wall-clock on a typical 10 MB photo / 5 Mbps uplink.
- **Failure visibility:** zero `catch (_: Exception)` in compression paths. Every failure surfaces a dialog.
- **Temp file leak rate:** 0 orphans in `~/.amethyst/tmp/` after a session.
- **ICC fidelity:** Display P3 source → output Color profile == source profile (verified via `exiftool` in CI).

## Dependencies & Risks

### New dependencies

| Lib | Version | License | Source | Risk |
|---|---|---|---|---|
| Thumbnailator | 0.4.21 | MIT | net.coobird | Low — mature, widely used in JVM CMSes |

(Note: NO TwelveMonkeys, NO sejda. Both dropped per review.)

### Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| ICC preservation gap in stock JPEG writer | Medium | Medium | Phase 0 gate verifies; downgrade acceptance to "no color management v1" if ImageIO can't embed. |
| Thumbnailator memory spike on huge images | Low | Low | Subsampling decode + 50 MP guard. |
| `BufferedImage` color profile differs across JDKs | Low | Low | Pin tests to JDK 21. |
| FilterChip + DropdownMenu UX cluttered in compose row | Low | Low | Phase 4 manual UX review. Can fall back to plain text + on-click dialog. |
| Animated WebP detection misses edge cases | Medium | Low | VP8X bit-1 + `ANIM` chunk per spec; treat false negatives as "compress as static" → user can re-attach. |
| Boot-time temp sweep deletes user file | Very Low | Medium | Strict prefix discipline (`amethyst_compress_*`, `amethyst_paste_*`). Never sweep without prefix match. |
| Headless flag breaks something else on CLI | Very Low | Low | CLI is JVM-only and never touches AWT GUI today; verified. |
| Supply chain | Low | Medium | Pin Thumbnailator SHA-256 in `verification-metadata.xml`. OWASP dep-check in CI. |

## Future Considerations

- **WebP output** — once a pure-Java encoder exists, or once we accept JNI libwebp. Settings hook ready (would re-introduce `OutputCodec` enum + per-codec quality).
- **HEIC input** — same: pure-Java decoder when it appears, otherwise JNI libheif.
- **AVIF input** — same as HEIC.
- **Before/After compare dialog** — moved here. Implementation pattern: `Dialog { Surface { ... } }` with `LaunchedEffect` in PARENT scope (per `custom-feeds-alertdialog.md`); cache cleanup via `DisposableEffect`.
- **Pipelined compress + upload** — `Channel<File>(capacity = 1)` between two coroutines saves wall-clock on multi-image posts (~5 s on 5-image post). Safe upgrade after v1.
- **Migrate Android off Zelory** to the shared `commons/jvmMain` pipeline — separate plan; this v1 proves the abstraction.
- **CLI: `amy upload`** —
  ```
  amy upload <file> [--server URL] [--quality low|medium|desktop-high]
                    [--keep-exif] [--alt TEXT]
                    [--account NAME] [--json]
  ```
  Reuses `ImageReencoder` + `UploadOrchestrator.upload`. Exit codes 0 OK · 1 runtime · 2 bad args. Pairs with future `amy post --attach <url>`.
- **NIP-78 cross-device sync of compression settings** — settings follow user between desktops.
- **Per-image override** — power-user mode for mixed-content posts.
- **Custom quality slider (1–100) + arbitrary max-dim** — power-user mode.
- **Video compression** — separate effort; depends on FFmpeg or VLC bundling.
- **Iterative target-size compression** — "compress until ≤ 500 KB."
- **Modern WebP via native libwebp** — escape hatch flag for compression-conscious users on trusted hosts.

## Documentation Plan

- [ ] Update `commons/ARCHITECTURE.md` `service/upload` section: list `ImageReencoder` and `CompressionQuality`.
- [ ] Update `docs/temp-file-cleanup-analysis.md`: mark desktop `clipboard_*` and `stripped_*` leaks as FIXED; add the new `~/.amethyst/tmp/` policy.
- [ ] No README needed in `desktopApp/` for one settings section.
- [ ] In-code: minimal KDoc on `ImageReencoder.reencode()` and `CompressionQuality`. No prose paragraphs.

## Sources & References

### Origin

- **Brainstorm:** [docs/brainstorms/2026-06-08-desktop-image-compression-brainstorm.md](../brainstorms/2026-06-08-desktop-image-compression-brainstorm.md)
- Key decisions carried forward (some revised during deepening, see Enhancement Summary above):
  1. Shared pipeline in `commons/jvmMain` (extended via new `ImageReencoder`).
  2. JPEG-only v1 (forced by lack of pure-Java WebP encoder).
  3. 3 quality presets including new Desktop High; default = Desktop High.
  4. HEIC dropped (no library); AVIF detected and refused; animated formats pass through.
  5. Strip EXIF default-on; toggle to keep; bypass path still strips on JPEG.
  6. Fail-loud + confirm-to-bypass on failure (`Dialog { Surface }`, not `AlertDialog`).
  7. Per-post override scope = whole post; resets on send AND on dismiss.
  8. Settings via new `ImageCompressionStore` + `LocalCompressionSettings` CompositionLocal.
  9. Clipboard paste integrated; no PNG temp roundtrip.
  10. Compare dialog deferred to Future.

### Internal references

- `commons/src/jvmMain/.../service/upload/MediaCompressor.kt:28-50` — existing `stripExif()`
- `commons/src/jvmMain/.../service/upload/UploadOrchestrator.kt:45-86` — orchestration
- `commons/src/jvmMain/.../service/upload/MediaMetadata.kt` — `MediaMetadataReader.compute`
- `commons/ARCHITECTURE.md:114,188-192` — `service/upload` JVM placement
- `desktopApp/src/jvmMain/.../ui/ComposeNoteDialog.kt:326-350,430-435` — options row + send path
- `desktopApp/src/jvmMain/.../ui/media/ClipboardPasteHandler.kt:43` — current PNG-temp leak
- `desktopApp/src/jvmMain/.../ui/media/MediaAttachmentRow.kt` — attachments thumbnails (unchanged)
- `desktopApp/src/jvmMain/.../ui/settings/MediaServerSettings.kt:70-98` — settings panel template
- `desktopApp/src/jvmMain/.../SearchHistoryStore.kt` — canonical Store + StateFlow + prefs pattern
- `desktopApp/src/jvmMain/.../service/upload/DesktopUploadTracker.kt:27-53` — tracker state
- `desktopApp/src/jvmMain/.../ui/SearchScreen.kt:564-580` — Material3 AlertDialog example (trivial confirms)
- `desktopApp/src/jvmMain/.../ui/deck/FeedBuilderDialog.kt:191` — `SingleChoiceSegmentedButtonRow` precedent + `Dialog { Surface }` precedent
- `desktopApp/src/jvmMain/.../ui/account/AccountSwitcherDropdown.kt:111` — `FilterChip` + anchored `DropdownMenu` precedent
- `desktopApp/src/jvmMain/.../ui/deck/LocalFeedProvider.kt` — `CompositionLocal` precedent
- `cli/src/main/kotlin/.../cli/commands/DmCommands.kt:31,163` — Amy's existing call into `UploadOrchestrator`
- `cli/ROADMAP.md:65` — Blossom uploads roadmap (`amy upload` future)
- `docs/plans/2026-03-16-feat-desktop-media-full-parity-plan.md` — sibling plan (shipped Phases 0-3; compression was its gap)
- `docs/temp-file-cleanup-analysis.md:25-44` — temp-leak baseline
- `commons/src/commonMain/.../icons/symbols/MaterialSymbols.kt` — icon set (no new codepoints needed v1)
- Memory note `custom-feeds-alertdialog.md` — AlertDialog state-isolation trap
- Memory note (`MEMORY.md`) — `kotlinx.collections.immutable` available; `Preferences` for desktop persistence

### External references

- [Thumbnailator (0.4.21)](https://github.com/coobird/thumbnailator)
- [Thumbnailator scaling quality issue #179](https://github.com/coobird/thumbnailator/issues/179)
- [TwelveMonkeys WebP wiki (decode only — confirms no encoder)](https://github.com/haraldk/TwelveMonkeys/wiki/WebP-Plugin)
- [TwelveMonkeys HEIC request #976 (confirms no HEIC plugin)](https://github.com/haraldk/TwelveMonkeys/issues/976)
- [JDK 21 JFR memory events / `jfr view`](https://docs.oracle.com/en/java/javase/21/jfapi/)
- [WebP container spec (animation detection)](https://developers.google.com/speed/webp/docs/riff_container)
- [Quarkus EXIF-stripper tutorial (2025) — same architecture style](https://www.the-main-thread.com/p/quarkus-image-metadata-stripper-exif-removal-tutorial)
- [libwebp CVE-2023-4863 (informational; we ship no native code)](https://nvd.nist.gov/vuln/detail/CVE-2023-4863)
- [Blossom protocol](https://github.com/hzrd149/blossom)

## Resolved-from-Brainstorm Questions

- **Q2 / HEIC decoder:** **Dropped from v1.** Pure-Java HEIC plugin does not exist in 2026 (TwelveMonkeys #976 confirms). JNI libheif rejected for v1 to keep installer JNI-free.
- **Q4 / WebP encoder:** **Dropped from v1.** No pure-Java WebP encoder exists (sejda fork is JNI + abandoned; TwelveMonkeys WebP is decode-only). Output is JPEG-only.
- **Q5 / Never upscale:** Locked. Source dim < preset max-dim → no resize; re-encode at preset quality only.
- **Q8 / Compose dialog placement:** Quality chip lives in the existing options row of `ComposeNoteDialog:326-350` next to `ServerSelector`, NOT inside `MediaAttachmentRow`. `FilterChip(selected = override != null)` + anchored `DropdownMenu`. Reset on send AND on `DisposableEffect.onDispose`.
- **Q10 / Pasted clipboard filename:** `paste-YYYYMMDD-HHMMSS.jpg` (always JPEG in v1; mime `image/jpeg`). No PNG temp roundtrip.
- **Q11 / Compare anchor:** **Compare dialog deferred to Future Considerations.** When it ships: `Dialog { Surface }` with effect hoisted to parent.
- **Q14 / Animated formats:** Pass through byte-identical. Detection via `ImageFormatSniffer`: GIF NETSCAPE2.0 extension; WebP VP8X bit-1 + `ANIM` chunk.
- **Brainstorm Q12 / Failure mode:** Confirmed: fail-loud + confirm-to-bypass. Resolved further during deepening: bypass path still applies `stripExif` for JPEG sources.

## Resolved-during-Deepening

- **EXIF on bypass:** YES — `stripExif` runs on JPEG bypass when `stripExif=true`. Non-JPEG bypass uploads raw; dialog text is explicit about which case applies.
- **Visual thumbnail in compare:** Moot — Compare dialog dropped from v1.
- **`Compare` icon codepoint:** Moot — Compare dropped.
- **Settings StateFlow pattern:** Use new `ImageCompressionStore` (mirrors `SearchHistoryStore`), not raw `DesktopPreferences`. `LocalCompressionSettings` CompositionLocal at App scope.
- **Compose components:** `SingleChoiceSegmentedButtonRow` for quality, `FilterChip + DropdownMenu` for chip, `Dialog { Surface }` (not `AlertDialog`) for failure dialog, `.collectAsState()` (not `collectAsStateWithLifecycle`) for state reads.
- **Coroutines:** `Dispatchers.Default.limitedParallelism(1)` for `compressionDispatcher`. `ensureActive()` between stages. `NonCancellable` for cleanup. `CompletableDeferred<UserChoice>` for failure dialog await.
- **AWT headless:** `-Djava.awt.headless=true` on CLI application{} block, top of `cli/Main.kt`, and `:commons:jvmTest` jvmArgs.
- **Temp dir:** `~/.amethyst/tmp/` mode 0700 with boot-time sweep of orphans > 24 h.
- **ICC profile:** Preserve on output via APP2 marker. Phase 0 gate.
- **Pre-decode guard:** Stream header dims via `ImageReader.getWidth(0)/getHeight(0)` THEN subsampled `reader.read(0, param)`. Never call `Thumbnails.of(file)` directly.
- **Phase order:** P3 → P4 → P5 → P6 → P7 serialized; ~7 working days total.

## Open Questions

- Do we have known-good Display P3 test fixtures (real iPhone photos) for ICC-preservation tests? Source from a contributor if not.
- Should the boot-time temp sweep also delete files in `~/.amethyst/tmp/` that DON'T match the `amethyst_*` prefix (i.e., be a strict allowlist for the directory)? Lean: YES — the directory is ours.
- Where exactly is `~/.amethyst/tmp/` for Windows / Linux? Windows: `%LOCALAPPDATA%\Amethyst\tmp\`; Linux: `$XDG_CACHE_HOME/amethyst/tmp/` (or `~/.cache/amethyst/tmp/`). Confirm consistency with `DesktopImageCacheFactory` precedent.
- Acceptable to ship without ICC profile preservation if Phase 0 reveals stock ImageIO can't embed cleanly? Lean: YES, with a follow-up issue — color shift is a regression vs current "ship raw" but matches Android Zelory behavior.
- Should Phase 7's "Send Original" button on a multi-failure dialog send ALL failures with one click, or one-at-a-time per row? Lean: one click sends all (atomic user decision).
