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
package com.vitorpamplona.amethyst.desktop.service.startup

import com.sun.management.OperatingSystemMXBean
import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.io.File
import java.lang.management.ManagementFactory

/**
 * Where a desktop start spends its time, from the process starting to the home feed's top posts
 * settling: one line per milestone (main, modules built, first frame, account loaded, follow list
 * known, outbox relays known) and one per change of the home feed's top posts, each stamped with
 * the milliseconds since the JVM started.
 *
 * Off unless asked for: `-Damethyst.startupTimeline=true` or `AMETHYST_STARTUP_TIMELINE=1`. Lines
 * go to the log (`grep StartupTimeline`) and, as tab-separated rows, to the file named by
 * `amethyst.startupTimeline.file` / `AMETHYST_STARTUP_TIMELINE_FILE`, which
 * `tools/desktop-run/startup-bench.sh` reads to work out when the top of the feed stopped changing.
 * Off, it costs a flag check per milestone.
 */
object StartupTimeline {
    private const val TAG = "StartupTimeline"
    private const val TOP_N = 10

    val enabled: Boolean =
        System.getProperty("amethyst.startupTimeline") == "true" ||
            System.getenv("AMETHYST_STARTUP_TIMELINE").let { it == "1" || it == "true" }

    // Only when on: JMX's classes cost a cold start ~40 ms.
    private val jvmStart: Long =
        if (enabled) runCatching { ManagementFactory.getRuntimeMXBean().startTime }.getOrDefault(System.currentTimeMillis()) else 0L

    private val file: File? =
        if (!enabled) {
            null
        } else {
            (System.getProperty("amethyst.startupTimeline.file") ?: System.getenv("AMETHYST_STARTUP_TIMELINE_FILE"))
                ?.let { File(it) }
                // The process's start as wall-clock time, so a reader can tell posts made after it.
                ?.also { runCatching { it.writeText("0\tmark\tjvm-start\t$jvmStart\n") } }
        }

    private val seen = HashSet<String>()

    init {
        // Stack samplers for the first seconds, for finding what a slow start spends its time on:
        // `-Damethyst.startupTimeline.sampleUi=<file>` samples the UI thread,
        // `-Damethyst.startupTimeline.sampleAll=<file>` every running thread that is in app code.
        if (enabled) {
            System.getProperty("amethyst.startupTimeline.sampleUi")?.let { sample(File(it), allThreads = false) }
            System.getProperty("amethyst.startupTimeline.sampleAll")?.let { sample(File(it), allThreads = true) }
        }
    }

    private fun sample(
        out: File,
        allThreads: Boolean,
    ) {
        Thread({
            val until = System.currentTimeMillis() + 10_000
            val lines = StringBuilder()
            var ui: Thread? = null
            while (System.currentTimeMillis() < until) {
                val stacks: Map<Thread, Array<StackTraceElement>> =
                    if (allThreads) {
                        Thread.getAllStackTraces()
                    } else {
                        if (ui == null) ui = Thread.getAllStackTraces().keys.firstOrNull { it.name.startsWith("AWT-EventQueue") }
                        ui?.let { mapOf(it to it.stackTrace) } ?: emptyMap()
                    }
                val now = sinceStart()
                stacks.forEach { (t, stack) ->
                    // The main and UI threads in any state (waiting on a lock or in native code is
                    // where a start loses time too); the others only while running app code.
                    val always = t.name == "main" || t.name.startsWith("AWT-EventQueue")
                    val inApp = !allThreads || stack.any { it.className.startsWith("com.vitorpamplona") }
                    val keep = always || (t.state == Thread.State.RUNNABLE && inApp)
                    if (stack.isNotEmpty() && keep && t !== Thread.currentThread()) {
                        lines
                            .append(now)
                            .append('\t')
                            .append(t.name)
                            .append(':')
                            .append(t.state)
                            .append('\t')
                        stack.take(120).joinTo(lines, ";") { "${it.className}.${it.methodName}" }
                        lines.append('\n')
                    }
                }
                Thread.sleep(if (allThreads) 5 else 2)
            }
            out.writeText(lines.toString())
        }, "startup-sampler").apply { isDaemon = true }.start()
    }

    fun sinceStart() = System.currentTimeMillis() - jvmStart

    /** A milestone; logged once per [name]. */
    fun mark(
        name: String,
        detail: () -> String = { "" },
    ) {
        if (!enabled) return
        synchronized(seen) { if (!seen.add(name)) return }
        // The process's CPU time so far: near cores x wall time means the start is CPU-bound.
        write("mark", name, "${detail()}\tcpu=${processCpuMs()}")
    }

    private fun processCpuMs(): Long =
        (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)
            ?.processCpuTime
            ?.let { it / 1_000_000 } ?: -1

    private fun write(
        type: String,
        name: String,
        detail: String,
    ) {
        val ms = sinceStart()
        Log.i(TAG) { "+$ms ms $type $name $detail" }
        file?.let { f -> synchronized(this) { runCatching { f.appendText("$ms\t$type\t$name\t$detail\n") } } }
    }

    /**
     * Follows the logged-in account's start: its lists, the outbox set and the relays connected.
     * The feed itself is [watchFeed], from the account's view model.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun watchSession(
        accountContent: Flow<AccountState>,
        client: INostrClient,
        scope: CoroutineScope,
    ) {
        if (!enabled) return
        scope.launch {
            val account = accountContent.mapNotNull { (it as? AccountState.LoggedIn)?.account }.first()
            mark("account-loaded") { account.pubKey.take(8) }
            watchAccount(account, scope)
        }
        scope.launch {
            var best = 0
            client.connectedRelaysFlow().collect { connected ->
                // Every new peak of connected relays until the feed settles is enough to see the ramp.
                if (connected.size > best) {
                    best = connected.size
                    if (best == 1 || best % 10 == 0) write("relays", "connected", best.toString())
                }
            }
        }
    }

    private fun watchAccount(
        account: Account,
        scope: CoroutineScope,
    ) {
        scope.launch {
            account.kind3FollowList.flow
                .map { it.authors.size }
                .distinctUntilChanged()
                .collect { if (it > 0) mark("follows-known") { "$it authors" } }
        }
        scope.launch {
            account.followsPerRelay
                .map { perRelay -> perRelay.size to perRelay.values.flatMapTo(HashSet()) { it }.size }
                .distinctUntilChanged()
                .collect { (relays, authors) -> write("outbox", "relays", "$relays relays\t$authors authors") }
        }
    }

    /** Logs every change of [feed]'s top posts, as `id:createdAt` pairs. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun watchFeed(
        name: String,
        feed: FeedContentState,
        scope: CoroutineScope,
    ) {
        if (!enabled) return
        // Off the UI thread: the time the feed changed, not the time a busy UI thread got to it.
        scope.launch(Dispatchers.Default) {
            feed.feedContent
                .flatMapLatest { state ->
                    when (state) {
                        is FeedState.Loaded -> state.feed.map { it.list }
                        is FeedState.Empty -> flowOf(emptyList())
                        else -> emptyFlow()
                    }
                }.map { list ->
                    list.size to list.take(TOP_N).joinToString(",") { "${it.idHex.take(12)}:${it.createdAt() ?: 0}" }
                }.distinctUntilChanged()
                .collect { (size, top) ->
                    if (size > 0) mark("$name-first-posts") { "$size posts" }
                    write("feed", name, "$size\t$top")
                }
        }
    }
}
