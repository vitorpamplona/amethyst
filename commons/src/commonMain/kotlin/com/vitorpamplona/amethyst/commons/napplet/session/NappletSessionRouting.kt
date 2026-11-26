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
import com.vitorpamplona.amethyst.commons.napplet.NappletActingRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletAttendance
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletHeldRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentityWatch
import com.vitorpamplona.amethyst.commons.napplet.NappletIncBus
import com.vitorpamplona.amethyst.commons.napplet.NappletIntentDispatcher
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletLiveSubscriptions
import com.vitorpamplona.amethyst.commons.napplet.NappletRequestRouter
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsSession
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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
 * The trusted half of one launch's bridge, which decides what the page's requests mean. In-process
 * hosts (Desktop, iOS) run [NappletSessionRouting] next to the page; Android's sandbox sends the
 * requests over IPC to the broker service, which runs the same routing there.
 */
interface NappletTrustedBridge {
    /** One request from the page (past the bridge gate and the held-request rule), on the thread it arrived on. */
    fun handle(payload: String)

    /** Whether the user is looking at the launch: its decrypting reads and held events wait for it. */
    fun setAttended(isAttended: Boolean)

    /** The launch ended: everything it opened goes with it. */
    fun close()
}

/**
 * The trusted side of one launch's bridge: each request the page sends, routed through
 * [NappletRequestRouter] to the broker, the live subscriptions, NAP-INC or NAP-INTENT, with every
 * answer going back through [reply] (to a request id) or [push] (unasked). Desktop and iOS run it
 * in-process; Android's broker service runs one per launch token and surface, over its IPC.
 */
class NappletSessionRouting<K : Any>(
    private val owner: K,
    private val launch: NappletLaunch,
    /** The account the launch signs as, whoever is active later; NAP-INC connects only its launches. */
    private val accountPubKey: HexKey,
    /** The launching account, for live relay subscriptions, read as each opens; null leaves them unopened. */
    private val account: () -> Account?,
    /** The broker that signs as [accountPubKey]; null once that account is gone, which fails requests closed. */
    private val broker: () -> NappletBroker?,
    private val services: NappletSessionServices<K>,
    /** NAP-FS: the files the user picked for this launch; null when it didn't declare FS. */
    private val fsSession: NappletFsSession?,
    private val scope: CoroutineScope,
    /** Answers the page's request [id] (null: it sent none) with [payload], a broker reply without an id. */
    private val reply: (id: String?, payload: String) -> Unit,
    /** Sends what the page didn't ask for by id: subscription events, NAP-INC, intents, identity changes. */
    private val push: (String) -> Unit,
    /**
     * The launch's key for NAP-INTENT, when the host's launcher names a handler's launch by something it
     * knows before the surface exists (Android: the launch token). [owner] otherwise.
     */
    private val intentKey: K = owner,
    /** Streams `identity.changed` once `identity.getPublicKey` succeeded, under [identityWatchId]; null streams none. */
    private val identityWatch: NappletIdentityWatch? = null,
    private val identityWatchId: String = "",
    /**
     * Whether a read whose results are decrypted with the user's key waits until the user looks at the
     * launch, as its sign and decrypt requests do (Android's broker). A host whose launches are always
     * attended doesn't need it.
     */
    private val readsWaitForAttendance: Boolean = false,
) : NappletTrustedBridge {
    private val lock = KmpLock()

    @Volatile
    private var closed = false

    // NAP-RESOURCE requests the page may still cancel, by request id. Cancelling drops the job before
    // it can answer, so the page gets no late terminal message for a request it gave up on.
    private val resourceRequests = HashMap<String, Job>()

    /** In-process: [send] gets every answer, replies with the request's id put back. */
    constructor(
        owner: K,
        launch: NappletLaunch,
        accountPubKey: HexKey,
        account: Account?,
        broker: NappletBroker,
        services: NappletSessionServices<K>,
        fsSession: NappletFsSession?,
        scope: CoroutineScope,
        send: (String) -> Unit,
    ) : this(owner, launch, accountPubKey, { account }, { broker }, services, fsSession, scope, { id, payload -> send(withReplyId(payload, id)) }, send)

    override fun handle(payload: String) = handle(payload, NappletPageBridge.idOf(payload))

    /** One request from the page, answered as [requestId] (the page's own id, or the host's for one sent without). */
    fun handle(
        payload: String,
        requestId: String?,
    ) {
        if (closed) return
        val type = NappletPageBridge.typeOf(payload)
        if (type == RESOURCE_CANCEL) {
            if (requestId != null) lock.withLock { resourceRequests.remove(requestId) }?.cancel()
            return
        }
        // NAP-INC goes to the bus now, on the arrival thread, so a napplet's messages reach it in the
        // order it sent them (the coroutines below run on a pool). The bus only connects launches of
        // the same account.
        NappletRequestRouter.routeInc(launch.declared, payload, type)?.let { outcome ->
            when (outcome) {
                is NappletRequestRouter.Outcome.Inc -> services.incBus.handle(owner, accountPubKey, push, outcome.request)?.let { reply(requestId, it) }
                is NappletRequestRouter.Outcome.Reply -> reply(requestId, outcome.payload)
                else -> {}
            }
            // Closed meanwhile: close() may have left the bus before this put the launch back on it.
            if (closed) services.incBus.remove(owner)
            return
        }
        // NAP-INTENT: an invoke may open the handler's window, so it runs off this thread.
        NappletRequestRouter.routeIntent(launch.declared, payload, type)?.let { outcome ->
            when (outcome) {
                is NappletRequestRouter.Outcome.Intent -> {
                    val intents = services.intents()
                    if (intents == null) {
                        if (requestId != null) reply(requestId, NappletProtocolJson.encodeIntentFailure(type ?: "intent.invoke", "intent unavailable"))
                    } else {
                        scope.launch { intents.handle(intentKey, launch.identity, push, outcome.request)?.let { reply(requestId, it) } }
                    }
                }
                is NappletRequestRouter.Outcome.Reply -> reply(requestId, outcome.payload)
                else -> {}
            }
            return
        }
        // Stamped now, before anything suspends: orders this request against a later close of its subscription.
        val arrivedAt = services.liveSubscriptions.arrival()
        val tracked = requestId != null && (type == RESOURCE_BYTES || type == RESOURCE_BYTES_MANY)
        val job =
            scope.launch(start = if (tracked) CoroutineStart.LAZY else CoroutineStart.DEFAULT) {
                route(payload, type, requestId, arrivedAt)
            }
        if (tracked && requestId != null) {
            lock.withLock { resourceRequests.put(requestId, job) }?.cancel()
            job.invokeOnCompletion { lock.withLock { if (resourceRequests[requestId] === job) resourceRequests.remove(requestId) } }
            job.start()
        }
    }

    private suspend fun route(
        payload: String,
        type: String?,
        requestId: String?,
        arrivedAt: Long,
    ) {
        val broker = broker()
        if (broker == null) {
            reply(requestId, NappletProtocolJson.encodeResponse(type ?: "napplet", NappletResponse.Failed(ACCOUNT_GONE)))
            return
        }
        // A query's results are decrypted with the user's key: while nobody is looking at the page it
        // waits (as its sign and decrypt requests do), and gives up like them.
        if (readsWaitForAttendance && NappletActingRequests.readsForUser(type) && !services.attendance.awaitAttended(owner, NappletHeldRequests.MAX_AGE_MS)) {
            reply(requestId, NappletProtocolJson.encodeResponse(type ?: "napplet", NappletResponse.Failed(NappletHeldRequests.EXPIRED)))
            return
        }
        when (val outcome = NappletRequestRouter.route(broker, launch.identity, launch.declared, payload, fsSession)) {
            is NappletRequestRouter.Outcome.Reply -> {
                reply(requestId, outcome.payload)
                // NAP-IDENTITY has no watch request: once the consent-gated snapshot succeeds, the launch
                // hears of its account leaving until it closes.
                if (type == "identity.getPublicKey" && outcome.response is NappletResponse.PublicKey && !closed) {
                    identityWatch?.start(identityWatchId, accountPubKey, push)
                }
            }
            is NappletRequestRouter.Outcome.Push -> outcome.payloads.forEach(push)
            is NappletRequestRouter.Outcome.OpenSubscription ->
                services.liveSubscriptions.open(owner, outcome.subId, outcome.filters, account(), arrivedAt, outcome.relays, outcome.outbox, outcome.mayDecrypt, push)
            is NappletRequestRouter.Outcome.CloseSubscription -> services.liveSubscriptions.close(owner, outcome.subId, arrivedAt)
            // NAP-INC and NAP-INTENT were routed above, before this coroutine started.
            else -> {}
        }
    }

    override fun setAttended(isAttended: Boolean) {
        lock.withLock {
            if (closed) return
            services.attendance.set(owner, isAttended)
        }
        // Encrypted events its subscriptions received meanwhile can be decrypted and delivered now.
        if (isAttended) services.liveSubscriptions.onAttended(owner)
    }

    /** The launch ended: its subscriptions, channels, intents, identity stream, requests and attendance go with it. */
    override fun close() {
        val pending =
            lock.withLock {
                if (closed) return
                closed = true
                resourceRequests.values.toList().also { resourceRequests.clear() }
            }
        pending.forEach { it.cancel() }
        fsSession?.close()
        services.liveSubscriptions.closeAllFor(owner)
        services.incBus.remove(owner)
        services.intents()?.remove(intentKey)
        identityWatch?.stop(identityWatchId)
        services.attendance.forget(owner)
    }

    companion object {
        const val ACCOUNT_GONE = "That account is no longer signed in."
        private const val RESOURCE_CANCEL = "resource.cancel"
        private const val RESOURCE_BYTES = "resource.bytes"
        private const val RESOURCE_BYTES_MANY = "resource.bytesMany"
    }
}
