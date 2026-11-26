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
package com.vitorpamplona.amethyst.commons.napplet.session

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.NappletAttendance
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletIncBus
import com.vitorpamplona.amethyst.commons.napplet.NappletIntentDispatcher
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletLiveSubscriptions
import com.vitorpamplona.amethyst.commons.napplet.NappletRequestRouter
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsSession
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * What every launch of one host shares, keyed by the host's surface [K]: attendance (which the
 * broker's decrypting reads wait on), the live relay subscriptions, the NAP-INC bus and NAP-INTENT.
 */
class NappletSessionServices<K : Any>(
    val attendance: NappletAttendance<K>,
    val liveSubscriptions: NappletLiveSubscriptions<K>,
    val incBus: NappletIncBus<K>,
    /** Set by the host, which is what can open a handler; null answers `intent unavailable`. */
    val intents: () -> NappletIntentDispatcher<K>?,
)

/**
 * The trusted side of one launch's bridge: each request the page sends, routed through
 * [NappletRequestRouter] to the broker, the live subscriptions, NAP-INC or NAP-INTENT, with every
 * answer going back through [send]. Desktop and iOS run it in-process; Android's broker service is
 * the same thing keyed by a launch token.
 */
class NappletSessionRouting<K : Any>(
    private val owner: K,
    private val launch: NappletLaunch,
    /** The account the launch signs as, whoever is active later; NAP-INC connects only its launches. */
    private val accountPubKey: HexKey,
    /** The launching account, for live relay subscriptions; null leaves them unopened. */
    private val account: Account?,
    private val broker: NappletBroker,
    private val services: NappletSessionServices<K>,
    /** NAP-FS: the files the user picked for this launch; null when it didn't declare FS. */
    private val fsSession: NappletFsSession?,
    private val scope: CoroutineScope,
    private val send: (String) -> Unit,
) {
    @Volatile
    private var closed = false

    /** One request from the page, on the thread it arrived on. */
    fun handle(payload: String) {
        if (closed) return
        // NAP-INC goes to the bus now, on the arrival thread, so a napplet's messages reach it in the
        // order it sent them (the coroutines below run on a pool). The bus only connects launches of
        // the same account.
        NappletRequestRouter.routeInc(launch.declared, payload)?.let { outcome ->
            val id = NappletPageBridge.idOf(payload)
            when (outcome) {
                is NappletRequestRouter.Outcome.Inc -> services.incBus.handle(owner, accountPubKey, send, outcome.request)?.let { send(withReplyId(it, id)) }
                is NappletRequestRouter.Outcome.Reply -> send(withReplyId(outcome.payload, id))
                else -> {}
            }
            // Closed meanwhile: close() may have left the bus before this put the launch back on it.
            if (closed) services.incBus.remove(owner)
            return
        }
        // NAP-INTENT: an invoke may open the handler's window, so it runs off this thread.
        NappletRequestRouter.routeIntent(launch.declared, payload)?.let { outcome ->
            val id = NappletPageBridge.idOf(payload)
            when (outcome) {
                is NappletRequestRouter.Outcome.Intent -> {
                    val intents = services.intents()
                    if (intents == null) {
                        if (id != null) send(withReplyId(NappletProtocolJson.encodeIntentFailure(NappletPageBridge.typeOf(payload) ?: "intent.invoke", "intent unavailable"), id))
                    } else {
                        scope.launch { intents.handle(owner, launch.identity, send, outcome.request)?.let { send(withReplyId(it, id)) } }
                    }
                }
                is NappletRequestRouter.Outcome.Reply -> send(withReplyId(outcome.payload, id))
                else -> {}
            }
            return
        }
        val arrivedAt = services.liveSubscriptions.arrival()
        scope.launch {
            val id = NappletPageBridge.idOf(payload)
            when (val outcome = NappletRequestRouter.route(broker, launch.identity, launch.declared, payload, fsSession)) {
                is NappletRequestRouter.Outcome.Reply -> send(withReplyId(outcome.payload, id))
                is NappletRequestRouter.Outcome.Push -> outcome.payloads.forEach(send)
                is NappletRequestRouter.Outcome.OpenSubscription ->
                    services.liveSubscriptions.open(owner, outcome.subId, outcome.filters, account, arrivedAt, outcome.relays, outcome.outbox, outcome.mayDecrypt, send)
                is NappletRequestRouter.Outcome.CloseSubscription -> services.liveSubscriptions.close(owner, outcome.subId, arrivedAt)
                // NAP-INC and NAP-INTENT were routed above, before this coroutine started.
                else -> {}
            }
        }
    }

    /** The launch ended: its subscriptions, channels, intents and attendance go with it. */
    fun close() {
        closed = true
        fsSession?.close()
        services.liveSubscriptions.closeAllFor(owner)
        services.incBus.remove(owner)
        services.intents()?.remove(owner)
        services.attendance.forget(owner)
    }
}
