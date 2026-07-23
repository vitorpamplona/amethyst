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
package com.vitorpamplona.amethyst.service.pow

import android.content.Context
import android.content.pm.ServiceInfo
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.service.pow.PoWEstimator
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobState
import com.vitorpamplona.amethyst.service.foreground.FlowProgressForegroundService
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/**
 * Short-lived foreground service that shields the PoW mining queue from the
 * cached-apps freezer: while it runs the process stays schedulable, so posts
 * finish mining even after the user backgrounds the app.
 *
 * Uses the Android 14+ `shortService` type — no special permission, but a
 * hard ~3 minute budget. On `onTimeout` the service exits cleanly; every
 * persistable job is already checkpointed by [PowJobStore], so anything still
 * unmined resumes on the next app launch. Started on every enqueue (the app
 * is necessarily in the foreground then), stops itself when the queue drains.
 *
 * The notification card (a live [androidx.core.app.NotificationCompat.ProgressStyle]) and all the
 * service lifecycle live in [FlowProgressForegroundService]; this subclass only maps mining state
 * to that card.
 */
class PowMiningForegroundService : FlowProgressForegroundService<ImmutableList<PoWJobState>>() {
    override val fgsType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
    override val channelId = CHANNEL_ID
    override val channelNameRes = R.string.pow_notification_channel_name
    override val channelDescRes = R.string.pow_notification_channel_description
    override val notificationId = NOTIFICATION_ID
    override val cancelAction = ACTION_CANCEL_ALL
    override val cancelLabelRes = R.string.pow_notification_cancel_all
    override val secondaryAction = ACTION_SEND_ALL_NOW
    override val secondaryLabelRes = R.string.pow_notification_send_without_pow

    // clock-driven refresh for the time-left text and bar; the shortService budget (~3 min)
    // caps this at a handful of updates.
    override val refreshMs: Long = PROGRESS_REFRESH_MS

    // Session totals so the progress track can show "done / enqueued since the service
    // started" — the queue itself only knows what is still pending.
    private var sessionTotal = 0
    private var lastQueueSize = 0

    // Benchmarked once per service run (~250 ms, cached by the estimator).
    @Volatile
    private var hashRate: Double? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun state() = Amethyst.instance.powPublishQueue.jobs

    override fun isActive(value: ImmutableList<PoWJobState>) = value.isNotEmpty()

    override fun cancelAll() = Amethyst.instance.powPublishQueue.cancelAll()

    override fun onSecondaryAction() = Amethyst.instance.powPublishQueue.sendAllWithoutPow()

    override fun needsClockRefresh(value: ImmutableList<PoWJobState>) = value.any { it.isMining }

    override fun onStarted() {
        scope.launch { hashRate = PoWEstimator.hashesPerSecond() }
    }

    override fun onEmission(value: ImmutableList<PoWJobState>) {
        if (value.size > lastQueueSize) sessionTotal += value.size - lastQueueSize
        lastQueueSize = value.size
    }

    override fun render(value: ImmutableList<PoWJobState>): Content {
        val done = (sessionTotal - value.size).coerceAtLeast(0)
        val total = (done + value.size).coerceAtLeast(1)

        val current = value.firstOrNull { it.isMining } ?: value.firstOrNull()

        // expected duration for the job being mined right now, so the card can say
        // "≈ 10 minutes left" and fill its bar toward a predictable end.
        val rate = hashRate
        val startedAt = current?.miningStartedAt
        val expectedSec = if (current != null && rate != null) PoWEstimator.estimateSeconds(current.difficulty, rate) else null
        val elapsedSec = startedAt?.let { (TimeUtils.now() - it).coerceAtLeast(0) }

        val base =
            current?.let {
                pluralStringRes(this, R.plurals.pow_mining_job, it.difficulty, stringRes(this, powKindLabelRes(it.kind)), it.difficulty)
            } ?: stringRes(this, R.string.pow_mining_title)
        val text =
            if (expectedSec != null && elapsedSec != null) {
                "$base • ${formatTimeLeft(this, expectedSec, elapsedSec)}"
            } else {
                base
            }

        val fraction = if (expectedSec != null && elapsedSec != null) elapsedSec / expectedSec else null

        val title =
            if (value.size > 1) {
                pluralStringRes(this, R.plurals.pow_mining_progress, value.size, value.size)
            } else {
                stringRes(this, R.string.pow_mining_title)
            }

        val bar =
            if (total <= 1) {
                // single post: fill toward the estimated duration; past the mean the search is
                // memoryless, so sweep instead of lying.
                if (fraction != null && fraction < 1.0) Bar.Determinate(fraction) else Bar.Indeterminate
            } else {
                Bar.Segmented(total, done)
            }

        return Content(title, text, bar)
    }

    companion object {
        private const val TAG = "PowMiningFgs"
        private const val CHANNEL_ID = "pow_mining"
        private const val NOTIFICATION_ID = 0x504F57 // "POW"
        private const val ACTION_CANCEL_ALL = "com.vitorpamplona.amethyst.pow.CANCEL_ALL"
        private const val ACTION_SEND_ALL_NOW = "com.vitorpamplona.amethyst.pow.SEND_ALL_NOW"

        private const val PROGRESS_REFRESH_MS = 30_000L

        // Best-effort de-dup for start(): the queue calls it on EVERY enqueue.
        @Volatile
        private var running = false

        /**
         * Best-effort start: enqueue happens while the user is interacting with the app,
         * so the foreground-start allowance normally holds.
         */
        fun start(context: Context) {
            if (running) return
            FlowProgressForegroundService.start(context, PowMiningForegroundService::class.java, TAG)
        }
    }
}
