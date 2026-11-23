# Desktop scroll benchmark

Status: built. The benchmark, its script, the first measurements, the image-prefetch fix it found, the AOT cache trained on scrolling, and the 1:1 image drawing that brought warm scrolling to ~60 fps in software.

## What it measures

`-Damethyst.scrollBench=<dir>` makes the app benchmark its own home feed
(`desktopApp/.../service/bench/ScrollBenchmark.kt`). It waits 30 s for the feed to load, then
scrolls it with the mouse wheel at a fixed pace, 250 notches 16 ms apart, in three passes:

- `down`: into posts the app has not drawn yet. It composes new cards, decodes their images, and
  the JIT compiles the scroll path for the first time.
- `up`: back over the same posts, which shows what the caches kept.
- `down-again`: the first pass with everything warm.

The wheel events are posted to the window as AWT would deliver them, so they go through Compose's
own wheel handling and smooth scrolling, the user's path. Nothing reaches into the feed's state.
After the three passes it writes `summary.tsv`, `frames.tsv` and one screenshot per pass, then exits.

Per frame:

- **Frame gap** (`interval_*`): time between frame starts (`withFrameNanos`). The recorder awaits
  every frame, so a late frame shows as a long gap rather than as no frame. 16.7 ms is 60 Hz.
  `jank_pct` counts gaps over 25 ms (a missed vsync), `hitch_pct` gaps over 50 ms.
- **UI thread work** (`busy_*`, `ui_busy_pct`): time the AWT event dispatch thread spent in events
  between two frame starts, timed by an `EventQueue` pushed for the benchmark. Compose composes,
  lays out and draws there, so this is the desktop's counterpart of Android's
  `frameDurationCpuMs`.

Per pass:

- **GC and JIT** (`gc_ms`, `jit_ms`): from the JVM's management beans. These are the two costs a
  JVM adds over native code.
- **Images** (`img_*`): a Coil `EventListener` (`ImageLoadStats`), installed only in benchmark
  runs, counts where each image came from (memory cache, disk, network) and how long the ones that
  were not in memory took. Images load and decode off the UI thread, so a cache miss shows here, as
  an image that appears late, and not in the frame columns.

`tools/desktop-run/scroll-bench.sh` boots the app on the headless display (one warm-up boot, then
three measured ones) and prints each pass's median; `scroll_bench_summary.py <before> <after>`
compares two builds. Usage and column meanings: `tools/desktop-run/README.md`, "Scroll benchmark".

## Setup of the numbers below

The same machine as the startup plan: 4 cores, Xvfb, Skia in **software** (no GPU), a 1380x900
window, JDK 21, the dev `run` with its AppCDS archive. Logged in read-only with Vitor's npub (260
follows), feed from live relays, image and video disk caches warm (`img_net` was 0 in every pass).
Each table is the median of 3 boots after 1 warm-up boot.

## Results

### Baseline (main + the image-cache changes)

| pass | fps | gap p50 | gap p90 | gap p99 | jank % | UI busy | JIT ms | images from memory | image load p90 |
|---|---|---|---|---|---|---|---|---|---|
| down | 23.7 | 31.3 | 89.2 | 189.5 | 58.9 | 98.8% | 8,737 | 49% | 147 ms |
| up | 41.5 | 21.7 | 36.4 | 54.3 | 34.2 | 97.9% | 3,073 | 75% | 40 ms |
| down-again | 46.8 | 18.8 | 31.0 | 48.0 | 25.6 | 94.7% | 1,612 | 94% | 38 ms |

What it says:

1. **Software drawing at this size is the steady-state limit.** Warm, the UI thread is busy
   95% of the time and the feed draws ~47 fps, a quarter of its frames missing a 60 Hz vsync. That
   is Skia rasterizing in software under Xvfb. A GPU desktop draws far faster, so absolute numbers
   here are not what users see. Relative changes are what this measures. On native Wayland the app
   also draws in software (`WaylandDisplay` sets `skiko.renderApi=SOFTWARE`), so this is closer to
   those users than to X11, macOS or Windows ones.
2. **The first scroll is a JIT problem.** The cold `down` pass ran 8.7 s of JIT compilation
   (on the compiler threads, taking the other cores) inside a 4 s pass. Its first half-second has
   frames of 200–560 ms, then it settles. A native app has no such first scroll; an AOT cache
   trained on a scroll (not only a start) is the fix to try (startup plan, "Next").
3. **GC is not the problem here.** 0–64 ms of collection per pass.

### Is it sensitive? The image memory cache, strong vs weak-only

Run on the build before commit `7e7386e3` (`strongReferencesEnabled(false)`) against after it,
the same day, one after the other:

| pass | images from memory, strong | weak-only | disk reloads, strong | weak-only |
|---|---|---|---|---|
| up | 74–79% (median 75%) | 60–71% (median 63%) | 17–22 | 23–31 |
| down-again | 92–98% | 69–95% | 2–7 | 4–23 |

Scrolling back over the same posts, the strong cache drew a quarter more of them from memory, with
ranges that don't overlap across boots. The frame columns showed **no** consistent difference:
`down`'s fps swung 15–26 on both builds, and the warm passes moved within ±5%. So:

- The image columns detect a caching change; the frame columns do not, and shouldn't be expected
  to (decoding runs off the UI thread).
- The `down` pass is too noisy (JIT, relays still answering) for frame comparisons; compare `up`
  and `down-again`, and expect only differences over ~10% to show with 3 boots.

### The images that came from disk on the way back up

The image log (`images.tsv`: every load of the run with its pass, source, decoded size, bytes,
cache key, requested size and requester) and `memory-cache.tsv` (the cache's fill after each pass)
answered why a quarter of the `up` pass's images came from disk with a strong cache:

- **Most were first loads, not misses.** Images already loaded earlier in the run came from
  memory 94–99% of the time even before any fix. The `up` pass's disk loads were mostly avatars,
  link previews and posts that had not been drawn when `down` went by (15–26 a boot), which the
  `img_mem_pct` column counts with the rest.
- **The cache never filled** (182 of 256 MB), so nothing was evicted.
- **But the feed prefetch decoded every image at its original size.** `PrefetchFeedMedia`'s
  request named no size; on the desktop Coil then decodes the original (Android's default is the
  screen). 18 prefetch decodes took **171 MiB** (one 4000x2252 photo, 36 MB), against **12 MiB**
  for the 84 images the cards drew at ~511 px. Where a card had decoded an image first, the
  prefetch's full-size request did not count its copy as big enough and decoded it again from disk:
  those were the `up` pass's genuine misses. A few 12 MP photos would fill the whole cache.

Fixed in `PrefetchFeedMedia` (shared with Android): the prefetch decodes each image to the
smallest box that still covers the card that will draw it, so its copy stays a valid hit for the
card. A single image fits the list's viewport. A note with several images is drawn as a gallery of
cropped cells, so it covers a square as wide as the list. A grid covers its cell. It skips an image
the memory cache already holds only when that copy is as big as the box (or is the original): a
smaller copy drawn elsewhere, such as the notification panel, would leave the card to decode on
screen. It warms again when the list grows a quarter past the size it warmed for, and a window
resize alone no longer re-runs the warm-up on every frame.

| per boot | before (3 boots) | after (5 boots) |
|---|---|---|
| full-size decodes | 170–172 MiB | 0–2 MiB |
| memory cache after the run | ~180 MB | 30–34 MB |
| prefetch decodes | 18, 171 MiB | 15–18, 16–19 MiB |
| re-visits from memory | 94–99% | 96–99% |

The re-visits left on disk are avatars (25–55 px) drawn at two sizes. Frame timing did not move
beyond its noise.

## The AOT cache, trained on scrolling

The release's trained AOT cache (tarball, AppImage, Flatpak; `trainReleaseAotCache`) recorded a
start that loaded the home feed and exited. On JDK 25 the cache keeps the classes that run loaded
and linked and its method profiles (JEP 515), so the JIT compiles hot methods early; but the
scroll path ran in no training run. `trainReleaseAotCache` now runs this benchmark while it
records: the feed loads for `aotTrainingSeconds`, then the benchmark scrolls it and exits the app,
whose JVM then writes the profile (`-Pamethyst.aotTrainingScroll=false` trains the start alone).
It scrolls with `-Damethyst.scrollBench.measure=false`: the same passes without the timing event
queue, image listener, frame recorder or screenshots, so the cache doesn't load the benchmark's
classes at every start or profile its receivers at hot call sites.

Measured on the packaged release image (JBR 25.0.4.1, same container as above), three images
built from one `createReleaseDistributable`, the images taking turns over two rounds, 4 boots each
after a warm-up boot, the AOT caches required to map (`-XX:AOTMode=on`;
`scroll-bench.sh --app <image> -- -XX:AOTMode=on`):

| pass | no AOT cache (per-user archive, the installers) | AOT cache, start only | AOT cache, start + scroll |
|---|---|---|---|
| down: fps | 25.9 | 31.7 | **36.5** |
| down: gap p90 / p99 ms | 71.6 / 117.6 | 56.0 / 90.8 | **46.2 / 77.9** |
| down: frames over 50 ms | 27.1% | 14.1% | **7.8%** |
| up: fps, gap p99 | 42.0, 58.9 | 43.0, 61.4 | **52.2, 37.8** |
| down-again: fps, gap p99 | 48.8, 44.2 | 48.8, 43.5 | **52.8, 38.4** |
| JIT ms in down / up / down-again | 8,509 / 2,550 / 1,507 | 4,203 / 1,783 / 1,328 | 7,716 / 1,022 / 664 |
| cache size | — | 182 MB | 198 MB |

Per boot, the scroll-trained image's slowest first pass (34.5 fps, p99 81 ms) is level with the
start-only image's best (34.8 fps, p99 81 ms). Scrolling back up, its boots ran 48–54 fps against
34–49; the warm `down-again` pass overlaps (48–54 against 43–50). The profiles move JIT work forward: the first pass
compiles more (7.7 s against 4.2 s), on the compiler threads, and every pass after it compiles
less and draws faster, because the code it runs was compiled with the training's profile instead
of after a slow warm-up. The cost is 16 MB of cache and ~30 s more of the release's training run.

The table's scroll cache was trained with the measuring on. Retrained without it (what ships),
on a rebuilt image, start-only against start + scroll, 3 and 4 boots (one start-only boot lost its
X connection before the app ran, a harness hiccup the next boot didn't repeat):

| pass | AOT cache, start only | AOT cache, start + scroll (no measuring) |
|---|---|---|
| down: fps, gap p90 / p99 ms | 32.8, 54.1 / 113.3 | **37.0, 46.6 / 71.0** |
| up: fps, gap p99 | 48.8, 62.6 | **55.1, 39.8** |
| down-again: fps, gap p99 | 46.0, 54.2 | **54.5, 38.5** |
| JIT ms in down / up / down-again | 4,992 / 1,657 / 1,090 | 6,076 / 724 / 606 |
| cache size | 174 MB | 193 MB |

Trained and measured on the same benchmark: the profile fits the feed's scroll path, which is what
users scroll, but other screens gain only what the start already covered.

### The release run itself

Release run 478 (9 Nov), the only one with a trained cache so far, ran past the build step's
15-minute attempt on both Linux portable legs, and its arm64 training failed: the app couldn't
load `libskiko-linux-arm64.so`, which links `libEGL.so.1`, missing from the runner. Those legs now
get 30 minutes an attempt (the job 75) and `libegl1`.

## Where a warm frame goes, and the images that took half of it

A CPU-time profile of the warm passes (JDK 25's `jdk.CPUTimeSample`, dev `run`) put ~66% of the UI
thread in Skia rasterizing the frame in software (`Canvas._nDrawPicture` and the blit), ~20% in all
of Compose (recomposition ~11%, measure and layout ~6%). The cost follows the pixels: the same image
at `-Dsun.java2d.uiScale=2` (4x the pixels) scrolled at 16.7 fps against 47.8.

Switching parts of the screen off one at a time (still composed and laid out, but drawing nothing),
2 boots each, dev `run`:

| drawn nothing | warm fps (down-again) | UI work per frame (median) |
|---|---|---|
| (base) | 51.0 | 16.2 ms |
| avatars, action rows | ~50 | no change |
| drawer + notification panel | 54.3 | ~-1 to -2 ms |
| note images and videos | 59.2 | 9.3 ms |
| all cards + drawer + panel | 59.5 | 6.3 ms |

The images were half the frame. A Skia microbenchmark (skiko 0.150.2, AVX-512 CPU) of one 450x600
photo explained it: drawn at a size other than its own it costs 5.9-7.6 ms a frame whatever the
sampling; drawn 1:1 at whole pixels 0.3-0.4 ms; 1:1 at a half-pixel offset 6.1 ms; 1:1 inside an
antialiased rounded clip 1.7 ms, aliased or rectangular 0.4 ms. Cards drew the image the feed's
prefetch decoded at the list's width (600 px) into a ~511 px slot, so every frame resampled it, and
the photo card also kept its blurhash (a few dozen pixels stretched over the slot) drawn under it.

Fixed:

- `PixelExactImage` (`commonsUI/src/skikoMain/.../service/image/`): the Skia decoders and the
  blurhash/thumbhash bridge return an image that scales one copy to its size on screen and draws
  that 1:1 at whole pixels. Copies sit in a 64 MB LRU (`ExactCopies`); a size that changes frame to
  frame draws scaled, without copies.
- The photo card drops its blurhash once a still image has loaded (an animated one keeps it).
- `cornerOnlyClip(shape)`: the photo and video cards clip with rectangles and use the antialiased
  rounded outline only inside the four corner squares (pixel-compared against the rounded clip: 12
  channel values differ, by at most 25 of 255, at the seams).
- The video's extracted thumbnail draws through `PixelExactImage` too.

The copies live only in memory: Coil's disk cache keeps the downloaded file as it was, one per URL,
and every size decodes from it (decoding runs off the UI thread; the drawing was what cost frames).
A `PixelExactImage` sees only the scale of the canvas it is recorded into, not a `graphicsLayer`
scale applied when the layer plays back, so a viewer that zooms with a layer would enlarge the copy
made for the fitted size. The image viewer (`ZoomableImage`) asks for `Size.ORIGINAL`, which the
feed's downsampled copies can't answer, and draws that decode's own bitmap
(`AsyncImagePainter.State.withFullResolution`), so its zoom shows the picture's pixels
(`PixelExactZoomTest`). Copies are capped at 4 MP, so one can't fill the copy budget alone.

| pass, 3 boots | before | after |
|---|---|---|
| up: fps, UI work per frame | 46.0, 18.5 ms | 51.0, 11.8 ms |
| down-again: fps, UI work per frame, frames over 25 ms | 51.0, 16.2 ms, 12.0% | **59.2, 10.6 ms, 5.6%** |

One of the three boots ran 49.8 fps on `down-again` (the live feed differs per boot). The cold `down`
pass is unchanged (~26 fps on the dev `run`): that one is the JIT.

### After the audit

- `PixelExactImage` applies only while Skia draws in software (`SoftwareDrawing`, set by the
  window from its renderer, and always on for the Swing-drawn Wayland window); on a GPU it draws
  scaled, as a plain bitmap. The "animating" guard is gone: making a copy costs about one scaled
  draw (8.2 ms against 7.9 ms for a 600x800 photo into 511x681), and the guard misfired when one
  image was drawn at two sizes, leaving the slow draw recorded in the card. Targets under 128x128
  draw scaled (microseconds, no copy); the copy budget counts the decoded bitmap each entry keeps
  alive; copies are keyed by the bitmap, so wrappers of the same pixels share them.
- GIFs: the blurhash goes once the first frame is in, and in software each frame is scaled to its
  drawn size on the player's thread, then drawn 1:1 (`GifPlayer.decode(frame, target)`); the image
  viewer, which zooms with a layer, keeps full-size frames.
- `mediaClip(shape)` (expect/actual; `clip` on Android) records the content once and plays it
  through the band and corner clips; it now also clips quoted notes, link previews and the
  captured video still.
- The prefetch decodes at the card's media width (`FeedMediaGutter`, reported by
  `NoteComposeLayout`), not the list's: in the run below every feed photo decoded at 511, the
  width cards draw at, and none at 600, so they draw 1:1 without a copy.
  Only lists of note cards take it (`mediaAtNoteWidth`: the default renderer of
  `RenderFeedState`/`RenderFeedContentState`, home, hashtags, profile threads). Picture and video
  cards draw media across the whole card, and a thread's main post sits 12 dp in from each side,
  both wider than a note's content column: from the one gutter they would have decoded too small,
  missed the cache and popped in. Every other list keeps decoding at its own width.

3 boots each, the committed state before the audit fixes (`HEAD`) and after, the same hour:

| pass | before | after |
|---|---|---|
| down: fps, gap p99 | 28.5, 144.7 ms | 31.8, 95.7 ms |
| up: fps | 53.5 | 55.1 |
| down-again: fps (boots), frames over 25 ms | 56.0 (52.3-58.3), 10.3% | **58.6 (58.6-59.3), 4.9%** |

## Every feed screen in the drawer

`-Damethyst.scrollBench.screen=<NavBarItem>` opens a drawer entry before the passes. One boot per
screen (37 of the drawer's feed screens), JFR's CPU-time sampler recording the warm passes, then 2
clean boots for the slow ones. Eleven screens had nothing to scroll for a read-only `npub` (Messages,
My Lists, Interest Sets, Favorite Algo Feeds, Front Page, Badges, Products, Relay Groups, Nests,
Geocaches, Web Bookmarks).

- Everywhere, Skia rasterizing in software is still most of the UI thread (45-88%); no app function
  takes more than ~3%. The image fixes reach every screen: every Coil image decodes to a
  `PixelExactImage`, and Pictures (62-63 fps), Video, Shorts, Longs, Podcasts and Articles scroll
  near 60.
- Single boots swing ±5 fps with the live feed. Confirmed slow with clean boots: **Discover** (49.2
  fps, 18 ms of UI work a frame), **Profile** (51.4, 13 ms) and **Polls** (54.0, 13 ms); Home ran
  53.7 the same hour.
- **Discover's follow-pack banners** are animated GIFs (the author's banner through `MyAsyncImage`,
  then `AnimatedGifImage`). Drawn as nothing, the screen does 10.9 ms a frame against 16.2 (49 ->
  58 fps); as still first frames, 14.9. A GIF's first frame (`data.firstFrame`, and a still GIF)
  draws through `Image(bitmap)`, resampled every frame; the playing frames, scaled on the player's
  thread, did not come out cheaper either in this `Crop` case. Switching the banner's rounded clip
  to `mediaClip` saved ~1 ms of the 18.
  Fixed: a GIF's first frame (a still GIF, and every GIF until its player, restarted each time its
  card scrolls back in, hands over a frame) draws through `PixelExactImage`; and a frame that
  overflows its box (a crop) is scaled only where it shows (`FramePlan`), so the player scales and
  Compose copies a banner-sized frame, not a 580x876 one. Discover, back to back, 2 boots each: down
  31.9 -> 39.6 fps (gap p99 269 -> 60 ms), up 42.6 -> 49.3, down-again 49.5 -> 53.0 with frames
  over 25 ms 18.9% -> 12.6%. The median frame barely moved: the fix took out the spikes.
- Large covers still clip with an antialiased rounded clip (~1.3 ms a cover in software): live
  stream covers, community covers, public-chat channel thumbs, follow-pack banners, profile gallery
  cells, music playlist covers, the podcast player.
- An idle screen still costs 6-9 ms a frame whenever a frame happens: Skia repaints the whole
  window. Anything animating on screen (a GIF, a spinner, a playing video's progress) keeps that up
  at its frame rate even when nothing scrolls.
- In this container every video falls back to Chromium (the native engine can't open them here), so
  its teardown and message loop show on the UI thread (up to ~4% on Profile); and a video Chromium
  failed on was tried again each time its card scrolled back (9 times in one run).
- Smaller and spread out: `NoteComposeLayout`'s measure (2-3%), `PixelExactImage.scaledCopy` in
  Shorts and Music Playlists (2-3%: copies made again in the warm passes, likely full-width portrait
  posters and their full-size blurhash copies outgrowing the 64 MB budget), `stringRes` (up to 1%),
  `RichTextViewer.measureSpaceWidth` (up to 0.7%), `croppedImageSize` on Software Apps (1.7%).

### Audit of the survey round

- `SoftwareDrawing` follows every open window (`report`/`forget`): on while any draws in software,
  so the fullscreen overlay, a pop-out screen or a napplet window can no longer switch the main
  window's drawing by reporting another renderer.
- One scaling helper (`ExactScaling.kt`: `scaleInto`, `scaledBitmap`, `drawAtWholePixels`) for the
  decoder, `PixelExactImage` and the GIF player; at an unchanged size it copies pixel for pixel (the
  cubic softened a GIF frame that was only cropped).
- GIFs: a frame scaled to its box is an immutable snapshot made on the player's thread (the native
  frame shared through `makeFromPixmap`, not copied) and drawn 1:1 by `ExactFramePainter`, resampled
  only while the box has changed size since; the player waits for the box's size instead of handing
  over a frame that would be resampled every draw.
- Copies made on the UI thread when an image first draws (~8 ms each): Home made 22-24 a boot. The
  prefetch now fits single images to the platform's inline height cap
  (`NotePlatform.maxInlineMediaHeight`), an image of unknown shape gets the cap in its own
  constraints, and a video's thumbnail is scaled off the UI thread before it shows
  (`PixelExactImage.prepare`). Then: 11 a boot (77 ms), placeholders and small images shown larger.
  A source over 16 MB draws scaled rather than keep the copy budget for itself.
- Discover, back to back: down-again 40.8 -> 51.0 fps (UI work 21.6 -> 17.0 ms); Home within noise.
- `tools/desktop-run/screen-survey.sh` runs the per-screen survey (`--profile` for the hotspots).

## Caching the static panes: ruled out

Software Skia rasterizes the whole window on every frame, including the drawer and the docked
notification panel, which rarely change while the feed scrolls. Cutting both out of the shell (an
ablation, not a fix) took Home's warm `busy_p50` from 10.7 to 7.3 ms. Rasterizing each pane on
its own into an offscreen surface accounts for that: the drawer costs 0.7 ms a frame and the
notification panel 2.3 ms (300x900 and 360x860 px at 1x).

The fix that suggests is a raster cache: draw the pane into a bitmap once and blit it until
something in it changes. Compose on skiko gives no signal for "something in it changed":

- A parent's recording references each child layer live (`GraphicsLayer.draw` →
  `RenderNode.drawInto`, a drawable). A child layer that re-records or changes a property (alpha,
  offset, a scroll) shows up at playback without re-running any ancestor's draw. In an
  `ImageComposeScene` spike, a pane's draw block re-ran when a text outside a child layer changed,
  but not when a child layer's alpha changed or a `LazyColumn` inside it scrolled.
- The real drawer records 25 nested layers (its scroll container, each row, the header images),
  and over a session of hovering and scrolling its own draw ran once. A cache keyed on that draw
  would keep showing stale hover states, ripples and avatars.
- The children are tracked only in internal fields (`childDependenciesTracker`), and a leaf layer's
  re-record leaves no trace there, so even reflection can't tell when to refresh.

Caching only panes whose recording holds no sublayers would be correct, but neither pane qualifies,
and the notification panel is a live feed that would invalidate it anyway. A correct pane cache
needs damage tracking inside skiko's `RenderNode` (an upstream change); until then, cost
per card (which the main feed pays many times over) is the lever.

## Where Skia's time goes, and the backgrounds painted twice

A native profile of the UI thread during a Home scroll (`perf record -e cpu-clock`, with
`--call-graph dwarf` because skiko is built without frame pointers; `libskiko` is not stripped)
splits it about evenly between Java (composition, layout, recording) and Skia. On the Skia side,
in share of all samples:

| Work | Share |
|---|---|
| Solid fills (`rect_memset32`, from `drawRect`/`drawPaint`/`clear`) | 6.2% (about 12% of the UI thread) |
| Text (`blit_mask_d32_a8_*`, glyph masks) | 4.9% |
| Presenting the frame (`XPutImage`; Xvfb's path) | 5.2% |
| Images (1:1 blits and the scaled draws left) | about 2.8% |

The fills are mostly overdraw. At rest a pixel in the centre pane is filled by skiko's clear (two
on X11, one on the Wayland path), then `NavDisplay`'s background, then `CappedScreenContent`'s,
then the screen's `DisappearingScaffold` surface, all with the same theme color; the docked
notification panel was filled twice too (the shell's cross-fade backdrop and the panel's
`Surface`).

- `CappedScreenContent` now paints only while its entry's navigation transition runs
  (`LocalNavBackdrop`, provided by the host's display, which paints the same color under every
  screen). The fill still covers what is below while a screen slides, fades or scales:
  `NavigationHostBackgroundTest.aCappedScreenCoversTheOneBelowWhileItSlidesIn` fails without it.
- The notification panel and its folded strip paint no fill of their own; the shell's backdrop
  under them is the only one.

Those two fills cost 0.28 ms a frame at 1x, timed alone in Skia (0.23 ms for the centre pane,
which sits at a fractional x and so takes the antialiased path, 0.05 ms for the panel), about 2.5%
of a warm frame and four times that at 2x. The scroll benchmark can't resolve a change that small
against the live feed's noise (its A/B moved busy p50 by less than a millisecond either way).

Left in place: the scaffold's surface (a dialog opened from a screen inherits the screen's
composition locals, so it can't tell it is at rest under the display), and skiko's clears, which
happen inside the library.

## The Java half: cards composed inside scroll frames

JFR's CPU-time sampler on the UI thread (the survey's `--profile`, and a recording of the first
pass with `-XX:FlightRecorderOptions:stackdepth=512`, since 64 frames cut composition stacks off
before the frames that classify them) splits Home this way:

| Pass | raster | compose | layout | record | input | other |
|---|---|---|---|---|---|---|
| warm (down again) | 59% | 14% (+ most of "other") | 6% | 7% | 3% | 12% |
| first pass down | 36% | 33% | 5% | 9% | 7% | 10% |

The warm passes already run at about 60 fps here (busy p50 9 to 10 ms); the first pass down is the
slow one (25 to 47 fps a boot). There, each wheel frame scrolls the `LazyColumn`, which remeasures
and composes every card scrolling in for the first time inside that frame (`subcompose` →
`composeInitial`: 13.6% of the UI thread). Only about 8 points of the compose share are the
card's own composables (`NoteCompose` and below); the rest is Compose's machinery around them.

Compose for desktop never prefetches: the scheduler the desktop scene provides,
`NoOpPlatformPrefetchScheduler`, drops every request, so the list can never compose a card
ahead of the frame that shows it, as it does on Android. `ProvideFramePrefetch`
(`desktopApp/.../platform/FramePrefetchScheduler.kt`, installed by `AppWindow` for every window)
provides one through `LocalPlatformPrefetchScheduler` (an `InternalComposeUiApi`): after each frame
has drawn, it runs the requests, high priority first, in the time left before the next frame is
due. When eight frames in a row finish no request (no frame leaves idle time, or the items
take longer to compose than the gaps), it drops its queue and stops asking for frames, so a slow
window is never kept drawing just to prefetch. `FramePrefetchSchedulerTest` checks that the default composes nothing ahead (if a Compose
update starts to, this scheduler can go) and that this one composes the next row.

Unlike Android's scheduler it runs nothing after a frame that is already late. A first version that
let one request run after a late frame, as Android does, made the first pass worse (38 to 32 fps
over 3 boots): most of its frames are over budget, so it composed a card after each one and pushed
the next frame back. Idle time only, over 4 boots each:

| Pass | fps before → after | jank | interval p90 |
|---|---|---|---|
| up | 54 → 60 | 10% → 4.2% | 25 → 20 ms |
| down again | 58 → 62 | 6.7% → 3.2% | 24 → 18 ms |
| first pass down | 38 → 34.5 | (boots 26–42 vs 28–45: no signal) | |

Cards that scroll back in were composed and thrown away when they scrolled out, so the up and down
again passes compose too; that work now lands between frames. The first pass rarely has idle time
to spare, and stays as it was.

Allocation samples on the UI thread were no lead: a handful of samples of Compose's own frame
dispatcher carry nearly all the weight (JFR weights each sample by everything since the thread's
last one), and GC takes 40 to 80 ms of a 4.5 s pass.

## A screen of notes composed ahead, and read only when seen

Compose 1.12's `LazyLayoutCacheWindow` keeps a range of items past the viewport composed, filled
in idle time by the prefetch scheduler. With `ProvideFramePrefetch` running those requests, a
window of one screen ahead composes the next screenful while the user reads the top of the feed,
so the first scroll down finds those cards built.

It was blocked by when a card marks itself read: `calculateBackgroundColor` called
`loadAndMarkAsRead` (which also dismisses the note's tray notification) during composition and
started the 5-second "new" highlight there. A screen composed ahead would have been marked read,
its notifications dismissed and its highlight spent before anyone saw it. Compose's own prefetch
already did that to one card on Android.

- `FirstShownItem` (`commonsUI/.../feeds/FirstShown.kt`) gives a list item a signal that completes
  on its first placement. Inside one, `calculateBackgroundColor` only checks at composition
  (`isUnread`) and marks read, dismisses and starts the highlight when the card shows. Outside one
  nothing changed.
- `rememberForeverLazyListState(cacheAhead = true)` takes the window from `LocalLazyListCacheWindow`,
  which only a platform with a working scheduler provides: Android (`MainActivity`) and desktop
  (`ProvideFramePrefetch`). The window is `FeedCacheWindow`, one screen ahead and none behind: a
  card that leaves the screen is disposed as before, so a video that scrolled away can't keep its
  last "fully shown" report and play on off screen.
- Home is the only feed that opts in so far; its notes are `FirstShownItem`s. Another feed opts in
  once everything its items do at composition that should wait for the user waits for the signal.
- `FeedCacheWindowTest` checks that rows composed ahead are not seen until they scroll in.

Home, 4 boots each, nothing else running (a first attempt was discarded: one baseline boot froze
for seconds and another overlapped a build):

| Pass | fps before → after | busy p50 | interval p90 | jank |
|---|---|---|---|---|
| first pass down | 21.1 → 31.6 | -28% | -40% | |
| up | 52.2 → 59.2 | | | -61% |
| down again | 57.3 → 62.2 | | | -80% |

First-pass boots: 21.7, 14.1, 20.5, 29.7 before, 31.5, 31.8, 51.8, 23.6 after. Android was not
measured here (no device, no KVM for an emulator). `baselineprofile`'s `FeedScrollBenchmark`
measures the same first scroll on a device (frame timing of four flings down Home after a cold
start) for an A/B there.

## Multi-second freezes: Chromium starting on the UI thread

Two benchmark boots on `main` stalled for 6.5 and 7.2 s at the start of a pass, the UI thread busy
the whole time (not GC: 75 and 0 ms). Each was the only boot in its set that started Chromium: a
feed video the native engine couldn't open fell back to `ChromiumVideoPane`, which started
`DesktopCefEngine` as soon as the card composed, and JCEF initializes on the AWT event thread
(`CefApp` runs `N_PreInitialize` and `N_Initialize` through `invokeAndWait`). Chromium's own log
lines fall inside both stalls. In 16 instrumented boots (thread dumps every 300 ms, JFR) none froze
for seconds, but a mid-pass start took 0.3 to 0.9 s of the UI thread and dropped that pass to 8.6 fps.
A card now starts Chromium only once it has been the video in view for 0.8 s (`chromiumMayStart` in
`DesktopVideoPlayer.kt`), unless Chromium already runs or the view was opened to watch the video.

Two smaller UI-thread stalls from the same investigation: Skiko's `FrameWatcher` calls `System.gc()`
every 30 s, a 117-373 ms stop-the-world full GC under G1, now concurrent with
`-XX:+ExplicitGCInvokesConcurrent`; and compose-resources string loads block the UI thread in
`runBlocking` on a cache miss (20-175 ms measured, longer while `Dispatchers.Default` is busy),
fixed below.

### Strings read once

compose-resources resolves a string the first time each `stringResource` call site composes: on
the JVM and Android a `runBlocking` on a load it starts on `Dispatchers.Default`, and a load that
misses its cache opens the locale's packed strings file and skips to the string, inflating up to
650 KB of the deflated file. Every card a feed composed paid that for each string in it.
`stringRes` now keeps every string it has read per resource environment (formatting arguments the
way the library does), and `AmethystApp` provides `CachingStringsReader`, which reads each strings
file once and slices every string from it (`StringResTest` checks both against the library). UI-thread
parks under string loads over a boot (JFR, parks over 1 ms, 2 boots each): 76 and 72 parks, 221 and
357 ms, longest 22.5 ms before; 57 and 67, 134 and 136 ms, longest 15.7 ms after. What is left is
each string's first use, about 2 ms each, which only the library could take off the UI thread.

### Who allocates

A whole-process JFR allocation profile of the Home scroll (2 boots, sites ranked by sample count,
since JFR weights a sample by everything its thread allocated since the last one and a few samples
can carry most of the weight): network reads (okio segments), event JSON (quartz `KiteReader`),
desktop video thumbnails (`VideoThumbnailCache`: frames decoded in Java by jcodec, written and read
as JPEG through ImageIO, converted pixel by pixel with `toComposeImageBitmap`), Coil's decoders,
relay subscription planning (`MapOfSetBuilder`, `toHexSet`, `CursorPlanner`, `FilterShape`, the
top-nav filters) and the trust network rebuild. The UI thread is a quarter of it, spread over
Compose's own machinery. GC runs 2 to 3 young collections a second while the feed loads, 8 to 18 ms
each. The thumbnail pipeline, which Skia could decode and encode natively, is the clearest target.
Stored posters, which every launch reads back, now decode through Skia (`decodePoster`):
1.7 ms a 640x360 poster against 6.6 ms for ImageIO plus `toComposeImageBitmap`, and no Java-heap
pixel arrays. Writing a new poster still goes through ImageIO's JPEG writer, once per video.

### Where the CPU goes, and a trust network downloaded on every launch

CPU-time samples of every thread from launch through the passes (2 boots): the UI thread 63-67%,
`Dispatchers.Default` 27-30%. The largest background cost, 10-11% of all CPU, was Schnorr
verification in `ParallelEventVerifier`, which only the trust network sync uses: 157k cards
downloaded and verified in the first 15-20 s of every boot, ahead of the passes. The network was on
disk; it was deleted at each start. A transient login (`AMETHYST_LOGIN`: every benchmark boot, agent
and test run) keeps no settings, so no backup of the kind 10040 either, and until the list came from
the relays `TrustProviderListState` resolved it as "no providers", which makes `TrustNetworkState`
delete its files; the list arrived 1.4 s later and the download began. It now stays unknown for
`LIST_WAIT_MS` (30 s) when there is neither a list nor a backup, and a list that arrives meanwhile
resolves at once (`TrustProviderListWaitTest`). A saved account resolved from its backup and was not
affected. Next after that: relay message parsing 3.5-5%, image decoding 3-4%; the outbox relay map,
rebuilt whole for every relay list that arrives, is 1.3%.

## The cache window on more feeds: held

Extending Home's window to notifications, the default note feeds, hashtag, the profile's Notes tab,
Discover's list tabs and Video (857d143a) was reverted: against `main`, 4 boots each, the profile
screen's first pass fell 26% (42.2, 54.8, 53.0, 49.0 fps to 38.3, 29.1, 51.1, 37.1) with busy-time
p90 51% worse, and Home's (with the docked notification panel opted in) was inconclusive. A first
suspect, unconfirmed: the profile's tabs sit in a pager, so more than one list composes a screen
ahead, and cards kept composed recompose as their data streams in during the first pass.

## Next

1. **Count first loads apart from misses** in the summary: `img_mem_pct` mixes them, which is what
   made the `up` pass look like a caching problem. `images.tsv` has what's needed.
2. **A fixed corpus.** The feed comes from live relays, so its posts differ between boots and
   days. Serving a recorded event set from a local relay (the `LaunchFixtureRelay` the tests use)
   would remove that noise.
3. **A GPU run.** Run it on a real Linux, macOS and Windows desktop (the property works on the dev
   `run` anywhere) for the absolute numbers, and native Wayland against XWayland with OpenGL.
4. **Train the installers too.** The `.deb`, `.rpm`, `.dmg` and `.msi` carry the per-user archive
   (classes only, no profiles), so they keep the slow first scroll the "no AOT cache" row shows.
   Training them needs their packaging rebuilt around a trained image (startup plan, "Next").
5. **HiDPI.** Measure the image fixes at 2x scale, where every pixel cost is four times larger.
6. **GPU drawing.** Run the benchmark where Skia draws on the GPU (macOS, Windows, X11) for the
   numbers users there see; the software-only paths stay off there.
7. Frame timing in CI is out of reach (shared runners, software drawing); the image columns and
   `jit_ms` are stable enough to watch there if wanted.
