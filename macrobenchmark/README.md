# `:macrobenchmark` — feed rendering measurement rig

Measures what a feed scroll actually costs, on a real device, per sub-component.
Built to study `NoteCompose`; kept because the traps below cost more time to find
than the code did.

## Running

```bash
ANDROID_SERIAL=<serial> ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
    -Pandroid.testInstrumentationRunnerArguments.class=\
com.vitorpamplona.amethyst.macrobenchmark.FeedScrollBenchmark#scrollAttribution
```

`leaveApksInstalledAfterRun` is **mandatory**. Without it AGP uninstalls the app when
the run finishes, deleting its data — the logged-in account included. The next run then
finds a login screen instead of a feed, having silently destroyed the state that made
the previous run possible.

Tests: `scrollLive`, `scrollQuiet` (network cut once the feed fills), `scrollAttribution`
(per-sub-component composition + draw), `scrollPhases` (frame phases and main-thread
costs — `postAndWait`, `Compose:onForgotten`, `AndroidOwner:onTouch`, …).

## Methodology, learned the hard way

**A tight within-arm spread proves nothing about a between-arm delta.** The single most
expensive mistake here. A result with a 0.9% baseline spread was still an artifact,
because the two arms ran at different times against a *live* feed and rendered different
notes. Always interleave arms (A,B,A,B) and always serve a frozen corpus —
`tools/feed-bench-corpus/setup.sh`.

**Check the controls before the result.** Every ablation should leave some section it
cannot possibly affect. If those move, the run is drift and the delta is unreadable.

**Verify the change is actually active.** A `CompositionLocal` that resolves to null, or
a flag applied to the wrong one of six same-named `FeedLoaded` functions, makes a fix a
silent no-op that looks exactly like "no effect". Emit a trace section from inside the
new code path and assert a non-zero count.

**Per-composition section metrics need a synthetic corpus; anything involving engagement
counts needs a real one.** Real notes have variable heights (images, `Show More`,
reposts), so a fixed-distance scroll composes a different number of cards each run —
section metrics swing 8–32% while frame metrics stay within 0.1%. Judge on frame metrics
unless composition counts are identical across runs.

**Don't touch the device while a run is in flight.** Driving the UI mid-run killed one
arm outright and corrupted another.

## What the numbers said (SM-T220, for orientation)

- The main thread is blocked in `postAndWait` on the RenderThread for roughly **two
  thirds of every frame**, and stayed within ~2% of that through six different
  ablations. Card composition is ~3% of frame CPU, so composition wins have a low
  ceiling: a −41% composition change moved frame P90 by 2.3%.
- Overdraw *depth* is not the cost. The content area measured 4×+, and removing a
  full-screen fill changed frame time by nothing — while clearing it in the theme made
  the window non-opaque and measured ~17% **worse** at P90.
- Still unexplained and worth a look: RenderThread `flush commands` at ~7.5 ms/frame,
  "Slow issue draw commands" on ~53% of frames, and tiny texture uploads costing
  absurdly much (a 360×17 texture at 37.6 ms).

## Release footprint — read before merging

The trace markers are gated on `BuildConfig.TRACE_NOTE_RENDER`, false outside the
`benchmark` build type, so **they never execute** in debug or release. They are **not**
stripped, however: the marker strings, `NoteRenderTrace`, `TracedComposition`,
`ProbedCollect` and a reference to `androidx.tracing.Trace` are all present in the R8'd
release DEX (verified by grepping it). Likely because `TracedComposition` is a
`@Composable inline` function the Compose plugin transforms before R8 sees it.

Runtime cost is nil; APK footprint is not. Before merging this to `main`, move the
tracer behind a source-set split (a no-op implementation for `debug`/`release`, the real
one only in `benchmark`) so release genuinely contains none of it.

## The probes are broken on purpose

`PROBE_NO_FLOW_STATE` kills every live counter, `PROBE_NO_RX_ICONS` blanks the icons,
`PROBE_NO_RX_CLICKABLE` makes buttons untappable. They exist to be measured and thrown
away. All default to false.

## Normalize `Sum` metrics by their count — always

`TraceSectionMetric(Mode.Sum)` reports the summed duration of every matching slice
in an iteration. That sum is comparable across arms **only if every iteration
renders the same number of cards.** On a corpus of real notes it does not: card
heights vary, so a fixed-distance swipe crosses a different number of cards each
run. Three arms measured here — two of them running *identical code* — reported
`NoteCardCount` of 10, 13 and 8.

The summed metrics therefore drifted 36–73% between identical arms, which is far
larger than any effect worth shipping, and made a real result invisible. Dividing
`<Section>SumSumMs` by `<Section>SumCount` removes the denominator:

```bash
python3 macrobenchmark/tools/normalize.py path/to/*.json
```

In the run that motivated this, that single step took `DrawAuthor` from an
unreadable 41.8% apparent swing to a **4.6% drift floor with a clear +74% effect**.
Read the per-occurrence table, never the raw sums.

## Warm the image cache before measuring

Freezing the *events* (`tools/feed-bench-corpus/setup.sh`) is not enough. Real
notes carry remote image URLs that resolve asynchronously, so card heights keep
moving until they land. The benchmark now scrolls the whole corpus once, with the
network still up, before cutting the radios — see `WARMUP_SCROLLS`.

## Analysing a trace

`tools/rt_analyze.sh <trace.perfetto-trace>` decomposes a trace with Perfetto's
`trace_processor`: RenderThread and main-thread **self**-time by slice (summing by
name across depths double-counts), texture/atlas uploads, and the children of the
`animation` slice. Prefer this to parsing `atrace` text — text parsing is what
previously credited macrobenchmark's own `reportMetricsWithPresentTime` to the app.

## Use the uniform corpus (`tools/feed-bench-corpus/seed-uniform.sh`)

The real-event corpus cannot give comparable arms. The app persists no events, so every
launch re-downloads and renders a *different subset* in a different order; two cold
launches shared only half their visible notes, and a longer settle made it worse. Combined
with notes of wildly different heights (only 28 of 105 real notes are text-only, spanning
11-510 chars), a fixed-distance scroll crosses a different number of cards every arm.

`seed-uniform.sh` serves notes that are all the *same height*, so the scroll crosses the
same number of cards no matter which notes loaded. Measured effect on two arms of
identical code:

| | real corpus | uniform corpus |
|---|---|---|
| NoteCardSumCount | 14 vs 17 | **18 vs 18** |
| frameDurationCpuMs P90 drift | 10.1% | **1.4%** |
| frameOverrunMs P90 drift | 20.0% | **2.8%** |

Requirements, each learned the hard way — see the script's comments:

- Bodies must be unique but the same length; byte-identical content is collapsed by the
  app's duplicate/spam filter (60 identical notes rendered as ONE card).
- Every note needs nonzero reactions, or the animation under test never constructs.
- No kind-6 reposts: they are their own card shape and, without the reposted event inline
  (NIP-18), render "Event is loading or can't be found in your relay list".
- All authors share one `picture` URL. Warm it once with the network up, then run offline:
  one cached bitmap, identical avatar path per card, and the real image path still runs.

Run the app **offline** (the benchmark cuts the radios before launch; the corpus arrives
over `adb reverse`, which is USB and unaffected), and set the bench account's Tor engine
to **Off** or a blocking "Tor isn't connecting" dialog swallows every swipe.
