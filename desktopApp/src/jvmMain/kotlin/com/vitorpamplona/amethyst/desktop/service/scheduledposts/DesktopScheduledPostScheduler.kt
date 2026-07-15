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

import com.vitorpamplona.amethyst.commons.scheduledposts.LoggingScheduledPostNotifier
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostPublisher
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

/**
 * In-app scheduler that publishes due scheduled posts while Amethyst Desktop runs.
 *
 * Phase 2 scope: the app is the sole writer, so no cross-process lockfile is needed.
 * On start it runs one catch-up drain (to publish anything overdue after the app was
 * closed), then ticks every [TICK_INTERVAL_MS]. All drains are account-scoped so one
 * account never publishes another account's rows. Overlapping ticks are prevented by
 * a non-blocking [Mutex] guard.
 */
class DesktopScheduledPostScheduler(
    private val store: ScheduledPostStore,
    private val scope: CoroutineScope,
) {
    private val notifier = LoggingScheduledPostNotifier()
    private val drainGuard = Mutex()
    private var job: Job? = null

    /**
     * (Re)start the loop for [accountPubkey], publishing to that account's outbox via
     * [client]. [resolveRelays] returns the account's current NIP-65 write relays;
     * the shared publisher falls back to each post's stored relay URLs when it is
     * empty. Calling this again (e.g. on account switch) cancels the prior loop.
     */
    fun start(
        client: INostrClient,
        accountPubkey: String,
        resolveRelays: () -> Set<NormalizedRelayUrl>,
    ) {
        stop()
        val publisher =
            ScheduledPostPublisher(
                store = store,
                client = client,
                resolveRelays = { resolveRelays() },
                onSent = { notifier.notifySent(it) },
                onFailed = { post, error -> notifier.notifyFailed(post, error) },
            )

        job =
            scope.launch(Dispatchers.IO) {
                // Catch-up pass first, then steady-state ticking.
                drainOnce(publisher, accountPubkey)
                while (isActive) {
                    delay(TICK_INTERVAL_MS)
                    drainOnce(publisher, accountPubkey)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun drainOnce(
        publisher: ScheduledPostPublisher,
        accountPubkey: String,
    ) {
        // Skip this cycle if a previous drain is still running.
        if (!drainGuard.tryLock()) return
        try {
            // Cross-process single-writer discipline: the headless `--publish-scheduled`
            // process drains the same store. The file lock ensures only one of the two
            // drains a due row at a time. If it's held, skip this tick (the other
            // drainer will handle the due posts).
            val report =
                ScheduledPostLock.withDrainLock {
                    runBlocking { publisher.drainDue(TimeUtils.now(), accountPubkey) }
                } ?: return
            if (report.published > 0 || report.failed > 0 || report.skipped > 0) {
                Log.i(TAG) {
                    "Drain: published=${report.published} failed=${report.failed} skipped=${report.skipped} " +
                        "for ${accountPubkey.take(8)}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scheduled post drain failed", e)
        } finally {
            drainGuard.unlock()
        }
    }

    companion object {
        private const val TAG = "DesktopScheduledPostScheduler"
        private const val TICK_INTERVAL_MS = 45_000L
    }
}
