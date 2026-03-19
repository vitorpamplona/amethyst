---
title: "feat: Desktop Media — Full Parity"
type: feat
status: active
date: 2026-03-16
origin: docs/brainstorms/2026-03-16-desktop-media-brainstorm.md
deepened: 2026-03-16
---

# Desktop Media — Full Parity

## Enhancement Summary

**Deepened on:** 2026-03-16
**Research agents used:** Architecture Strategist, Performance Oracle, Security Sentinel, Code Simplicity Reviewer, Pattern Recognition Specialist, Race Condition Reviewer, Coil3 Best Practices Researcher, VLCJ Framework Docs Researcher, Blossom Protocol Researcher, Compose Desktop DnD/Clipboard Researcher, KMP Expect/Actual Pattern Analyzer

### Critical Corrections (from original plan)

| # | Original Assumption | Correction |
|---|-------------------|------------|
| 1 | `metadata-extractor` for EXIF stripping | **READ-ONLY library.** Use Apache Commons Imaging `ExifRewriter.removeExifMetadata()` for lossless JPEG EXIF removal |
| 2 | `LinkedHashMap.removeEldestEntry` for caches | **NOT thread-safe** — `get()` in access-order mode mutates internal linked list. Use existing `androidx.collection.LruCache` (already KMP dep in commons) or `ConcurrentHashMap` |
| 3 | `coil-gif` works on JVM desktop | **Android-only** — `AnimatedImageDecoder` requires Android API 28+. Use `org.jetbrains.skia.Codec` for GIF decoding (first frame or animated) |
| 4 | VLCJ via `SwingPanel` | **Flickering confirmed.** Use DirectRendering via `CallbackVideoSurface` → Skia Bitmap → Compose `Image` (no SwingPanel needed) |
| 5 | Missing `kotlinx-coroutines-swing` dep | Required for `Dispatchers.Main.immediate` on JVM desktop with Coil3 |
| 6 | BlossomFetcher "just move" to commons | **Not trivial** — depends on `BlossomServerResolver` which uses Android `LruCache` + `IRoleBasedHttpClientBuilder`. Must abstract resolver interface first |
| 7 | UploadOrchestrator "extract" to commons | Really a **REWRITE** — deeply coupled to `Context`, `Uri`, `R.string`, `Account`, `ServerType` enum with NIP-95/96/Blossom paths |
| 8 | `vlc-setup` plugin ready for production | **No confirmed arm64 macOS support.** Only tested on Intel Mac (High Sierra). macOS support marked "experimental" |
| 9 | Coil3 memory cache "just works" | **Hard-codes 512MB total memory on non-Android.** Must set `maxSizeBytes()` explicitly using `Runtime.getRuntime().maxMemory()` |
| 10 | `MediaUploadResult` has no platform deps | **Hidden dependency** on `BlurhashWrapper` which imports from Android-only `BlurHashFetcher.kt` |

### Key Improvements from Research

1. **VLCJ DirectRendering pattern** — Complete working code from ComposeVideoPlayer project (no SwingPanel)
2. **Coil3 JVM cookbook** — `PlatformContext.INSTANCE`, explicit cache sizing, stable Keyer for custom fetchers
3. **Blossom full spec** — BUD-01 through BUD-11 documented with HTTP examples and auth flow
4. **Compose DnD modern API** — `Modifier.dragAndDropTarget` (old `onExternalDrag` removed in 1.8), `text/uri-list` fallback for Linux
5. **Race condition mitigations** — 13 identified (3 CRITICAL), with concrete fixes
6. **Simplicity alternative** — Desktop-only code first, extract to commons later (0 users → ship fast)

---

## Overview

Add complete media functionality to Amethyst Desktop: image display, video playback, Blossom upload, drag-drop/paste UX, lightbox, gallery, encrypted DM media, profile gallery, server management, and audio playback. Currently desktop renders **zero media** — notes show URL text only, no inline images or video.

## Problem Statement

Desktop Amethyst is text-only. The `NoteCard` at `desktopApp/ui/note/NoteCard.kt:128-132` renders URLs as blue underlined text via `RichTextContent`. No `AsyncImage`, no Coil dependency, no ImageLoader setup. `UserAvatar` uses `coil3.compose.AsyncImage` from commons but likely fails silently — no `SingletonImageLoader` configured for desktop.

Android has 40+ media-related files across upload, display, playback, and gallery. All are tightly coupled to Android APIs (`Context`, `Uri`, `ContentResolver`, `BitmapFactory`, `MediaMetadataRetriever`, `ExoPlayer`).

## Proposed Solution

Extract platform-agnostic media logic to commons using a 4-layer architecture, then build desktop-specific UI on top. Blossom-only upload (no NIP-96). Coil3 for images (already KMP). VLCJ with DirectRendering for video.

(see brainstorm: `docs/brainstorms/2026-03-16-desktop-media-brainstorm.md`)

### Simplicity Consideration

The simplicity reviewer recommends a **desktop-only-first** approach: write desktop code in `desktopApp/` without extracting to commons. Extract later when Android migration happens. Rationale: desktop has zero users, so ship fast and iterate. This plan documents the full extraction architecture but **implementation should start desktop-only** for Phases 0-3, deferring commons extraction to a follow-up PR.

## Technical Approach

### Architecture: 4-Layer Extraction

```
Layer 1: commons/commonMain — Pure protocol (BlossomClient, auth, server discovery)
Layer 2: commons/commonMain — expect/actual file operations
Layer 3: commons/{androidMain,jvmMain} — Platform actuals
Layer 4: desktopApp/ and amethyst/ — Platform UI
```

### Research Insight: jvmAndroid Source Set

The codebase already has a `jvmAndroid` intermediate source set in `commons/build.gradle.kts` that bridges Android and Desktop JVM code. OkHttp-based HTTP clients work identically on both — place shared JVM code there, not in `commonMain` (which would try to compile for iOS/web targets).

### Key Architectural Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Upload protocol | Blossom only | NIP-96 deprecated (see brainstorm) |
| Image loading | Coil3 3.4.0 | Already in project, KMP support |
| Video playback | VLCJ 4.8.x + DirectRendering | SwingPanel flickers; DirectRendering uses CallbackVideoSurface → Skia Bitmap |
| Upload code | Desktop-only first, extract later | Ship fast, 0 desktop users, extract when Android migrates |
| Desktop input | Drag-drop + paste + file picker | Essential desktop UX |
| EXIF stripping | Apache Commons Imaging | `metadata-extractor` is read-only; Commons Imaging does lossless EXIF removal |
| Video bundling | Require system VLC for now | `vlc-setup` plugin has no confirmed arm64 macOS support |
| GIF decoding | `org.jetbrains.skia.Codec` | `coil-gif` is Android-only; Skia Codec decodes frames |
| Cache implementation | `androidx.collection.LruCache` | Already KMP dep in commons; thread-safe unlike `LinkedHashMap` |

### Existing Codebase Inventory

**Already shared (ready to use):**

| Component | Location |
|-----------|----------|
| BlurHashDecoder/Encoder | `commons/blurhash/` |
| PlatformImage expect/actual | `commons/blurhash/PlatformImage.kt` → `.android.kt` / `.jvm.kt` |
| Base64ImagePlatform | `commons/base64Image/` (JVM uses ImageIO) |
| RichTextParser (URL → media type) | `commons/richtext/RichTextParser.kt` |
| MediaContentModels | `commons/richtext/MediaContentModels.kt` |
| UserAvatar (AsyncImage) | `commons/ui/components/UserAvatar.kt` |
| GalleryParser | `commons/richtext/GalleryParser.kt` |
| Blossom protocol events | `quartz/nipB7Blossom/` (kind 24242, 10063, URI, UploadResult) |
| NIP-94 FileHeader | `quartz/nip94FileMetadata/` |
| NIP-92 IMetaTag | `quartz/nip92IMeta/` |
| NIP-68 PictureEvent | `quartz/nip68Picture/` |
| NIP-71 VideoEvent/VideoMeta | `quartz/nip71Video/` |
| ProfileGalleryEntryEvent | `quartz/experimental/profileGallery/` |
| `androidx.collection.LruCache` | Already in commons dependencies (KMP) |

**Android-only (needs extraction or desktop equivalent):**

| Component | File | Android Deps | Action |
|-----------|------|-------------|--------|
| BlossomUploader | `amethyst/service/uploads/blossom/BlossomUploader.kt` | ContentResolver, Uri, Context, MimeTypeMap | **Rewrite** HTTP core for desktop |
| UploadOrchestrator | `amethyst/service/uploads/UploadOrchestrator.kt` | Context, Uri, R.string, Account, ServerType | **Rewrite** — too coupled for extraction |
| MultiOrchestrator | `amethyst/service/uploads/MultiOrchestrator.kt` | Context | Defer |
| MediaUploadResult | `amethyst/service/uploads/MediaUploadResult.kt` | Hidden BlurhashWrapper dep | Fix dep chain first |
| MediaCompressor | `amethyst/service/uploads/MediaCompressor.kt` | Context, Bitmap, Uri, Compressor | expect/actual |
| BlurhashMetadataCalculator | `amethyst/service/uploads/BlurhashMetadataCalculator.kt` | Context, BitmapFactory, MediaMetadataRetriever | expect/actual |
| BlossomServerResolver | `amethyst/service/uploads/blossom/bud10/BlossomServerResolver.kt` | LruCache (Android), IRoleBasedHttpClientBuilder | Replace LruCache with `androidx.collection.LruCache` (KMP) |
| ServerHeadCache | `amethyst/service/uploads/blossom/bud10/ServerHeadCache.kt` | None | Move (safe) |
| ImageLoaderSetup | `amethyst/service/images/ImageLoaderSetup.kt` | Android SingletonImageLoader, GIF decoder (API 28+) | Desktop equivalent |
| ImageCacheFactory | `amethyst/service/images/ImageCacheFactory.kt` | Context for safeCacheDir | Desktop paths |
| BlossomFetcher | `amethyst/service/images/BlossomFetcher.kt` | Depends on BlossomServerResolver (Android LruCache) | Abstract resolver interface first |
| BlurHashFetcher | `amethyst/service/images/BlurHashFetcher.kt` | toAndroidBitmap() | JVM: toBufferedImage() |
| Base64Fetcher | `amethyst/service/images/Base64Fetcher.kt` | toBitmap() | JVM: toBufferedImage() |
| ZoomableContentView | `amethyst/ui/components/ZoomableContentView.kt` | Android zoom lib | Desktop equivalent |
| ImageGallery | `amethyst/ui/components/ImageGallery.kt` | Pure Compose | Extract |
| MediaAspectRatioCache | `amethyst/model/MediaAspectRatioCache.kt` | Android LruCache | `androidx.collection.LruCache` (KMP) |

### Dependency Changes

**desktopApp/build.gradle.kts — add:**
```kotlin
// Image loading (Coil3)
implementation(libs.coil.compose)
implementation(libs.coil.okhttp)
implementation(libs.coil.svg)
// NOTE: coil-gif is Android-only, skip it. Use Skia Codec instead.

// Coroutines — required for Coil3 Main dispatcher on JVM desktop
implementation(libs.kotlinx.coroutines.swing)

// Video playback (VLCJ)
implementation("uk.co.caprica:vlcj:4.8.3")

// EXIF stripping (lossless)
implementation("org.apache.commons:commons-imaging:1.0.0-alpha5")
```

**gradle/libs.versions.toml — add:**
```toml
vlcj = "4.8.3"
commons-imaging = "1.0.0-alpha5"
kotlinx-coroutines-swing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "kotlinx-coroutines" }
```

### Research Insight: Coil3 JVM Desktop Configuration

```kotlin
// Critical: PlatformContext.INSTANCE (not Android Context)
// Critical: Set memory cache explicitly (Coil3 hard-codes 512MB total on non-Android)
// Critical: Set disk cache to OS-appropriate persistent path (default is temp dir)

fun createDesktopImageLoader(): ImageLoader {
    return ImageLoader.Builder(PlatformContext.INSTANCE)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizeBytes(calculateDesktopMemoryCacheSize())
                .strongReferencesEnabled(true)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(getDesktopCacheDir().resolve("amethyst/image_cache").toOkioPath())
                .maxSizeBytes(512L * 1024 * 1024) // 512 MB
                .build()
        }
        .precision(Precision.INEXACT)
        .crossfade(true)
        .components {
            add(SvgDecoder.Factory()) // Works on JVM via Skia SVG
            // add(SkiaGifDecoder.Factory()) // Custom: first-frame GIF via Codec
            // add(BlossomFetcher.Factory(...))
        }
        .build()
}

fun calculateDesktopMemoryCacheSize(): Long {
    val maxMemory = Runtime.getRuntime().maxMemory()
    return (maxMemory * 0.25).toLong().coerceAtMost(512L * 1024 * 1024)
}

fun getDesktopCacheDir(): File {
    val os = System.getProperty("os.name").lowercase()
    return when {
        "mac" in os -> File(System.getProperty("user.home"), "Library/Caches")
        "win" in os -> File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"))
        else -> File(System.getenv("XDG_CACHE_HOME") ?: "${System.getProperty("user.home")}/.cache")
    }
}
```

### Research Insight: Skia GIF Decoding (replaces coil-gif)

```kotlin
// org.jetbrains.skia.Codec provides frame-by-frame GIF decoding
// Skia supports: PNG, JPEG, WebP (static), BMP, ICO, WBMP
// GIF: first frame via Image.makeFromEncoded, full animation via Codec
// Animated WebP: first frame only (same Codec approach for animation)
// HEIC: NOT supported on JVM

class SkiaGifDecoder(private val source: ImageSource) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        val data = org.jetbrains.skia.Data.makeFromBytes(bytes)
        val codec = org.jetbrains.skia.Codec.makeFromData(data)
        val bitmap = org.jetbrains.skia.Bitmap()
        bitmap.allocN32Pixels(codec.width, codec.height)
        codec.readPixels(bitmap, 0) // first frame
        bitmap.setImmutable()
        return DecodeResult(image = bitmap.asImage(), isSampled = false)
    }
    class Factory : Decoder.Factory { /* check mimeType == "image/gif" */ }
}
```

### Research Insight: VLCJ DirectRendering Pattern

```kotlin
// NO SwingPanel — renders directly to Skia Bitmap → Compose Image
val callbackVideoSurface = CallbackVideoSurface(
    object : BufferFormatCallback {
        override fun getBufferFormat(w: Int, h: Int): BufferFormat {
            info = ImageInfo.makeN32(w, h, ColorAlphaType.OPAQUE)
            return RV32BufferFormat(w, h)
        }
        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {
            byteArray = ByteArray(buffers[0].limit())
        }
    },
    object : RenderCallback {
        override fun display(mp: MediaPlayer, bufs: Array<out ByteBuffer>, fmt: BufferFormat?) {
            bufs[0].get(byteArray); bufs[0].rewind()
            val bmp = Bitmap(); bmp.allocPixels(info!!); bmp.installPixels(byteArray)
            imageBitmap = bmp.asComposeImageBitmap() // triggers recomposition
        }
    },
    true, VideoSurfaceAdapters.getVideoSurfaceAdapter()
)
// Then display via: Image(bitmap = imageBitmap, ...)
```

**Performance:** 2 frame copies per display frame. ~8MB/frame at 1080p. Adequate for 1-2 players, CPU-intensive for more. New ByteArray per frame required (reusing crashes).

### Research Insight: Compose Desktop Drag-and-Drop

```kotlin
// Modern API (1.8+): Modifier.dragAndDropTarget
// Old onExternalDrag was REMOVED in 1.8
// awtTransferable + DataFlavor.javaFileListFlavor for files
// text/uri-list fallback needed for Linux + browser drops
// Cannot inspect file types during drag hover — filter in onDrop

val dropTarget = remember {
    object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val transferable = event.awtTransferable ?: return false
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                onFilesDropped(files)
                return true
            }
            return false
        }
    }
}
```

**Clipboard:** Compose `ClipboardManager` is text-only. Use AWT `Toolkit.getDefaultToolkit().systemClipboard` with `DataFlavor.imageFlavor` for image paste, `DataFlavor.javaFileListFlavor` for file paste.

---

## Implementation Phases

### Phase 0: Spike — Coil3 + VLCJ Desktop Validation

**Goal:** Confirm Coil3 loads images on JVM desktop with disk cache. Confirm VLCJ DirectRendering works in Compose.

| # | Task | File | Details |
|---|------|------|---------|
| 0.1 | Coil3 spike | `desktopApp/spike/CoilSpike.kt` | `ImageLoader.Builder(PlatformContext.INSTANCE)` with DiskCache (persistent path), MemoryCache (explicit size), load HTTP image via AsyncImage |
| 0.2 | VLCJ spike | `desktopApp/spike/VlcjSpike.kt` | DirectRendering via `CallbackVideoSurface` → Skia Bitmap → Compose `Image`. Test mp4 URL. **NO SwingPanel** |
| 0.3 | Validate SVG | spike | Test `coil-svg` + `SvgDecoder.Factory()` — confirmed KMP-compatible via Skia SVG |
| 0.4 | Validate GIF (first frame) | spike | Test `Image.makeFromEncoded(bytes)` for static GIF. Optionally test `Codec` for frame count |
| 0.5 | Add `kotlinx-coroutines-swing` | `desktopApp/build.gradle.kts` | Required for `Dispatchers.Main.immediate` |

**Gate:** If VLCJ DirectRendering drops frames badly or Coil3 disk cache fails, evaluate alternatives (Klarity for video, OkHttp cache interceptor for images).
**Deliverable:** Spike branch with working video + image rendering.
**Effort:** 1-2 days.

### Research Insight: Spike Simplification

The simplicity reviewer notes Phase 0 can be a 20-line test in `main()` — no need for separate spike files. Just add deps, create `ImageLoader`, call `AsyncImage` in the existing desktop window.

---

### Phase 1: Image Display Foundation

**Goal:** Images render inline in desktop notes with blurhash previews.

| # | Task | Module | Details |
|---|------|--------|---------|
| 1.1 | Add Coil3 deps to desktopApp | `desktopApp/build.gradle.kts` | `coil-compose`, `coil-okhttp`, `coil-svg`, `kotlinx-coroutines-swing`. **NOT** `coil-gif` (Android-only) |
| 1.2 | Create DesktopImageLoaderSetup | `desktopApp/service/images/DesktopImageLoaderSetup.kt` | `ImageLoader.Builder(PlatformContext.INSTANCE)` with explicit MemoryCache size (25% of JVM heap, max 512MB), DiskCache (OS-appropriate persistent path), OkHttp, SvgDecoder, custom fetchers. Package: `com.vitorpamplona.amethyst.desktop.service.images` |
| 1.3 | Create DesktopImageCacheFactory | `desktopApp/service/images/DesktopImageCacheFactory.kt` | macOS: `~/Library/Caches/AmethystDesktop`, Linux: `$XDG_CACHE_HOME/AmethystDesktop`, Windows: `%LOCALAPPDATA%/AmethystDesktop/cache`. `maxSizeBytes(512MB)` |
| 1.4 | Create desktop BlossomFetcher | `desktopApp/service/images/DesktopBlossomFetcher.kt` | **Desktop-only first** (don't extract to commons yet). Simplified version without `IRoleBasedHttpClientBuilder`. Uses single OkHttpClient for now. Resolves `blossom:` URIs via kind 10063 server list |
| 1.5 | Create desktop BlurHashFetcher | `desktopApp/service/images/DesktopBlurHashFetcher.kt` | Uses `PlatformImage.toBufferedImage()` → `BitmapImage(toComposeImageBitmap())`. Implement stable `Keyer` to avoid recomposition flicker |
| 1.6 | Create desktop Base64Fetcher | `desktopApp/service/images/DesktopBase64Fetcher.kt` | Uses existing `Base64ImagePlatform.jvm.kt` with ImageIO |
| 1.7 | Create MediaAspectRatioCache | `desktopApp/model/MediaAspectRatioCache.kt` | Use `androidx.collection.LruCache(1000)` (already KMP dep). **NOT** `LinkedHashMap` (not thread-safe) |
| 1.8 | Update NoteCard for inline images | `desktopApp/ui/note/NoteCard.kt` | Parse imeta tags from events. When URL matches image extension → `AsyncImage` with blurhash placeholder. Use `RichTextParser` media classification |
| 1.9 | Initialize ImageLoader in Main.kt | `desktopApp/Main.kt` | Call `setSingletonImageLoaderFactory` at app startup. Use `@OptIn(DelicateCoilApi::class)` for eager init |
| 1.10 | Add SkiaGifDecoder | `desktopApp/service/images/SkiaGifDecoder.kt` | Custom Coil3 `Decoder` using `org.jetbrains.skia.Codec` for GIF first-frame rendering |

**Deliverable:** Notes with images show blurhash placeholder → full image. `UserAvatar` also starts working.

**Acceptance Criteria:**
- [ ] `./gradlew :desktopApp:compileKotlin` passes
- [ ] Desktop app shows inline images in feed notes
- [ ] Blurhash placeholders display while loading
- [ ] SVG images render correctly (via SvgDecoder)
- [ ] GIF shows first frame (static display is acceptable for MVP)
- [ ] `UserAvatar` renders profile pictures
- [ ] Disk cache persists across app restarts (not in temp dir)
- [ ] Android build unbroken: `./gradlew :amethyst:compileDebugKotlin`

### Research Insight: Coil3 Custom Fetcher Gotchas

- Use `coil3.Uri` not `android.net.Uri`
- `ConnectivityChecker` is a no-op on desktop (always returns "connected")
- Custom fetchers **must** implement stable `Keyer` or cache key — otherwise recomposition triggers re-fetch ([#2551](https://github.com/coil-kt/coil/issues/2551))
- `PlatformContext.INSTANCE` is an empty singleton on JVM — don't cast or use Android methods

---

### Phase 2: Desktop Blossom Upload Client

**Goal:** Upload media from desktop to Blossom servers.

**Approach:** Write a **desktop-only** upload client in `desktopApp/`. Don't extract to commons yet (simplicity-first). The Android upload path stays unchanged.

| # | Task | Module | Details |
|---|------|--------|---------|
| 2.1 | Create DesktopBlossomClient | `desktopApp/service/upload/DesktopBlossomClient.kt` | HTTP PUT `/upload`, `/mirror`, `/media`, DELETE. Takes `File` + metadata. Returns `BlossomUploadResult` (from quartz). Uses OkHttp. Package: `com.vitorpamplona.amethyst.desktop.service.upload` |
| 2.2 | Create DesktopBlossomAuthHelper | `desktopApp/service/upload/DesktopBlossomAuthHelper.kt` | Creates kind 24242 auth events via quartz `BlossomAuthorizationEvent`, base64-encodes for `Authorization: Nostr <b64>` header. Include `t`, `x`, `expiration`, `server` tags per BUD-11 |
| 2.3 | Create DesktopServerDiscovery | `desktopApp/service/upload/DesktopServerDiscovery.kt` | Queries kind 10063 from relays, resolves server list, caches with `androidx.collection.LruCache` |
| 2.4 | Create DesktopMediaMetadata | `desktopApp/service/upload/DesktopMediaMetadata.kt` | `ImageIO.read(file)` for dimensions, `BlurHashEncoder` (from commons) for blurhash, file size + SHA-256 |
| 2.5 | Create DesktopMediaCompressor | `desktopApp/service/upload/DesktopMediaCompressor.kt` | JPEG: `ImageWriteParam.compressionQuality`. EXIF strip: Apache Commons Imaging `ExifRewriter.removeExifMetadata()`. No re-encoding for EXIF removal |
| 2.6 | Create DesktopUploadOrchestrator | `desktopApp/service/upload/DesktopUploadOrchestrator.kt` | Coordinate: strip EXIF → compute metadata → auth → upload. Accept `File` directly (not Uri). Blossom-only (no NIP-95/96) |
| 2.7 | Create DesktopMediaUploadTracker | `desktopApp/service/upload/DesktopMediaUploadTracker.kt` | Simple `mutableStateOf(isUploading: Boolean)` for MVP (matches existing Android's 47-line tracker). StateFlow upgrade later |
| 2.8 | BUD-06 preflight check | `desktopApp/service/upload/ServerHeadCache.kt` | HEAD `/upload` with `X-Content-Type`, `X-Content-Length`, `X-SHA-256` headers. Cache results |
| 2.9 | Unit tests | `desktopApp/src/jvmTest/` | Mock OkHttp responses. Test upload, auth header format (base64 of kind 24242), SHA-256 computation, EXIF stripping |

**Deliverable:** `./gradlew :desktopApp:jvmTest` passes. Desktop can upload files to Blossom.

**Acceptance Criteria:**
- [ ] BlossomClient uploads file to test server (integration test or mock)
- [ ] Auth headers correctly signed (kind 24242 with `t=upload`, `x=<hash>`, `expiration`, `server`)
- [ ] SHA-256 computed correctly for test files
- [ ] EXIF stripped losslessly from JPEG test image
- [ ] BUD-06 preflight HEAD works
- [ ] Android build unbroken

### Research Insight: Blossom Protocol Details

**Upload flow per BUD-02:**
1. Compute SHA-256 of exact file bytes
2. Create kind 24242 event: `t=upload`, `x=<sha256>`, `expiration=+1hr`, `server=<domain>`
3. Base64-encode (standard base64 works despite spec saying base64url)
4. `PUT /upload` with `Authorization: Nostr <b64>`, `Content-Type`, `Content-Length`, `X-SHA-256`
5. Response: `BlobDescriptor` JSON with `url`, `sha256`, `size`, `type`, `uploaded`

**`/upload` vs `/media` (BUD-05):**
- `/upload` — server MUST NOT modify blob. Hash preserved.
- `/media` — server MAY optimize (strip EXIF, compress). Returned hash differs. Primal uses this exclusively.
- **Recommendation:** Use `/upload` first (simpler). Add `/media` support later for trusted servers.

**Auth token security (BUD-11):**
- Always include `server` tag to prevent token replay across servers
- Unscoped `delete` tokens can be replayed — always scope delete operations
- Include `x` tag (blob hash) for upload/delete operations

### Research Insight: Race Conditions in Upload

**CRITICAL:** Upload scope cancellation on navigation. If user navigates away during upload, the coroutine scope gets cancelled. Mitigation: launch uploads in a `GlobalScope` or `applicationScope` that survives navigation, with a reference in the tracker.

**HIGH:** DnD during active upload. User drops new files while upload in progress. Mitigation: queue uploads, don't replace in-flight uploads.

---

### Phase 3: Desktop Upload UX

**Goal:** Upload media from desktop via file picker, drag-drop, and clipboard paste.

| # | Task | Module | Details |
|---|------|--------|---------|
| 3.1 | Desktop file picker | `desktopApp/ui/media/DesktopFilePicker.kt` | `java.awt.FileDialog` (native look) or `JFileChooser` with media type filters. Multi-select support |
| 3.2 | Drag-and-drop handler | `desktopApp/ui/media/DragDropHandler.kt` | `Modifier.dragAndDropTarget` (modern API, 1.8+). `event.awtTransferable` + `DataFlavor.javaFileListFlavor`. Include `text/uri-list` fallback for Linux. Visual drop zone indicator via `onEntered`/`onExited` callbacks |
| 3.3 | Clipboard paste handler | `desktopApp/ui/media/ClipboardPasteHandler.kt` | AWT `Toolkit.getDefaultToolkit().systemClipboard`. Check `DataFlavor.imageFlavor` (→ save BufferedImage to temp file → upload) and `DataFlavor.javaFileListFlavor` (→ direct file upload) |
| 3.4 | Upload dialog composable | `desktopApp/ui/media/UploadDialog.kt` | Progress bar per file, server selector (kind 10063 list), alt text input, cancel button |
| 3.5 | Upload preview thumbnails | `desktopApp/ui/media/UploadPreview.kt` | Thumbnail generation from local file using ImageIO |
| 3.6 | Wire upload to ComposeNoteDialog | `desktopApp/ui/ComposeNoteDialog.kt` | Add "Attach media" button. Connect to file picker + drag-drop. On upload complete → insert imeta tag + URL into note |
| 3.7 | Media button in note composer | `desktopApp/ui/ComposeNoteDialog.kt` | IconButton (Attach) → opens file picker. Shows attached files with remove option |

**Deliverable:** Drop/paste/pick file → preview → upload → note publishes with embedded image.

**Acceptance Criteria:**
- [ ] File picker opens, filters by media type, multi-select works
- [ ] Drag file onto compose area shows drop zone highlight
- [ ] Clipboard paste (Ctrl+V / Cmd+V) detects images and files
- [ ] Upload progress shows per-file
- [ ] Server selection from kind 10063 list
- [ ] Alt text saved in imeta tag
- [ ] Published note contains correct imeta tags
- [ ] Cancel upload works mid-flight

### Research Insight: DnD Platform Quirks

| Platform | Behavior | Note |
|----------|----------|------|
| macOS Finder | `javaFileListFlavor` works | Aliases resolve to alias file (use `canonicalFile`) |
| Windows Explorer | `javaFileListFlavor` works | Stable |
| Linux Nautilus | `javaFileListFlavor` works | Some WMs only provide `text/uri-list` |
| Browser drag | Usually `text/uri-list` | Not `javaFileListFlavor` — handle separately |
| **Cannot inspect file types during drag hover** | Only know flavor (files vs text), not extensions | Filter in `onDrop`, show generic "drop files here" during hover |

---

### Phase 4: Video Playback (VLCJ DirectRendering)

**Goal:** Videos play inline in desktop notes.

| # | Task | Module | Details |
|---|------|--------|---------|
| 4.1 | Add VLCJ deps | `desktopApp/build.gradle.kts` | `uk.co.caprica:vlcj:4.8.3`. **No vlc-setup plugin** — require system VLC for now (arm64 macOS not confirmed for plugin) |
| 4.2 | DesktopVideoPlayer composable | `desktopApp/ui/media/DesktopVideoPlayer.kt` | DirectRendering: `CallbackVideoSurface` → `RV32BufferFormat` → `ByteBuffer.get(byteArray)` → `Skia Bitmap.installPixels()` → `asComposeImageBitmap()` → Compose `Image`. Lazy init player |
| 4.3 | Video controls overlay | `desktopApp/ui/media/VideoControls.kt` | Play/pause, seek bar, volume slider, fullscreen toggle, time display. Auto-hide after 2s. Compose overlay on top of Compose Image (no z-order issues since no SwingPanel) |
| 4.4 | Video in note rendering | `desktopApp/ui/note/NoteCard.kt` | When URL matches video extension → show thumbnail + play button. On click → load VLCJ player |
| 4.5 | Video upload support | `desktopApp/ui/media/UploadDialog.kt` | Accept video files in upload flow. No transcoding — upload as-is |
| 4.6 | Video thumbnail generation | `desktopApp/service/media/VideoThumbnailGenerator.kt` | Use VLCJ `snapshots().get(320, 0)` on a dedicated player. Play → seek to 2s → pause → snapshot → stop |
| 4.7 | VLCJ player pool | `desktopApp/service/media/VlcjPlayerPool.kt` | Single `MediaPlayerFactory`, pool of 2-3 reusable `EmbeddedMediaPlayer` instances. Reuse players (`media().play(newMrl)`) instead of create/destroy. Strong references to prevent GC crash |

**Deliverable:** Video posts play inline. Controls work. Resource-efficient.

**Acceptance Criteria:**
- [ ] mp4, webm URLs play inline (m3u8 if VLC supports)
- [ ] Play/pause, seek, volume controls functional
- [ ] Fullscreen toggle works
- [ ] Video pauses when scrolled out of view
- [ ] Player instances pooled (max 3 active)
- [ ] Graceful fallback if VLC init fails (show URL link)
- [ ] VLC not installed → show "Install VLC" prompt with download link

### Research Insight: VLCJ Lifecycle Critical Rules

1. **Never let player instances get garbage collected** — native callbacks crash JVM if Java object is collected
2. **Keep strong references** to `MediaPlayerFactory`, `EmbeddedMediaPlayer`, and video surfaces
3. **Reuse players** — one factory, pool of players. Change media with `media().play(newMrl)`
4. **Release order:** player first, then factory
5. **macOS: only CallbackVideoSurface works** — no heavyweight AWT components since Java 7
6. **Audio-only:** Use `MediaPlayerFactory("--no-video")` for audio tracks

### Research Insight: Race Conditions in Video

**CRITICAL:** VLCJ use-after-release segfault. If `DisposableEffect.onDispose` releases player while a `RenderCallback.display()` is executing on the VLC thread → native crash. Mitigation: state machine per player slot. Set `RELEASING` state, wait for in-flight render to complete, then release.

**CRITICAL:** Rapid navigation between notes with video. User scrolls fast → player allocated → scrolled away before `mediaPlayerReady` event → player released during initialization. Mitigation: debounce player allocation (300ms visibility threshold before allocating).

---

### Phase 5: Lightbox & Gallery

**Goal:** Full-screen media viewing with zoom and gallery navigation.

| # | Task | Module | Details |
|---|------|--------|---------|
| 5.1 | Lightbox overlay | `desktopApp/ui/media/LightboxOverlay.kt` | Full-window overlay with semi-transparent backdrop. Click outside to close |
| 5.2 | Zoom + pan | `desktopApp/ui/media/ZoomableImage.kt` | Mouse wheel zoom, click-drag pan. `graphicsLayer` with `scaleX/Y` + `translationX/Y`. Double-click to reset |
| 5.3 | Gallery carousel | `desktopApp/ui/media/GalleryCarousel.kt` | Multi-image navigation with left/right buttons and indicator dots |
| 5.4 | Save to disk | `desktopApp/ui/media/SaveMediaAction.kt` | Button → `JFileChooser` save dialog. Download URL → write to chosen path |
| 5.5 | Keyboard shortcuts | `desktopApp/ui/media/LightboxOverlay.kt` | Esc=close, Left/Right=navigate, +/-=zoom, Ctrl+S=save, Space=play/pause (video) |
| 5.6 | Extract ImageGallery layout logic | `amethyst/ui/components/ImageGallery.kt` → `commons/` | The 1-5+ image grid layout is pure Compose — extract to commons for reuse |

**Deliverable:** Click image → fullscreen lightbox with zoom. Multi-image carousel. Keyboard navigation.

**Acceptance Criteria:**
- [ ] Click any inline image opens lightbox
- [ ] Mouse wheel zooms in/out smoothly
- [ ] Click-drag pans when zoomed
- [ ] Arrow keys navigate gallery
- [ ] Esc closes lightbox
- [ ] Save downloads file to disk
- [ ] Video plays in lightbox with controls

### Research Insight: Race Condition — Lightbox Double-Click

**MEDIUM:** User double-clicks an image. First click opens lightbox, second click registers as "click outside" → closes immediately. Mitigation: 200ms debounce on lightbox open/close transitions.

---

### Phase 6: Encrypted Media (NIP-17 DM Files)

**Goal:** Send and receive encrypted files in DMs.

| # | Task | Module | Details |
|---|------|--------|---------|
| 6.1 | Verify encryption is shared | `quartz/nip17Dm/files/` | `ChatMessageEncryptedFileHeaderEvent` (kind 15) already in commonMain with AESGCM cipher from quartz utils. Should work on desktop as-is |
| 6.2 | Encrypted upload flow | `desktopApp/ui/chat/` | Pick file → encrypt with NIP-44 → Blossom upload → create ChatMessageEncryptedFileHeaderEvent |
| 6.3 | Encrypted download + display | `desktopApp/ui/chat/` | Receive encrypted file event → download from Blossom → decrypt → display/save |
| 6.4 | Chat file upload UI | `desktopApp/ui/chat/ChatFileUploader.kt` | Similar to Phase 3 upload dialog but in DM context. Show encryption indicator |

**Deliverable:** Desktop DM users can send/receive encrypted images and files.

**Acceptance Criteria:**
- [ ] Send image in DM from desktop, visible on Android
- [ ] Receive encrypted image from Android, displays on desktop
- [ ] Non-participants cannot view encrypted media
- [ ] Upload progress shown in chat compose

---

### Phase 7: Profile Gallery (NIP-68)

**Goal:** View and create picture-first posts (kind 20). Profile gallery tab.

| # | Task | Module | Details |
|---|------|--------|---------|
| 7.1 | Picture event display | `desktopApp/ui/note/PictureDisplay.kt` | Kind 20 renderer. Image-first layout with title + description below. Uses imeta tags for multi-image |
| 7.2 | Profile gallery tab | `desktopApp/ui/profile/GalleryTab.kt` | Grid layout of user's picture posts. Lazy grid with thumbnails |
| 7.3 | Picture post composer | `desktopApp/ui/media/PicturePostComposer.kt` | Create kind 20 events. Multi-image upload + imeta + title + description |
| 7.4 | Gallery entry event support | `desktopApp/ui/profile/GalleryTab.kt` | Read `ProfileGalleryEntryEvent` (kind 1163) for curated galleries |

**Deliverable:** Profile shows gallery tab with image grid. Users can create picture posts.

**Acceptance Criteria:**
- [ ] Profile screen shows Gallery tab
- [ ] Gallery grid loads thumbnails with blurhash
- [ ] Click thumbnail opens lightbox
- [ ] Can create kind 20 picture post from desktop
- [ ] Picture post visible on Android Amethyst

---

### Phase 8: Media Server Management

**Goal:** UI for managing Blossom server list (kind 10063).

| # | Task | Module | Details |
|---|------|--------|---------|
| 8.1 | Server list settings screen | `desktopApp/ui/settings/MediaServerSettings.kt` | View/add/remove servers. Drag to reorder |
| 8.2 | Server status checking | `desktopApp/service/media/ServerHealthCheck.kt` | HEAD request to verify availability. Show green/red indicator |
| 8.3 | Default server selection | settings UI | Mark preferred upload server. Persist in Preferences |
| 8.4 | Publish kind 10063 | Uses quartz `BlossomServersEvent` | Update on relays when user modifies list |

**Deliverable:** Settings page to manage Blossom servers.

**Acceptance Criteria:**
- [ ] Server list loads from kind 10063 event
- [ ] Add/remove servers updates list
- [ ] Health check shows server status
- [ ] Changes published to relays
- [ ] Upload dialog reflects server list

---

### Phase 9: Audio Playback

**Goal:** Play audio tracks (MP3, OGG, FLAC, WAV) inline in notes.

| # | Task | Module | Details |
|---|------|--------|---------|
| 9.1 | Audio player composable | `desktopApp/ui/media/AudioPlayer.kt` | VLCJ with `MediaPlayerFactory("--no-video")`. Play/pause + progress bar + time. No video surface needed |
| 9.2 | Audio in note rendering | `desktopApp/ui/note/NoteCard.kt` | URL matches audio extension → show inline audio player |
| 9.3 | Waveform visualization (optional) | `desktopApp/ui/media/AudioWaveform.kt` | Simple amplitude bars. Low priority — skip if VLCJ doesn't expose PCM |

**Deliverable:** Audio files play inline in notes.

**Acceptance Criteria:**
- [ ] MP3, OGG, FLAC URLs show audio player
- [ ] Play/pause and seek work
- [ ] Multiple audio players on screen don't conflict

### Phase Dependency Graph

```
Phase 0 (Spike) ──→ Phase 1 (Images) ─────┬──→ Phase 5 (Lightbox)
                                            │
                     Phase 2 (Upload) ──────┼──→ Phase 3 (Desktop UX) ──→ Phase 6 (Encrypted)
                                            │
                                            ├──→ Phase 7 (Gallery)
                                            │
                                            └──→ Phase 8 (Server Mgmt)

                     Phase 4 (Video) ────────────→ Phase 9 (Audio)

Independent: Phase 0 first. Then Phase 1, 2, 4 can run in parallel.
Ship Phases 0-3 as first PR. Phases 4-9 are incremental follow-ups.
```

## System-Wide Impact

### Interaction Graph

- Image display: NoteCard → RichTextParser → MediaContentModels → Coil3 ImageLoader → custom Fetchers → OkHttp → Blossom servers
- Upload: ComposeNoteDialog → DesktopFilePicker/DragDrop/Clipboard → DesktopMediaCompressor → DesktopBlossomClient → OkHttp → Blossom server → imeta tag → relay broadcast
- Auth chain: DesktopUploadOrchestrator → DesktopBlossomAuthHelper → quartz BlossomAuthorizationEvent → Account.signer → kind 24242 → base64 header

### Error & Failure Propagation

| Error Source | Handling |
|-------------|----------|
| Coil3 load failure | Show blurhash if available, else placeholder icon. Log error |
| Blossom upload network error | Retry with exponential backoff (3 attempts). Show error in upload dialog |
| VLCJ init failure (VLC not installed) | Show "Install VLC" prompt with download link. Fall back to URL link |
| SHA-256 mismatch after upload | Re-upload. Warn user if persistent |
| Server unreachable (BUD-06 preflight) | Skip server, try next in list |
| Encrypted media decrypt failure | Show "Unable to decrypt" placeholder |
| EXIF strip failure | Upload anyway with warning (privacy risk) |

### Race Condition Mitigations (from review)

| Severity | Issue | Mitigation |
|----------|-------|------------|
| **CRITICAL** | LinkedHashMap concurrent corruption | Use `androidx.collection.LruCache` (thread-safe) or `ConcurrentHashMap` |
| **CRITICAL** | VLCJ use-after-release segfault | State machine per player: `IDLE`→`LOADING`→`PLAYING`→`RELEASING`. Guard `RenderCallback.display()` with state check |
| **CRITICAL** | Upload scope cancelled on navigation | Launch uploads in application-scoped coroutine (survives navigation) |
| HIGH | Stale blurhash/image flash in lazy list | Stable `Keyer` for Coil3 custom fetchers |
| HIGH | DnD during active upload | Queue uploads, don't replace in-flight |
| HIGH | Non-atomic progress + state update | Use `MutableStateFlow<UploadState>` (single source of truth, atomic update) |
| HIGH | Pause-after-dispose (VLCJ) | Check player state before calling `controls().pause()` |
| MEDIUM | Stale server cache | TTL on server discovery cache (5 min) |
| MEDIUM | Lightbox double-click | 200ms debounce on open/close |
| MEDIUM | Gallery rapid navigation | Debounce image load requests |
| MEDIUM | StateFlow progress frequency | Throttle upload progress updates to 100ms intervals |
| LOW | Clipboard + DnD concurrent | Serialize input handling (queue) |
| LOW | Video control timer overlap | Single timer job for auto-hide |

### State Lifecycle Risks

| Risk | Mitigation |
|------|------------|
| Upload in progress + user navigates away | Application-scoped coroutine. Upload continues in background. Show notification on completion |
| VLCJ player leak | `DisposableEffect` with state machine release. Player pool enforces max count. Strong references prevent GC crash |
| Coil disk cache corruption | Coil3 handles internally. Worst case: re-download |
| Partial upload (network cut) | SHA-256 pre-computed. Server rejects incomplete uploads. Client retries |

### API Surface Parity

| Interface | Android | Desktop | Same? |
|-----------|---------|---------|-------|
| BlossomClient | Android-specific | Desktop-specific | **Different** (for now — extract to commons later) |
| UploadOrchestrator | Android-specific | Desktop-specific | **Different** (for now) |
| ImageLoader fetchers | Android-specific | Desktop-specific | **Different** (for now — same Coil3 API though) |
| VideoPlayer | ExoPlayer | VLCJ DirectRendering | Different |
| FilePicker | ActivityResultContracts | JFileChooser / FileDialog | Different |
| MediaCompressor | Zelory + MediaCodec | ImageIO + Commons Imaging | Different |

### Integration Test Scenarios

1. **Upload round-trip:** Desktop uploads image → published note with imeta → other client sees image via Blossom URL
2. **Cross-platform encrypted DM:** Android sends encrypted image → Desktop receives, decrypts, displays
3. **Blossom fallback:** Primary server down → resolver tries secondary server from kind 10063
4. **Gallery consistency:** Create kind 20 on desktop → visible in Android profile gallery
5. **Video lifecycle:** Scroll feed with 5 video notes → only 2-3 VLCJ players active → no OOM

## Alternative Approaches Considered

| Approach | Why Rejected |
|----------|-------------|
| NIP-96 + Blossom | NIP-96 deprecated. Double protocol = double maintenance (see brainstorm) |
| JavaFX MediaView for video | Adds JavaFX runtime (~50MB), conflicts with Compose Desktop rendering |
| SwingPanel for VLCJ | Confirmed flickering + always-on-top z-order issues. DirectRendering avoids entirely |
| Extract to commons immediately | Over-engineering for 0 desktop users. Ship desktop-only first, extract when needed |
| FFmpeg JNI for video | Complex native binding. VLCJ wraps VLC which bundles FFmpeg internally |
| Ktor instead of OkHttp | OkHttp already used everywhere. Both are JVM. No benefit to switching |
| `metadata-extractor` for EXIF strip | **Read-only library**. Only reads EXIF, cannot remove it |
| `LinkedHashMap` for caches | **Not thread-safe** in access-order mode. `get()` mutates internal state |
| Klarity (FFmpeg-based) | Interesting alternative (65 stars, Feb 2026 update), but small community. Worth prototyping as VLCJ backup |

## Risk Analysis & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| VLCJ DirectRendering CPU overhead at 1080p | Medium | Medium | ~8MB/frame copy. Adequate for 1-2 players. Pool limits exposure |
| VLC not installed on user system | High | High | Show clear "Install VLC" prompt. Link to VLC download. Future: bundle VLC |
| vlc-setup plugin no arm64 macOS | Confirmed | Medium | Don't use plugin for now. Require system VLC. Revisit when plugin matures |
| Coil3 JVM edge cases | Low | Medium | Coil 3.4.0 is mature. Explicit cache sizing avoids defaults. SvgDecoder confirmed working |
| Commons extraction breaks Android | N/A (deferred) | N/A | Desktop-only approach for Phases 0-3 eliminates this risk |
| Apache Commons Imaging alpha status | Medium | Low | Only using EXIF strip (well-tested path). Fallback: skip EXIF strip for MVP |
| Scope creep across 9 phases | High | High | Ship Phases 0-3 as first PR. Later phases are incremental |
| Compose DnD experimental API changes | Medium | Low | Pin Compose version. AWT `DropTarget` fallback available |

## Acceptance Criteria

### Functional Requirements

- [ ] Images display inline in desktop feed notes with blurhash placeholders
- [ ] Upload works via file picker, drag-drop, and clipboard paste
- [ ] Videos play inline with controls (play/pause, seek, volume)
- [ ] Lightbox opens on image click with zoom and gallery navigation
- [ ] Encrypted media works in DMs (send + receive)
- [ ] Profile gallery shows picture posts
- [ ] Blossom server management UI in settings
- [ ] Audio plays inline in notes

### Non-Functional Requirements

- [ ] Image load time < 2s for typical photos on broadband
- [ ] Upload shows progress in real-time
- [ ] Max 3 active video players (prevent OOM)
- [ ] Disk cache respects 512MB limit
- [ ] EXIF stripped before upload (privacy)
- [ ] Android build never broken (desktop-only approach)

### Quality Gates

- [ ] `./gradlew :desktopApp:compileKotlin` passes
- [ ] `./gradlew :desktopApp:jvmTest` passes (upload unit tests)
- [ ] `./gradlew :amethyst:compileDebugKotlin` passes (no Android breakage)
- [ ] `./gradlew spotlessApply` passes
- [ ] Manual test: full upload → display round-trip on desktop

## Key Files (New or Modified)

### New Files

| File | Phase |
|------|-------|
| `desktopApp/.../service/images/DesktopImageLoaderSetup.kt` | 1 |
| `desktopApp/.../service/images/DesktopImageCacheFactory.kt` | 1 |
| `desktopApp/.../service/images/DesktopBlossomFetcher.kt` | 1 |
| `desktopApp/.../service/images/DesktopBlurHashFetcher.kt` | 1 |
| `desktopApp/.../service/images/DesktopBase64Fetcher.kt` | 1 |
| `desktopApp/.../service/images/SkiaGifDecoder.kt` | 1 |
| `desktopApp/.../model/MediaAspectRatioCache.kt` | 1 |
| `desktopApp/.../service/upload/DesktopBlossomClient.kt` | 2 |
| `desktopApp/.../service/upload/DesktopBlossomAuthHelper.kt` | 2 |
| `desktopApp/.../service/upload/DesktopServerDiscovery.kt` | 2 |
| `desktopApp/.../service/upload/DesktopMediaMetadata.kt` | 2 |
| `desktopApp/.../service/upload/DesktopMediaCompressor.kt` | 2 |
| `desktopApp/.../service/upload/DesktopUploadOrchestrator.kt` | 2 |
| `desktopApp/.../service/upload/DesktopMediaUploadTracker.kt` | 2 |
| `desktopApp/.../ui/media/DesktopFilePicker.kt` | 3 |
| `desktopApp/.../ui/media/DragDropHandler.kt` | 3 |
| `desktopApp/.../ui/media/ClipboardPasteHandler.kt` | 3 |
| `desktopApp/.../ui/media/UploadDialog.kt` | 3 |
| `desktopApp/.../ui/media/UploadPreview.kt` | 3 |
| `desktopApp/.../ui/media/DesktopVideoPlayer.kt` | 4 |
| `desktopApp/.../ui/media/VideoControls.kt` | 4 |
| `desktopApp/.../service/media/VlcjPlayerPool.kt` | 4 |
| `desktopApp/.../service/media/VideoThumbnailGenerator.kt` | 4 |
| `desktopApp/.../ui/media/LightboxOverlay.kt` | 5 |
| `desktopApp/.../ui/media/ZoomableImage.kt` | 5 |
| `desktopApp/.../ui/media/GalleryCarousel.kt` | 5 |
| `desktopApp/.../ui/media/AudioPlayer.kt` | 9 |

### Modified Files

| File | Phase | Change |
|------|-------|--------|
| `desktopApp/build.gradle.kts` | 0,1,4 | Add Coil3, kotlinx-coroutines-swing, VLCJ, Commons Imaging deps |
| `gradle/libs.versions.toml` | 0,1,4 | Add new version entries |
| `desktopApp/ui/note/NoteCard.kt` | 1,4,9 | Add inline media rendering |
| `desktopApp/ui/ComposeNoteDialog.kt` | 3 | Add media attach button + upload flow |
| `desktopApp/Main.kt` | 1 | Initialize ImageLoader |

## Sources & References

### Origin

- **Brainstorm document:** [docs/brainstorms/2026-03-16-desktop-media-brainstorm.md](docs/brainstorms/2026-03-16-desktop-media-brainstorm.md)
- Key decisions carried forward: Blossom-only, Coil3 for images, VLCJ for video, desktop-only-first approach

### Internal References

- Existing shared UI analysis: `docs/shared-ui-analysis.md`
- PlatformImage expect/actual pattern: `commons/blurhash/PlatformImage.kt`
- Android ImageLoader setup: `amethyst/service/images/ImageLoaderSetup.kt:25-91`
- Android BlossomUploader: `amethyst/service/uploads/blossom/BlossomUploader.kt`
- NoteCard (current text-only): `desktopApp/ui/note/NoteCard.kt:128-132`
- ComposeNoteDialog (current text-only): `desktopApp/ui/ComposeNoteDialog.kt`
- Blossom protocol research: `docs/brainstorms/2026-03-16-blossom-protocol-research.md`

### External References

- Blossom protocol spec: https://github.com/hzrd149/blossom
- Coil3 Compose Multiplatform: https://coil-kt.github.io/coil/compose/
- Coil3 GIF on JVM limitation: https://github.com/coil-kt/coil/issues/2347
- Coil3 SVG on desktop: https://github.com/coil-kt/coil/issues/2330
- Coil3 Main dispatcher: https://github.com/coil-kt/coil/issues/2009
- VLCJ documentation: https://github.com/caprica/vlcj
- VLCJ GC tutorial: https://capricasoftware.co.uk/tutorials/vlcj/4/garbage-collection
- ComposeVideoPlayer (DirectRendering): https://github.com/rjuszczyk/ComposeVideoPlayer
- Klarity (FFmpeg alternative): https://github.com/numq/Klarity
- vlc-setup Gradle plugin: https://github.com/nickolay-mahozad/vlc-setup
- Apache Commons Imaging: https://commons.apache.org/proper/commons-imaging/
- Compose Desktop DnD docs: https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-drag-drop.html
- Skiko Codec API: https://jetbrains.github.io/skiko/skiko/org.jetbrains.skia/-codec/index.html
- HEIC not supported on JVM: https://github.com/JetBrains/skiko/issues/942

## Answered Questions (from original plan)

| # | Question | Answer |
|---|----------|--------|
| 1 | Does `coil3-gif` work on JVM desktop? | **No.** Android-only (`AnimatedImageDecoder` requires API 28+). Use `org.jetbrains.skia.Codec` for GIF decoding |
| 2 | Does `vlc-setup` support arm64 macOS? | **Unconfirmed.** Only Intel Mac (High Sierra) tested. macOS marked "experimental". Require system VLC for now |
| 3 | Does `awtTransferable` deliver files on macOS? | **Yes.** `DataFlavor.javaFileListFlavor` works with Finder on JDK 17+. Include `text/uri-list` fallback for Linux |
| 4 | Is `LinkedHashMap.removeEldestEntry` sufficient? | **No.** Not thread-safe in access-order mode. Use `androidx.collection.LruCache` (already KMP dep) or `ConcurrentHashMap` |
| 5 | Is Coil3 `SvgDecoder` KMP? | **Yes.** Works on JVM via `org.jetbrains.skia.svg.SVGDOM`. Add `SvgDecoder.Factory()` to ImageLoader components |
| 6 | Is `UserAvatar` rendering on desktop? | **Likely failing silently** — no `SingletonImageLoader` configured. Will work after Phase 1 ImageLoader init |

## Remaining Unanswered Questions

1. Does VLCJ DirectRendering achieve acceptable frame rate at 1080p on Apple Silicon?
2. Can `org.jetbrains.skia.Codec` handle all animated WebP variants reliably on JVM?
3. Is VLC 3.0.21 universal binary reliable via JNA on arm64 macOS with JDK 17+?
4. Is there a GPU-accelerated path for VLCJ → Skia Bitmap transfer (avoid CPU copy)?
5. Does `Modifier.dragAndDropTarget` work on Linux Wayland in Compose 1.10.x?
6. Can Klarity handle streaming URLs (HLS, DASH) as VLCJ alternative?
7. Does Coil3 `@ExperimentalCoilApi` `NetworkFetcher` constructor stay stable across versions?
