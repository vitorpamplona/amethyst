/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame-timing of the home feed scroll — i.e. of [NoteCompose] rendering, since the feed's
 * `LazyColumn` item body is one `NoteCompose` per note.
 *
 * Two variants, because the feed's frame budget is contended by two very different things:
 *
 *  - [scrollLive] scrolls while the relay pool is still delivering. This is what a user
 *    actually experiences, but the number is a mix of rendering cost and the ingest storm
 *    (relay parse + `LocalCache` writes + the GC pressure they create) stealing CPU from
 *    the main thread.
 *  - [scrollQuiet] cuts the network *after* the feed has filled, so ingest stops and the
 *    same already-cached notes are re-rendered. That isolates the composition/layout/draw
 *    cost of the card itself.
 *
 * The gap between the two is the ingest interference; the [scrollQuiet] number is the
 * ceiling that rendering work alone can move.
 *
 * ## Running
 *
 * ```
 * ANDROID_SERIAL=<serial> ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
 *     -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
 * ```
 *
 * **The `leaveApksInstalledAfterRun` flag is mandatory, not optional.** Without it AGP
 * uninstalls the target APK when the run finishes, which deletes the app's data — the
 * logged-in account and the selected feed included. The next run then launches a
 * first-boot app, finds a login screen instead of a feed, and fails in `setupBlock`
 * (having silently destroyed the account that made the previous run possible).
 *
 * The device must already be logged in and sitting on a feed with content. A throwaway
 * account on the **Global** feed is the reproducible choice: it needs no follows, so the
 * same content type is available on every device.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class FeedScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollLive() = scrollFeed(quiet = false)

    @Test
    fun scrollQuiet() = scrollFeed(quiet = true)

    /**
     * Same quiet scroll, but reporting the per-sub-component composition time emitted by
     * `TracedComposition` in the app. Requires the `benchmark` build type (which sets
     * `TRACE_NOTE_RENDER = true`); on any other build the sections are absent and every
     * value reads zero.
     *
     * `Mode.Sum` is the number that matters: total ms spent composing that part of the
     * card across the whole scroll, which is what a code change has to move.
     */
    @Test
    fun scrollAttribution() = scrollFeed(quiet = true, metrics = frameMetrics() + sectionMetrics())

    /**
     * Splits a frame into its phases rather than its composables.
     *
     * The card's composition is only a few percent of frame CPU on a slow device, so the rest has to
     * be somewhere the `TracedComposition` markers cannot see: the measure/layout/draw traversal.
     * These sections are emitted by `ViewRootImpl` and Compose UI themselves — no app instrumentation
     * — so this works on any build.
     *
     * `Mode.Sum` per iteration is the useful figure: total ms the scroll spent in each phase.
     */
    @Test
    fun scrollPhases() = scrollFeed(quiet = true, metrics = frameMetrics() + phaseMetrics())

    private fun frameMetrics(): List<Metric> = listOf(FrameTimingMetric())

    private fun sectionMetrics(): List<Metric> =
        TRACED_SECTIONS.flatMap { section ->
            listOf(
                TraceSectionMetric(section, TraceSectionMetric.Mode.Sum, label = "${section.removePrefix("Amethyst:")}Sum"),
                TraceSectionMetric(section, TraceSectionMetric.Mode.Count, label = "${section.removePrefix("Amethyst:")}Count"),
            )
        }

    private fun phaseMetrics(): List<Metric> =
        FRAME_PHASES.flatMap { section ->
            listOf(
                TraceSectionMetric(section, TraceSectionMetric.Mode.Sum, label = "${section.replace(':', '_').replace('#', '_')}Sum"),
                TraceSectionMetric(section, TraceSectionMetric.Mode.Count, label = "${section.replace(':', '_').replace('#', '_')}Count"),
            )
        }

    private fun scrollFeed(
        quiet: Boolean,
        metrics: List<Metric> = frameMetrics(),
    ) {
        var prepared = false

        try {
            rule.measureRepeated(
                packageName = PACKAGE,
                metrics = metrics,
                // Full AOT, deliberately: this study compares *rendering* costs across code
                // changes, and JIT warm-up variance would swamp the differences being looked
                // for. Absolute numbers therefore read slightly better than a shipped build.
                compilationMode = CompilationMode.Full(),
                iterations = ITERATIONS,
                setupBlock = {
                    if (!prepared) {
                        // Cut the radios BEFORE the app launches, not after the feed loads.
                        //
                        // The corpus relay is reached over `adb reverse` (USB loopback), which
                        // is unaffected by wifi/data, so the app still ingests the fixed corpus
                        // -- and *only* the corpus. Cutting the network after the settle, as
                        // this used to, left the app free to pull live notes from ~190 real
                        // relays while the feed was being built. Those differ on every launch,
                        // which is why each arm was internally deterministic (identical card
                        // counts across all 10 iterations) yet landed on a different constant
                        // from its neighbours -- 8 vs 12 vs 13 NoteCards -- making three A/B
                        // runs unreadable.
                        if (quiet) setNetworkEnabled(false)
                        pressHome()
                        startActivityAndWait()
                        // Let relays connect and fill the feed. Everything measured afterwards
                        // re-renders notes that are already in LocalCache.
                        device.wait(Until.hasObject(By.scrollable(true)), FEED_APPEAR_TIMEOUT_MS)
                        check(device.hasObject(By.scrollable(true))) {
                            "No scrollable feed appeared — is an account logged in with a non-empty feed?"
                        }
                        Thread.sleep(INGEST_SETTLE_MS)

                        // Warm the image cache before measuring anything. A corpus of real notes
                        // carries remote image URLs, and those resolve asynchronously: until they
                        // do, card heights keep changing, so a fixed-distance scroll composes a
                        // different number of cards on every run. That alone widened section
                        // spreads from ~1-3% to 17-28% and made an A/B unreadable. Scrolling the
                        // whole corpus once, with the network still up, settles every height and
                        // fills Coil's cache; only then is it safe to cut the network.
                        repeat(WARMUP_SCROLLS) { swipeFeed(down = true) }
                        device.waitForIdle()
                        repeat(WARMUP_SCROLLS) { swipeFeed(down = false) }
                        device.waitForIdle()
                        Thread.sleep(IMAGE_SETTLE_MS)

                        prepared = true
                    }
                    // Every iteration starts from the same place in the feed, so each one
                    // renders a comparable set of cards.
                    repeat(SCROLLS) { swipeFeed(down = false) }
                    device.waitForIdle()
                },
            ) {
                repeat(SCROLLS) { swipeFeed(down = true) }
                device.waitForIdle()
            }
        } finally {
            // Restore the radios even when an iteration throws, so a failed run does not
            // leave the device offline for the next one.
            if (quiet) setNetworkEnabled(true)
        }
    }

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * Drags the feed by a fixed fraction of the screen using raw coordinates.
     *
     * Deliberately not `UiObject2.scroll()`: that resolves an accessibility node up front
     * and then throws [androidx.test.uiautomator.StaleObjectException] the moment the list
     * recycles it mid-gesture, which a scrolling feed does constantly. Raw coordinates also
     * make the gesture identical on every run and every device (same fraction of the
     * screen, same step count), which is what makes iterations comparable.
     *
     * [SWIPE_STEPS] is high on purpose: many small steps produce a steady drag rather than
     * a fling, so the measured frames are the ones that actually compose new cards instead
     * of a short burst followed by decelerating scroll.
     */
    private fun swipeFeed(down: Boolean) {
        val x = device.displayWidth / 2
        val top = (device.displayHeight * SWIPE_TOP_FRACTION).toInt()
        val bottom = (device.displayHeight * SWIPE_BOTTOM_FRACTION).toInt()
        if (down) {
            device.swipe(x, bottom, x, top, SWIPE_STEPS)
        } else {
            device.swipe(x, top, x, bottom, SWIPE_STEPS)
        }
    }

    private fun setNetworkEnabled(enabled: Boolean) {
        val state = if (enabled) "enable" else "disable"
        device.executeShellCommand("svc wifi $state")
        device.executeShellCommand("svc data $state")
        // The radios take a moment to actually go down; without this the first measured
        // iteration still sees in-flight relay traffic.
        Thread.sleep(NETWORK_TOGGLE_SETTLE_MS)
    }

    companion object {
        const val PACKAGE = "com.vitorpamplona.amethyst.benchmark"

        /** Enough iterations for a stable median without pushing the tablet into an lmkd kill. */
        const val ITERATIONS = 10

        const val SCROLLS = 4

        /** Gesture spans the middle 60% of the screen, clear of the status and nav bars. */
        const val SWIPE_TOP_FRACTION = 0.2
        const val SWIPE_BOTTOM_FRACTION = 0.8

        /** ~5 ms per step, so one swipe is a ~300 ms steady drag rather than a fling. */
        const val SWIPE_STEPS = 60

        const val FEED_APPEAR_TIMEOUT_MS = 30_000L

        /**
         * Long enough for relays to deliver a screenful of notes, short enough to stay under
         * the memory ceiling of a 3 GB SM-T220, which lmkd-kills a release build around 1.85 GB.
         */
        const val INGEST_SETTLE_MS = 20_000L

        const val NETWORK_TOGGLE_SETTLE_MS = 3_000L

        /** Enough passes to touch every card in the corpus so all images resolve. */
        const val WARMUP_SCROLLS = 24

        /** Lets the last decodes land after the warm-up gesture stops. */
        const val IMAGE_SETTLE_MS = 8_000L

        /**
         * Frame-phase sections emitted by the platform and by Compose UI itself. Nothing in the app
         * has to be instrumented for these; absent sections simply report zero.
         */
        val FRAME_PHASES =
            listOf(
                "Choreographer#doFrame",
                "traversal",
                "measure",
                "layout",
                "draw",
                "AndroidOwner:measureAndLayout",
                "Compose:recompose",
                "Compose:applyChanges",
                // RenderThread: DrawFrame executes the display list the UI thread recorded, and is
                // where the other half of frame CPU lives on a draw-bound device.
                "DrawFrame",
                "syncFrameState",
                "flush commands",
                "eglSwapBuffersWithDamageKHR",
                // Main-thread costs an atrace capture showed dominating a scroll, none of which
                // the composition/draw markers can see.
                "postAndWait",
                "Compose:onForgotten",
                "Compose:onRemembered",
                "AndroidOwner:onTouch",
                "TextStringSimpleNode::measure",
                "TextAnnotatedStringNode:measure",
                "TextLayout:initLayout",
                "animation",
                "Amethyst:DrawAuthor",
            )

        /** Must stay in sync with `NoteTrace` in the app. */
        val TRACED_SECTIONS =
            listOf(
                "Amethyst:NoteCard",
                "Amethyst:AuthorImages",
                "Amethyst:FirstUserInfoRow",
                "Amethyst:SecondUserInfoRow",
                "Amethyst:NoteContent",
                "Amethyst:ReactionsRow",
                // Drill-down inside the two most expensive slots.
                "Amethyst:RxIndicators",
                "Amethyst:RxZapraiser",
                "Amethyst:RxReply",
                "Amethyst:RxBoost",
                "Amethyst:RxLike",
                "Amethyst:RxZap",
                "Amethyst:RxShare",
                "Amethyst:RxPay",
                "Amethyst:TxtReplyPreview",
                "Amethyst:TxtRichText",
                "Amethyst:TxtHashtags",
                // Draw phase (Modifier.tracedDraw).
                "Amethyst:DrawReactions",
                "Amethyst:DrawAuthor",
                "Amethyst:DrawFirstRow",
                "Amethyst:DrawRichText",
                // Non-zero count proves the shared-painter path is actually being taken.
                "Amethyst:SharedPainter",
            )
    }
}
