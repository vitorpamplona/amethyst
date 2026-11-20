# Desktop scroll benchmark

Status: built. The benchmark, its script, the first measurements, and the image-prefetch fix it found.

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

## Next

1. **Count first loads apart from misses** in the summary: `img_mem_pct` mixes them, which is what
   made the `up` pass look like a caching problem. `images.tsv` has what's needed.
2. **A fixed corpus.** The feed comes from live relays, so its posts differ between boots and
   days. Serving a recorded event set from a local relay (the `LaunchFixtureRelay` the tests use)
   would remove that noise.
3. **A GPU run.** Run it on a real Linux, macOS and Windows desktop (the property works on the dev
   `run` anywhere) for the absolute numbers, and native Wayland against XWayland with OpenGL.
4. **An AOT cache trained on scrolling**, measured with the `down` pass's `jit_ms` and gaps.
5. Frame timing in CI is out of reach (shared runners, software drawing); the image columns and
   `jit_ms` are stable enough to watch there if wanted.
