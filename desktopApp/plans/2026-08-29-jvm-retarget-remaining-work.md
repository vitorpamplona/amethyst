# Finishing the JVM retarget: what the last 150 files actually need

**Date:** 2026-08-29 · **Owning module:** `desktopApp` · **Status:** plan

`./gradlew -Pamethyst.jvmReadiness :amethystJvmProbe:jvmReadiness` reports
**2149 of 2299 files compiling clean (93%)**, 150 files and 1353 errors left.

The headline number stops being useful here. The first 2149 files came from
writing stand-ins; almost none of the last 150 do. Sorting them by *what they
actually need* gives five groups, and only two of them are "write a desktop
implementation of the same thing".

| group | files | errors | what it is |
|---|---:|---:|---|
| A. WebView / soft-keyboard bridge | 14 | 214 | Android workaround, no desktop counterpart *needed* |
| B. osmdroid map | 4 | 83 | native view hosted in `AndroidView` |
| C. LightCompressor video pipeline | 15 | 214 | Android AAR; the JVM backend is already in `:desktopApp` |
| D. Health Connect | 2 | 58 | no desktop equivalent |
| E. NIP-55 external signer | 8 | 59 | no desktop equivalent (NIP-46 is the desktop path) |
| F. UnifiedPush | 2 | 24 | no desktop equivalent |
| G. coil3 Android-only decoders | 15 | 153 | GIF + video-frame decoding; JVM backend already in `:desktopApp` |
| H. Android View / window plumbing | 16 | 158 | mostly Android workarounds |
| I. Long tail | 74 | 390 | ~5 errors each, one more small seam apiece |

## A + H — the ones that must NOT be ported (30 files, 372 errors)

The single biggest file left is `RemoteImeView.kt` (100 errors). It is a custom
Android `View` implementing `onCreateInputConnection`/`EditorInfo`, and it
exists **only** because Android cannot raise the soft keyboard for a WebView
living in another process. A desktop window has a hardware keyboard and Compose
handles text input directly, so the correct desktop counterpart is *nothing* —
porting it would be building a workaround for a problem the platform does not
have.

The same is true of most of H: `SetDialogToEdgeToEdge` and `WindowUtils` inset a
dialog under the system bars; `NotificationServiceTileService` is a Quick
Settings tile; the PiP files dock an Activity into a system overlay; the
`StrictMode`/`Choreographer` logging measures Android frame scheduling. None of
these have desktop meaning.

**Approach:** these belong behind the same seam the probe already uses for the
ExoPlayer engine and the WebRTC call stack — an interface in shared code with a
platform implementation on each side, and the Android file excluded from the
JVM compile as *the Android implementation* rather than as a failure.

**The rule that keeps this honest**, because otherwise the exclusion list
becomes a way to make the number go up: a file may be excluded only when it is
behind a named seam that either has a desktop implementation or carries a
`PlatformGaps.declareUnavailable`. The gate should print the exclusions with
their seam, and fail if one has neither. Without that, "93%" starts meaning
"93% of what we chose to count".

## D + E + F — no desktop equivalent (12 files, 141 errors)

Documentation, not code. Health Connect is an Android system service holding
on-device health records. UnifiedPush is a push *distributor* — an app running
on desktop holds its own relay connections, so the feature has no job to do.
NIP-55 is Android's intent-based external signer; the desktop answer already
exists and already works, and it is NIP-46 remote signing.

**Approach:** each gets a `DesktopCapabilities` entry and the UI asks
`PlatformGaps.isUnavailable` before drawing the control, per the three-state
model in `2026-08-29-desktop-platform-capabilities.md`. The files themselves go
behind the same seam as group A.

## C + G — the best value left (30 files, 367 errors)

These are the two places where the desktop backend **already exists in this
repository** and is simply not wired up.

`:desktopApp` already carries JCodec (BSD-2-Clause) and a bundled LGPL FFmpeg
driven through Jaffree, and already has a `VideoThumbnailCache`. LightCompressor
is an Android AAR around `MediaCodec` and cannot be used off Android — but
nothing needs it to be. Likewise coil3's `coil-gif` and `coil-video` are
Android-only publications (their Gradle modules carry only
`releaseVariant*Publication`; there is no JVM variant to add), and both do work
the JVM can do: ImageIO reads GIF frames, and video thumbnails are what
`VideoThumbnailCache` already produces.

**Approach:** the same SPI pattern the port already uses eight times over —
`VideoEngine`, `MediaPlayer.Backend`, `MediaMetadataRetriever.Extractor`,
`Toast.Presenter`. Define `VideoCompressor` and a coil3 `Decoder` seam in shared
code, implement them in `:desktopApp` over JCodec/FFmpeg and ImageIO, and let
the Android build keep LightCompressor and coil-gif behind the same interface.
No new dependency, no licensing question, and the Android side does not change
behaviour.

This is where I would start: 30 files, ~370 errors, and it makes uploads and
image loading work rather than compile.

## B — the map, and the QR scanner (5 files, ~103 errors)

Compose Desktop has no `AndroidView`, so a hosted native view has to be
re-drawn. Both of these are more tractable than they look:

- **Map.** osmdroid's tile fetching is HTTP plus a disk cache — shareable as-is.
  What is Android-only is the `MapView` that paints them. A Compose `Canvas`
  that draws the same tiles with pan/zoom is a real piece of work but a
  self-contained one, and it would serve Android too.
- **QR scanner.** The app uses `journeyapps:zxing-android-embedded` (an AAR).
  **`com.google.zxing:core` is pure Java and Apache-2.0** (verified from its
  POM), so a desktop scanner is webcam capture plus the same decoder.

## I — the long tail (74 files, 390 errors)

Averages five errors a file, and most are one more small seam. Worth doing in
clusters, because several repeat across files:

- `IntentCompat` + `Consumer` + `addOnNewIntentListener` — 5 files at once. This
  is deep-link and share-intent delivery; the desktop counterpart is the app's
  own URL-scheme handler.
- `CalendarContract` — writing an `.ics` and opening it, which `IcsShare`
  already does.
- `UiModeManager`, `WindowCompat`, `navigationBarColor` — theme and system bars.
- `KeyProperties` / `KeyGenParameterSpec` — Android Keystore. Desktop already
  stores keys through `java-keyring`, so this routes to an implementation that
  exists rather than a new one.
- `OpenableColumns`, `ConfigurationCompat`, `Animatable`, `BitmapDrawable`.
- **LaTeX rendering** (`ui/components/LatexEquation.kt`) uses
  `ru.noties:jlatexmath-android`. The upstream `org.scilab.forge:jlatexmath` is
  pure Java and renders to `Graphics2D` — but it is **GPLv2 with the Classpath
  Exception**, which under this repo's dependency rule is a **WARN**: linkable,
  our own code stays MIT, and it must be called out in the PR. (The Android
  wrapper already in use derives from the same upstream, so this is not a new
  obligation — but it should be stated rather than inherited silently.)

## The thing the gate does not measure

93% compiling is not 0% running. Every number in this document comes from a
compiler, and a compiler cannot tell you that the app boots, that resources
resolve at runtime, that DI comes up, or that a screen draws.

The next milestone worth more than the percentage is a **desktop entry point
that actually starts the Android UI** — even one screen. That will surface a
class of problem no amount of stub-writing finds: `Amethyst.instance`
initialisation order, the resource table under a real locale, Compose Desktop's
window vs. the Activity assumptions, and every stub whose signature is right and
whose behaviour is not. Some of the stubs written so far are certainly wrong in
ways only running will show.

Suggested order: **C + G** (real features, backend already present) →
**a bootable desktop entry point** (finds what the gate cannot) → **A + H + D +
E + F** behind seams (mechanical, and shrinks the number honestly) → **I** in
clusters → **B** last, as the largest genuinely new UI work.
