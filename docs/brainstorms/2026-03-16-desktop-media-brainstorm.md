# Brainstorm: Desktop Media — Full Parity

**Date:** 2026-03-16
**Status:** Draft
**Branch:** TBD (`feat/desktop-media`)

## What We're Building

Full media functionality for Amethyst Desktop — display, upload, gallery, lightbox, video playback, encrypted media, and desktop-native UX. Feature parity with Android Amethyst's media stack, adapted for mouse-first desktop interaction.

### Scope

| Feature | Included | Notes |
|---------|----------|-------|
| Image display in notes | Yes | Coil3 AsyncImage, blurhash previews |
| Video playback | Yes | VLCJ with bundled libvlc |
| Blossom upload | Yes | Extract to commons, Blossom-only (no NIP-96) |
| Drag-drop / clipboard paste | Yes | Essential desktop UX from day 1 |
| Lightbox / zoom | Yes | Full-screen media viewer with zoom |
| Image gallery carousel | Yes | Multi-image posts |
| Profile gallery (NIP-68) | Yes | Kind 20 picture posts, create + view |
| Video events (NIP-71) | Yes | Kind 21/22 display |
| Encrypted media (NIP-17 DMs) | Yes | Full send + receive |
| Media server management | Yes | Blossom server list (kind 10063) |
| Audio/voice playback | Yes | MP3, OGG, FLAC, WAV |
| Media compression | Yes | Desktop-adapted (Java ImageIO) |
| Blurhash generation on upload | Yes | Already in commons/ |
| EXIF stripping | Yes | Privacy: strip metadata before upload |
| Alt text / accessibility | Yes | NIP-92 imeta alt field |

### Out of Scope (for now)

- NIP-96 upload (deprecated, Blossom replaces it)
- Voice recording (microphone capture — desktop-specific, complex)
- Picture-in-picture video
- Live streaming (NIP-53)
- Torrent/magnet distribution

## Why This Approach

### Blossom Only (No NIP-96)

NIP-96 is officially marked "unrecommended: replaced by blossom APIs" in the NIP registry. Building desktop from scratch gives us the opportunity to skip legacy protocol support entirely.

**Blossom advantages:**
- Content-addressed (SHA-256) — files portable across servers
- Native mirroring (BUD-04) — upload once, mirror to N servers
- Server-side optimization (BUD-05) — `/media` endpoint
- Payment support (BUD-07) — Cashu/Lightning
- Clean URI scheme (BUD-10) — `blossom:<hash>.<ext>`

### Extraction Strategy — Layered Architecture

Android's BlossomUploader is tightly coupled to Android (`Context`, `Uri`, `ContentResolver`). We need to separate the HTTP upload protocol from platform file access.

**Layer 1: commons/commonMain — Pure Blossom protocol client**
- `BlossomClient` — HTTP PUT /upload, /mirror, /media, DELETE, GET /list. Takes `InputStream` + metadata. Returns `BlobDescriptor`. No platform deps.
- `BlossomAuthHelper` — Creates kind 24242 auth events, base64-encodes for Authorization header
- `BlossomServerDiscovery` — Queries kind 10063, resolves server list, caches
- `MediaUploadResult` — Result data class (already platform-agnostic)
- `UploadOrchestrator` — Coordinates upload to server + optional optimization + mirroring
- `MultiUploadOrchestrator` — Manages parallel upload of multiple files
- `MediaUploadTracker` — StateFlow-based progress tracking
- `ServerHeadCache` — BUD-06 pre-flight response cache

**Layer 2: commons/commonMain — expect/actual for platform file operations**
```
expect fun readFileBytes(path: String): ByteArray
expect fun computeFileSha256(path: String): String
expect fun getMimeType(path: String): String?
expect fun getFileSize(path: String): Long
expect class MediaMetadataExtractor {
    fun extractDimensions(path: String): Pair<Int, Int>?
    fun extractBlurhash(path: String): String?
}
expect class MediaCompressor {
    fun compressImage(path: String, quality: Float): String
    fun stripExif(path: String): String
}
```

**Layer 3: Platform actuals**
- `androidMain/` — Uses `ContentResolver`, `Uri`, Android Bitmap, `MediaMetadataRetriever`
- `jvmMain/` — Uses `java.io.File`, Java ImageIO, `metadata-extractor`, BufferedImage→blurhash

**Layer 4: Platform UI (desktopApp/ and amethyst/)**
- File pickers, drag-drop handlers, video players, lightbox composables

This design lets any future client (iOS, web) reuse Layer 1 + 2 by providing Layer 3 actuals.

### Coil3 for Image Loading

Coil3 officially supports Compose Multiplatform including JVM/Desktop. Android Amethyst already uses Coil3. Benefits:
- Disk + memory caching
- Custom fetchers (Blossom URI, blurhash, base64)
- Crossfade animations
- SVG support

### VLCJ for Video

ExoPlayer is Android-only (Media3). VLCJ wraps VLC's libvlc via JNA — supports every format VLC does (mp4, webm, m3u8, mkv, etc.). Compose integration via `SwingPanel` or offscreen rendering. We bundle libvlc with the app (~100MB) for zero user setup.

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Upload protocol | Blossom only | NIP-96 deprecated |
| Upload code location | commons/commonMain | Share with Android |
| Image loading | Coil3 | Already used on Android, KMP support |
| Video playback | VLCJ (bundled libvlc) | ExoPlayer is Android-only |
| Desktop input | Drag-drop + paste + file picker | Essential for desktop UX |
| Encrypted media | Full support | DM file sharing parity |
| Profile gallery | Yes (NIP-68 kind 20) | Create + view |

## Existing Codebase Audit

### Already Shared (commons/)

| Component | Location | Status |
|-----------|----------|--------|
| BlurHashDecoder/Encoder | `commons/blurhash/` | Ready |
| PlatformImage (expect/actual) | `commons/blurhash/PlatformImage.kt` | Ready (JVM uses BufferedImage) |
| BitmapUtils (JVM) | `commons/blurhash/BitmapUtils.jvm.kt` | Ready |
| Base64ImagePlatform (JVM) | `commons/base64Image/` | Ready |
| RichTextParser | `commons/richtext/RichTextParser.kt` | Ready (classifies URLs as image/video) |
| MediaContentModels | `commons/richtext/MediaContentModels.kt` | Ready (MediaUrlImage, MediaUrlVideo, etc.) |
| UrlParser | `commons/richtext/UrlParser.kt` | Ready |
| Image extensions list | RichTextParser companion | png, jpg, gif, bmp, jpeg, webp, svg, avif |
| Video extensions list | RichTextParser companion | mp4, avi, wmv, mpg, amv, webm, mov + audio |

### In Quartz (protocol layer, shared)

| Component | Location | Status |
|-----------|----------|--------|
| BlossomAuthorizationEvent | `quartz/nipB7Blossom/` | Ready (kind 24242) |
| BlossomServersEvent | `quartz/nipB7Blossom/` | Ready (kind 10063) |
| BlossomUri | `quartz/nipB7Blossom/` | Ready (blossom: URI parsing) |
| BlossomUploadResult | `quartz/nipB7Blossom/` | Ready |
| FileHeaderEvent (NIP-94) | `quartz/nip94FileMetadata/` | Ready (kind 1063) |
| BlurhashTag | `quartz/nip94FileMetadata/tags/` | Ready |
| DimensionTag | `quartz/nip94FileMetadata/tags/` | Ready |
| IMetaTag/Builder (NIP-92) | `quartz/nip92IMeta/` | Ready |
| PictureEvent (NIP-68) | `quartz/nip68Picture/` | Ready (kind 20) |
| VideoEvent (NIP-71) | `quartz/nip71Video/` | Ready (kind 21/22/34235/34236) |
| ProfileGalleryEntryEvent | `quartz/experimental/profileGallery/` | Ready |
| FileServersEvent (NIP-96) | `quartz/nip96FileStorage/` | Exists but we're skipping NIP-96 |
| ChatMessageEncryptedFileHeaderEvent | `quartz/nip17Dm/files/` | Ready (encrypted DM files) |

### Android-Only (needs extraction or desktop equivalent)

| Component | Location | Action |
|-----------|----------|--------|
| BlossomUploader | `amethyst/service/uploads/blossom/` | Extract HTTP logic to commons |
| Nip96Uploader | `amethyst/service/uploads/nip96/` | Skip (Blossom only) |
| UploadOrchestrator | `amethyst/service/uploads/` | Extract to commons |
| MultiOrchestrator | `amethyst/service/uploads/` | Extract to commons |
| MediaUploadResult | `amethyst/service/uploads/` | Extract (already platform-agnostic) |
| MediaCompressor | `amethyst/service/uploads/` | Desktop equivalent (Java ImageIO) |
| BlurhashMetadataCalculator | `amethyst/service/uploads/` | Desktop equivalent (Java ImageIO + commons blurhash) |
| BlossomServerResolver | `amethyst/service/uploads/blossom/bud10/` | Extract to commons |
| ServerHeadCache | `amethyst/service/uploads/blossom/bud10/` | Extract to commons |
| ImageLoaderSetup | `amethyst/service/images/` | Desktop Coil3 config |
| BlossomFetcher | `amethyst/service/images/` | Extract to commons (Coil3 fetcher for blossom: URIs) |
| BlurHashFetcher | `amethyst/service/images/` | Extract to commons (Coil3 fetcher for blurhash) |
| Base64Fetcher | `amethyst/service/images/` | Extract to commons |
| ZoomableContentView | `amethyst/ui/components/` | Desktop lightbox equivalent |
| ZoomableContentDialog | `amethyst/ui/components/` | Desktop lightbox dialog |
| ImageGallery | `amethyst/ui/components/` | Extract carousel to commons |
| MyAsyncImage | `amethyst/ui/components/` | Desktop equivalent |
| VideoView/VideoViewInner | `amethyst/service/playback/` | Desktop video player (VLCJ) |
| ExoPlayerPool/Builder | `amethyst/service/playback/` | Desktop player pool equivalent |
| MediaAspectRatioCache | `amethyst/model/` | Extract to commons |
| NewMediaView/Model | `amethyst/ui/actions/` | Desktop media post composer |
| MediaUploadTracker | `amethyst/ui/actions/uploads/` | Extract to commons |
| SelectFromGallery | `amethyst/ui/actions/uploads/` | Desktop file picker |
| ShowImageUploadItem | `amethyst/ui/actions/uploads/` | Extract upload preview to commons |
| ChatFileSender/Uploader | `amethyst/ui/screen/chats/` | Desktop encrypted upload |
| BlossomServersViewModel | `amethyst/ui/actions/mediaServers/` | Extract to commons |
| GalleryThumb | `amethyst/ui/screen/profile/gallery/` | Desktop gallery grid |
| PictureDisplay | `amethyst/ui/note/types/` | Extract to commons |
| VideoDisplay | `amethyst/ui/note/types/` | Desktop video renderer |
| VoiceTrack | `amethyst/ui/note/types/` | Desktop audio player |

### Desktop-Specific (new code)

| Component | Location | Notes |
|-----------|----------|-------|
| DesktopImageLoader setup | `desktopApp/` or `commons/jvmMain/` | Coil3 disk cache config for desktop |
| DesktopVideoPlayer | `desktopApp/` | VLCJ wrapper composable |
| DesktopFilePicker | `desktopApp/` | JFileChooser / AWT FileDialog |
| DragDropHandler | `desktopApp/` | Compose Desktop DnD API |
| ClipboardPasteHandler | `desktopApp/` | AWT clipboard image reading |
| DesktopMediaCompressor | `commons/jvmMain/` | Java ImageIO-based compression |
| DesktopBlurhashCalculator | `commons/jvmMain/` | BufferedImage → blurhash |
| DesktopExifStripper | `commons/jvmMain/` | metadata-extractor (lossless EXIF removal) |
| MediaScreen (desktop) | `desktopApp/` | Desktop media gallery/feed layout |
| UploadDialog (desktop) | `desktopApp/` | Upload progress, server selection, alt text |

## Blossom Protocol Implementation

### Upload Flow (BUD-02)

```
1. User drops/pastes/picks file
2. Client computes SHA-256 locally
3. (Optional) HEAD /upload pre-flight check (BUD-06)
4. Sign kind 24242 auth event (BUD-11) with t=upload
5. PUT /upload with binary body + Authorization header
6. Server returns BlobDescriptor {url, sha256, size, type, uploaded}
7. (Optional) PUT /media for server-side optimization (BUD-05)
8. (Optional) PUT /mirror to additional servers (BUD-04)
9. Client adds imeta tag to note event (NIP-92)
```

### Server Discovery (BUD-03)

```
1. Query user's kind 10063 event from relays
2. Parse server URLs from ["server", "https://..."] tags
3. Cache server list
4. Upload to preferred server(s)
5. When URL breaks: extract SHA-256 from URL → try other servers
```

### Auth (BUD-11)

```kotlin
// Kind 24242 event structure
BlossomAuthorizationEvent(
    content = "Upload Blob",
    tags = [
        ["t", "upload"],
        ["x", "<sha256>"],           // scope to specific blob
        ["expiration", "<timestamp>"], // required
        ["server", "cdn.example.com"]  // scope to server
    ]
)
// Sent as: Authorization: Nostr <base64(signedEvent)>
```

## NIP Coverage

| NIP | What | How We Use It |
|-----|------|---------------|
| NIP-92 | Media Attachments (imeta tag) | Attach metadata to media URLs in notes |
| NIP-94 | File Metadata (kind 1063) | File header events, metadata tags |
| NIP-B7 | Blossom Media | Server discovery, URL fallback |
| NIP-68 | Picture Events (kind 20) | Picture-first posts, profile gallery |
| NIP-71 | Video Events (kind 21/22) | Video display and metadata |
| NIP-17 | Private DMs (encrypted files) | Encrypted media in DMs |

## Desktop UX Patterns

### Drag & Drop
- Drop zone on compose area
- Visual feedback (border highlight, preview)
- Multiple files → MultiOrchestrator
- Accept: images, videos, audio files

### Clipboard Paste (Ctrl+V)
- Detect image data in clipboard
- Auto-create temp file → upload flow
- Screenshot workflow: PrtScn → paste → upload

### File Picker
- OS-native dialog (JFileChooser on desktop)
- Filter by supported media types
- Multiple file selection

### Lightbox
- Click image → full-screen overlay
- Mouse wheel zoom + pan
- Arrow keys for gallery navigation
- Esc to close
- Save to disk option

### Upload Progress
- Inline progress indicator per file
- Server selection dropdown (from kind 10063 list)
- Alt text input field
- Compression toggle
- Preview before send

## Phases

### Phase 1: Image Display Foundation
**Goal:** Images render in desktop notes with blurhash previews.

| Task | Module | Details |
|------|--------|---------|
| Desktop Coil3 ImageLoader setup | `desktopApp/` | DiskCache (OS-appropriate path), MemoryCache, OkHttp network, custom fetchers |
| Extract BlossomFetcher | `amethyst/` → `commons/` | Coil3 fetcher for `blossom:` URIs |
| Extract BlurHashFetcher | `amethyst/` → `commons/` | Coil3 fetcher for blurhash placeholder rendering |
| Extract Base64Fetcher | `amethyst/` → `commons/` | Coil3 fetcher for base64 data URIs |
| Desktop inline image rendering | `desktopApp/` | AsyncImage in note content, aspect ratio handling |
| MediaAspectRatioCache extraction | `amethyst/` → `commons/` | LruCache for URL→aspect ratio (replace Android LruCache with common impl) |

**Deliverable:** Notes in desktop feed display inline images with blurhash placeholders.
**Verifiable:** Run desktop app, navigate to feed with image posts, images load with blue/gray previews → full images.

### Phase 2: Blossom Upload Protocol Extraction
**Goal:** Shared upload client in commons, usable by Android and Desktop.

| Task | Module | Details |
|------|--------|---------|
| Create `BlossomClient` | `commons/commonMain/` | HTTP PUT /upload, /mirror, /media. Takes InputStream. Returns BlobDescriptor. |
| Create `BlossomAuthHelper` | `commons/commonMain/` | Kind 24242 event creation + base64 encoding |
| Create `BlossomServerDiscovery` | `commons/commonMain/` | Kind 10063 query, server list resolution |
| Create expect/actual file operations | `commons/` | `readFileBytes`, `computeFileSha256`, `getMimeType`, `getFileSize` |
| Create expect/actual `MediaMetadataExtractor` | `commons/` | Dimensions, blurhash computation |
| Create expect/actual `MediaCompressor` | `commons/` | JPEG quality, EXIF stripping (metadata-extractor) |
| Extract `UploadOrchestrator` | `amethyst/` → `commons/` | Multi-server coordination using BlossomClient |
| Extract `MultiUploadOrchestrator` | `amethyst/` → `commons/` | Parallel file upload management |
| Extract `MediaUploadResult` | `amethyst/` → `commons/` | Already platform-agnostic |
| Migrate Android to use commons upload | `amethyst/` | Android actuals + wire up to existing UploadOrchestrator callers |
| Extract `BlossomServersViewModel` | `amethyst/` → `commons/` | Server list state management |

**Deliverable:** `./gradlew :commons:jvmTest` passes with upload unit tests. Android still works.
**Verifiable:** Android upload flow unchanged. Desktop can call BlossomClient to upload a file.

### Phase 3: Desktop Upload UX
**Goal:** Upload media from desktop via file picker, drag-drop, and clipboard paste.

| Task | Module | Details |
|------|--------|---------|
| Desktop file picker | `desktopApp/` | JFileChooser with media type filters, multi-select |
| Drag-and-drop handler | `desktopApp/` | `dragAndDropTarget` + `awtTransferable` + `javaFileListFlavor` |
| Clipboard paste handler | `desktopApp/` | AWT Toolkit clipboard, `DataFlavor.imageFlavor`, temp file creation |
| Upload dialog composable | `desktopApp/` | Progress bar, server selector (kind 10063), alt text field, compression toggle |
| Upload preview | `desktopApp/` or `commons/` | Thumbnail preview before upload |
| Wire up to compose screen | `desktopApp/` | Add media button to note composer, connect upload flow |

**Deliverable:** Desktop user can drag image → see preview → upload to Blossom → post note with imeta.
**Verifiable:** Drop file on compose area, see upload progress, note publishes with embedded image.

### Phase 4: Video Playback
**Goal:** Videos play inline in desktop notes.

| Task | Module | Details |
|------|--------|---------|
| Add VLCJ + vlc-setup plugin | `desktopApp/build.gradle.kts` | `ir.mahozad.vlc-setup`, VLCJ 4.8.x dep |
| DesktopVideoPlayer composable | `desktopApp/` | SwingPanel + EmbeddedMediaPlayerComponent |
| Video controls overlay | `desktopApp/` | Play/pause, seek bar, volume, fullscreen toggle |
| Video in note rendering | `desktopApp/` | Replace URL-only display with inline player |
| Video upload support | `desktopApp/` | Accept video files in upload flow (Phase 3) |

**Deliverable:** Video posts play inline in desktop feed.
**Verifiable:** Navigate to note with mp4/webm URL, video plays with controls.

### Phase 5: Lightbox & Gallery
**Goal:** Full-screen media viewing with zoom and gallery navigation.

| Task | Module | Details |
|------|--------|---------|
| Lightbox overlay composable | `desktopApp/` or `commons/` | Full-screen overlay, semi-transparent backdrop |
| Zoom + pan | `desktopApp/` | Mouse wheel zoom, click-drag pan (use zoomable lib or custom) |
| Gallery carousel | `commons/commonMain/` | Multi-image navigation (arrow keys + swipe indicators) |
| Save to disk | `desktopApp/` | Right-click or button → save image/video to local filesystem |
| Keyboard shortcuts | `desktopApp/` | Esc close, Left/Right navigate, +/- zoom |

**Deliverable:** Click any image → fullscreen lightbox with zoom, multi-image gallery navigation.
**Verifiable:** Click image in note, zooms to fullscreen. Arrow keys cycle images. Esc closes.

### Phase 6: Encrypted Media (DM Files)
**Goal:** Send and receive encrypted files in NIP-17 DMs.

| Task | Module | Details |
|------|--------|---------|
| Extract encryption logic | `amethyst/` → `commons/` | NostrCipher usage for file encrypt/decrypt |
| Desktop encrypted upload flow | `desktopApp/` | Pick file → encrypt → Blossom upload → send encrypted event |
| Desktop encrypted display | `desktopApp/` | Receive encrypted file event → download → decrypt → display |
| Chat file upload dialog | `desktopApp/` | Similar to Phase 3 upload dialog but in DM context |

**Deliverable:** Desktop DM users can send/receive encrypted images and files.
**Verifiable:** Send image in DM from desktop, receive on Android (and vice versa).

### Phase 7: Profile Gallery (NIP-68)
**Goal:** View and create picture-first posts (kind 20). Profile gallery tab.

| Task | Module | Details |
|------|--------|---------|
| Picture event display | `desktopApp/` | Kind 20 renderer with image-first layout |
| Profile gallery tab | `desktopApp/` | Grid of user's picture posts |
| Picture post composer | `desktopApp/` | Create kind 20 events with multiple images + imeta |
| Gallery entry events | `desktopApp/` | ProfileGalleryEntryEvent support |

**Deliverable:** Desktop profile shows gallery tab. Users can create Instagram-style picture posts.
**Verifiable:** View profile → gallery tab shows image grid. Create picture post → visible on Android.

### Phase 8: Media Server Management
**Goal:** UI for managing Blossom server list (kind 10063).

| Task | Module | Details |
|------|--------|---------|
| Server list settings screen | `desktopApp/` | View/add/remove/reorder Blossom servers |
| Server status checking | `commons/` | HEAD request to verify server availability |
| Default server selection | `desktopApp/` | Choose preferred upload server |
| Publish kind 10063 | `commons/` | Update server list on relays |

**Deliverable:** Desktop settings page to manage Blossom servers.
**Verifiable:** Add server → appears in upload dialog dropdown. Remove server → no longer used.

### Phase 9: Audio Playback
**Goal:** Play audio tracks (MP3, OGG, FLAC) in notes.

| Task | Module | Details |
|------|--------|---------|
| Audio player composable | `desktopApp/` | VLCJ audio-only mode (no video surface needed) |
| Waveform visualization | `desktopApp/` | Optional: visual waveform for voice messages |
| Audio in note rendering | `desktopApp/` | Play/pause button + progress bar inline |

**Deliverable:** Audio files play inline in notes.
**Verifiable:** Note with MP3 URL shows audio player, plays on click.

### Phase Dependency Graph

```
Phase 1 (Images) ─────┬──→ Phase 5 (Lightbox)
                       │
Phase 2 (Upload) ──────┼──→ Phase 3 (Desktop UX) ──→ Phase 6 (Encrypted)
                       │
                       ├──→ Phase 7 (Gallery)
                       │
                       └──→ Phase 8 (Server Mgmt)

Phase 4 (Video) ────────────→ Phase 9 (Audio)

Independent: Phase 1, 2, 4 can run in parallel
```

## Assumptions

1. **Coil3 JVM/Desktop is production-ready** — Coil3 3.x advertises Compose Multiplatform support. Verify actual JVM desktop stability before committing.
2. **VLCJ + Compose SwingPanel works** — SwingPanel embeds Swing components in Compose. VLCJ renders to a Canvas/Panel. Need spike to confirm smooth integration (no flickering, proper resizing).
3. **OkHttp in commons is fine** — BlossomUploader uses OkHttp for HTTP. Both Android and Desktop are JVM, so OkHttp works in `commons/jvmAndroid/` or `commons/commonMain/` (OkHttp has KMP support). If iOS is ever targeted, this becomes an issue.
4. **Extracting to commons won't break Android** — Moving upload code from `amethyst/` to `commons/` requires updating Android imports. Must ensure Android's Koin DI and lifecycle wiring still works.
5. **libvlc can be bundled per-platform** — Compose Desktop packaging plugin supports native lib bundling. Need to verify for macOS (dylib), Linux (.so), Windows (.dll).

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| VLCJ SwingPanel flicker | Video unusable | Spike test early (Phase 4 is independent) |
| Commons extraction breaks Android | Regression | Run Android build after each extraction |
| Coil3 JVM disk cache bugs | Missing images | Fall back to OkHttp manual caching |
| libvlc bundle size (~100MB) | Large app | Consider optional download or separate installer |
| Scope creep (15 features) | Never ships | Phases exist for a reason — ship Phase 1-3 first |

## Resolved Questions

1. **Video player** — VLCJ with bundled libvlc. Ship libvlc with the app (~100MB) for zero user setup. Full format support (mp4, webm, m3u8, mkv, etc.).

2. **EXIF stripping** — Use Drew Noakes' `metadata-extractor` library. Surgically strip EXIF while preserving image quality (no re-encoding loss).

3. **Upload concurrency** — Higher parallelism than Android by default (desktop has more resources). Upload to multiple Blossom servers simultaneously. Make concurrent upload count user-configurable in settings.

4. **Coil3 disk cache** — Use OS-appropriate paths: macOS `~/Library/Caches/AmethystDesktop`, Linux `$XDG_CACHE_HOME/AmethystDesktop` (default `~/.cache/`), Windows `%LOCALAPPDATA%/AmethystDesktop/cache`. Use `maxSizeBytes(1GB)` not `maxSizePercent` (that needs Android Context).

5. **Compose Desktop DnD** — `Modifier.dragAndDropTarget` is experimental (`@ExperimentalFoundationApi`) in 1.7.x. Uses `event.awtTransferable` + `DataFlavor.javaFileListFlavor` on desktop. Old `onExternalDrag` deprecated, removed in 1.8.0. API works but expect minor changes.

6. **Media compression** — JPEG: `ImageWriteParam.compressionQuality` (0.0-1.0). PNG: lossless deflate level. WebP: use `org.sejda.imageio:webp-imageio` for lossy/lossless write (~3MB native libs per platform). Start with JPEG/PNG re-encoding, add WebP output later.

7. **libvlc bundling** — Use `ir.mahozad.vlc-setup` Gradle plugin. Downloads and bundles libvlc per platform. Targets VLC 3.x + VLCJ 4.8.x. Compose Desktop integration via `SwingPanel`.

## Open Questions

1. **vlc-setup Apple Silicon** — Does the vlc-setup plugin support arm64 macOS, or only x86_64? Need to verify.
2. **Compose DnD macOS quirks** — Does `awtTransferable` properly deliver file URIs on macOS, or are there Finder-specific issues?
