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
package com.vitorpamplona.amethyst.desktop.service.scheduledposts

import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostPublisher
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Headless entry point for the `--publish-scheduled` mode.
 *
 * The OS scheduler (see [OsScheduler]) launches the app binary in this mode roughly
 * every 5 minutes while pending scheduled posts exist. This routine is KEY-FREE: it
 * only opens websockets and pushes already-signed event bytes taken verbatim from
 * the store. It NEVER loads the signer, keychain, account list, or any UI — the
 * events were signed earlier (when the user scheduled them, with the app open) and
 * are re-verified in [ScheduledPostPublisher] before send.
 *
 * Flow:
 *  1. Acquire the single-writer drain lock (shared with the in-app timer). If it's
 *     held (app running, or another headless run), log and exit 0.
 *  2. Load the shared store, compute the union of due posts' relay URLs.
 *  3. Build a minimal [NostrClient] over a plain (non-Tor) OkHttp websocket builder,
 *     connect, wait briefly for sockets, then drain ALL accounts' due posts.
 *  4. Exit 0 (or 1 if the drain reported failures).
 */
fun runHeadlessPublish(): Int {
    val logFile = File(scheduledDir(), "scheduler.log")

    fun log(msg: String) {
        val line = "${timestamp()} $msg"
        System.err.println("[scheduled-publish] $line")
        try {
            logFile.parentFile?.mkdirs()
            logFile.appendText("$line\n")
        } catch (_: Exception) {
            // Logging is best-effort; never fail the drain over a log write.
        }
    }

    val result =
        ScheduledPostLock.withDrainLock {
            headlessDrain(::log)
        }

    if (result == null) {
        log("Drain skipped: another process holds the lock (app running or headless run in flight)")
        return 0
    }
    return result
}

private fun headlessDrain(log: (String) -> Unit): Int {
    val store = ScheduledPostStore(File(scheduledDir(), ScheduledPostStore.FILE_NAME))

    // Build a plain OkHttp client for websockets — no Tor, no proxy. Headless mode
    // reads no settings, so we cannot know Tor preferences; scheduled posts that
    // require Tor simply won't publish here (documented limitation).
    val okHttp =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    val client = NostrClient(BasicOkHttpWebSocket.Builder { okHttp })

    return try {
        runBlocking {
            val now = TimeUtils.now()

            // Determine the relay set: union of due (PENDING & publishAtSec<=now) rows.
            val dueRelays: Set<NormalizedRelayUrl> =
                store
                    .list()
                    .filter { it.status == ScheduledPostStatus.PENDING && it.publishAtSec <= now }
                    .flatMap { it.relayUrls }
                    .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                    .toSet()

            if (dueRelays.isEmpty()) {
                log("No due posts to publish")
                return@runBlocking 0
            }

            log("Connecting to ${dueRelays.size} relay(s) for due posts")
            client.connect()

            // publishAndConfirmDetailed (inside the publisher) triggers per-relay
            // connect+send, but give the sockets a head start so the first confirm
            // window isn't spent on the TCP/TLS/WS handshake.
            client.reconnect(onlyIfChanged = false, ignoreRetryDelays = true)
            awaitAnyConnection(client, dueRelays)

            val publisher =
                ScheduledPostPublisher(
                    store = store,
                    client = client,
                    resolveRelays = { post ->
                        post.relayUrls.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
                    },
                    onSent = { log("Sent ${it.id} (kind event ${it.signedEventJson.length} bytes)") },
                    onFailed = { post, error -> log("Failed ${post.id}: $error") },
                )

            // All accounts: headless mode has no "current account". The publisher
            // asserts event.pubKey == post.accountPubkey per row, so cross-account
            // publishing is impossible even though we drain every row.
            val report = publisher.drainDue(now)
            log("Drain complete: published=${report.published} failed=${report.failed} skipped=${report.skipped}")

            if (report.failed > 0) 1 else 0
        }
    } catch (e: Exception) {
        log("Headless drain crashed: ${e.message}")
        1
    } finally {
        try {
            client.close()
        } catch (_: Exception) {
        }
    }
}

/** Waits up to [timeoutMs] for at least one target relay to connect. */
private suspend fun awaitAnyConnection(
    client: NostrClient,
    relays: Set<NormalizedRelayUrl>,
    timeoutMs: Long = 8_000,
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val connected = client.connectedRelaysFlow().value
        if (relays.any { it in connected }) return
        delay(200)
    }
}

private fun scheduledDir(): File {
    val dir = File(System.getProperty("user.home"), ".amethyst/scheduled")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
