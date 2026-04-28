# Image Loading

Amethyst uses **Coil 3.x (KMP)** for async image loading on both Android and Desktop. Coil is configured once at app startup with custom fetchers, decoders, and an OkHttp/Ktor network layer wired to Tor/proxy settings.

## Android Setup

Under `amethyst/src/main/java/com/vitorpamplona/amethyst/service/images/`:

- **`ImageLoaderSetup.kt`** — `class ImageLoaderSetup` is the entry point. Called from `Amethyst.onCreate()`. Installs a global Coil `ImageLoader` with custom fetchers/decoders and a shared `OkHttpClient` (via `OkHttpFactory`).
- **`ImageCacheFactory.kt`** — builds the disk + memory cache backing the loader. Size-bounded; cleared on memory pressure.
- **`ThumbnailDiskCache.kt`** — separate on-disk cache for generated thumbnails.
- **`Base64Fetcher.kt`** — resolves `data:image/...;base64,...` URLs into bitmaps inline.
- **`BlossomFetcher.kt`** — fetches blossom-hosted media using authenticated requests (NIP-96 / blossom auth event).
- **`BlurHashFetcher.kt`** / **`ThumbHashFetcher.kt`** — synthesize placeholders from `blurhash` / `thumbhash` in NIP-92 `imeta` tags while the real image loads.
- **`ProfilePictureFetcher.kt`** — special-case fetcher that falls back to a robohash when a profile has no `picture`.
- **`MyDebugLogger`** (inside `ImageLoaderSetup.kt`) — surfaces loader errors to logcat in debug builds.

## Desktop Setup

Mirror under `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/service/images/`:

- **`DesktopImageLoaderSetup.kt`** — same shape as Android, but uses Skia decoders and a JVM-native OkHttp client.
- **`DesktopBase64Fetcher.kt`**, **`DesktopBlurHashFetcher.kt`**, **`DesktopThumbHashFetcher.kt`**, **`SkiaGifDecoder.kt`** — Skia / JVM equivalents of the Android fetchers.
- Called from `desktopApp/.../desktop/Main.kt` (`fun main()` at L172) via `DesktopImageLoaderSetup.setup()` before `application { }`.

## Composable Entry Points (Android)

- **`amethyst/.../ui/components/MyAsyncImage.kt`** — `@Composable fun MyAsyncImage(...)`. Wraps Coil's `AsyncImage` with the project's error fallbacks, blurhash placeholders, and content-description defaults.
- **`amethyst/.../ui/components/RobohashAsyncImage.kt`** — auto-generates a deterministic robohash avatar from a pubkey when no profile picture is set.
- **`amethyst/.../ui/components/ImageGallery.kt`** — paged/zoomable gallery for notes with multiple images.

Desktop has parallel composables under `desktopApp/.../ui/` — they consume the shared Coil `ImageLoader` configured by `DesktopImageLoaderSetup`.

## Typical Reuse

```kotlin
MyAsyncImage(
    model = url,
    contentDescription = description,
    modifier = Modifier.size(48.dp).clip(CircleShape),
    placeholderBlurHash = imeta?.blurhash,    // NIP-92 placeholder
    loading = { /* shimmer */ },
    error = { /* broken image */ },
)
```

For profile pictures, prefer `RobohashAsyncImage` so users without a `picture` field still get a stable avatar.

## Gotchas

- **Never call `ImageLoader.Builder` in a composable** — build once in `ImageLoaderSetup` and rely on `SingletonImageLoader`. Otherwise you shred the cache and blow up memory.
- **Blossom/encrypted media must go through `BlossomFetcher`** — using the default HTTP fetcher returns ciphertext that decoders reject.
- **Debug logcat noise**: `MyDebugLogger` is on in debug builds only; do not enable in release.
- **`OkHttpFactory` feeds the loader** — if you replace the HTTP client (e.g. to route through Tor), update the factory, not the loader setup, or Tor routing silently bypasses images.
- **NIP-92 `imeta` metadata flows in from the event, not the URL.** See `compose-expert/references/rich-text-parsing.md` for how `MediaUrlImage`/`MediaUrlVideo` carry the blurhash and dimensions into the composable.

## Related

- `compose-expert/references/rich-text-parsing.md` — how `MediaContentModels` feed composables.
- `gradle-expert/references/version-catalog-guide.md` — Coil version alignment (`coil = 3.4.0` in `libs.versions.toml`).
