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
package com.vitorpamplona.amethyst.commons.model.marmot.devices

import com.vitorpamplona.marmot.OutboundGroupEvent
import com.vitorpamplona.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.marmot.client.MarmotManager
import com.vitorpamplona.marmot.features.deviceGroup.DeviceGroupTrust
import com.vitorpamplona.marmot.features.deviceGroup.MarmotDeviceGroupManager
import com.vitorpamplona.marmot.features.devicePairing.CancelReason
import com.vitorpamplona.marmot.features.devicePairing.ConfirmedLink
import com.vitorpamplona.marmot.features.devicePairing.ExistingDevicePairing
import com.vitorpamplona.marmot.features.devicePairing.MarmotDevicePairingKinds
import com.vitorpamplona.marmot.features.devicePairing.NewDevicePairing
import com.vitorpamplona.marmot.features.devicePairing.PairingDelivery
import com.vitorpamplona.marmot.features.devicePairing.PairingError
import com.vitorpamplona.marmot.features.devicePairing.PairingException
import com.vitorpamplona.marmot.features.devicePairing.PairingMessage
import com.vitorpamplona.marmot.features.devicePairing.PairingMessageException
import com.vitorpamplona.marmot.mip00KeyPackages.KeyPackageUtils
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64

/**
 * Marmot device groups for one account on this device (`features/device-group.md`,
 * `features/device-pairing.md`): the headless runtime every front end drives. It wires
 * marmot-kmp's [MarmotDeviceGroupManager] and the two pairing state machines to a
 * [MarmotDevicesTransport], publishes everything they return, runs their timers from [tick],
 * and exposes what a devices screen shows as [state].
 *
 * Exists only while the account's device-group switch is on (`MarmotDeviceGroupSwitch`): the
 * [manager] must have been built with a `MarmotDeviceGroupConfig`.
 *
 * Not done here, by design for now: history deliveries (kind 473, `account-sync.md` "History").
 * A newly linked device gets its chats but not their past messages; the delivery would plug in
 * after [addNewDeviceToChatsAndReport] (the approver sends the bundles it has for each chat) and
 * in [afterIngest] (the new device imports them). The switch stays off by default until it lands.
 */
class MarmotDevices(
    private val manager: MarmotManager,
    private val signer: NostrSigner,
    private val transport: MarmotDevicesTransport,
    /** Where this device publishes its KeyPackage; read when a link starts. */
    private val keyPackageRelays: suspend () -> Set<NormalizedRelayUrl>,
    private val clock: () -> Long = { TimeUtils.now() },
) {
    val groups: MarmotDeviceGroupManager = checkNotNull(manager.deviceGroups) { "MarmotManager was built without device groups" }

    private val mutex = Mutex()
    private val _state = MutableStateFlow(MarmotDevicesSnapshot())
    val state: StateFlow<MarmotDevicesSnapshot> = _state.asStateFlow()

    private var started = false

    /**
     * Pairing messages that arrived before [start] (the app opens gift wraps while `restoreAll`
     * still runs, and stores the wrap without its content, so it never comes back): replayed by
     * [start], oldest first, the newest [MAX_EARLY_PAIRING] kept.
     */
    private val earlyPairing = ArrayDeque<Pair<PairingMessage, Long>>()

    // ---- new device ----------------------------------------------------------------------------
    private var newDevice: NewDevicePairing? = null
    private var newDeviceState: NewDeviceLinkState? = null

    // ---- existing device -----------------------------------------------------------------------
    private var existing: ExistingDevicePairing? = null

    private class Prompt(
        val request: PairingMessage.Request,
        val requestedAt: Long,
        var keyPackage: KeyPackageEvent? = null,
        var label: String = "",
        var state: DeviceLinkPrompt.State = DeviceLinkPrompt.State.Preparing,
    )

    private val prompts = LinkedHashMap<HexKey, Prompt>()

    /** Wrap ids already handled this process: relays redeliver every poll. */
    private val seenWraps = HashSet<HexKey>()

    /** New devices this device sent its chat inventory to. */
    private val inventorySent = HashSet<HexKey>()
    private var lastRetag = 0L
    private var lastSignInCheck = 0L

    /** Whether [nostrGroupId] is this account's hidden device group: no chatroom, no notification. */
    fun isDeviceGroup(nostrGroupId: HexKey): Boolean = groups.isTrustedDeviceGroup(nostrGroupId)

    /**
     * Loads trust; with [announce], also broadcasts this device's small state and asks its siblings
     * for theirs (`account-sync.md` "Broadcasts", on start). Call once after `restoreAll`. A front end
     * that starts a process per command (`amy`) announces only from the verbs that run the device
     * group, not on every run.
     */
    suspend fun start(announce: Boolean = true) =
        mutex.withLock {
            if (!started) {
                groups.start()
                started = true
                while (earlyPairing.isNotEmpty()) {
                    val (message, createdAt) = earlyPairing.removeFirst()
                    runStep("early pairing") { handlePairing(message, createdAt) }
                }
            }
            if (announce) runStep("start broadcast") { publishAll(groups.broadcastDue(clock(), start = true)) }
            refreshState()
        }

    // =============================================================================================
    // Inbound
    // =============================================================================================

    /** A kind 1059 addressed to this account: true when it carried a pairing message. */
    suspend fun onGiftWrap(wrap: GiftWrapEvent): Boolean {
        if (!mutex.withLock { seenWraps.add(wrap.id) }) return false
        val received = PairingDelivery.unwrap(wrap, signer) ?: return false
        onPairing(received.message, received.createdAt)
        return true
    }

    /**
     * A pairing rumor an app's own gift-wrap pipeline already opened: the seal's author is
     * [sealAuthor], the rumor is [rumor]. Only a rumor this account sent itself counts
     * (`transports/nostr.md` "Pairing delivery"). True when it was pairing traffic.
     */
    suspend fun onPairingRumor(
        sealAuthor: HexKey,
        rumor: Event,
    ): Boolean {
        if (!MarmotDevicePairingKinds.isPairingKind(rumor.kind)) return false
        if (sealAuthor != signer.pubKey || rumor.pubKey != signer.pubKey) return true
        val message =
            try {
                PairingMessage.fromContent(rumor.kind, rumor.content)
            } catch (e: PairingMessageException) {
                Log.d(TAG) { "unreadable pairing rumor ${rumor.id.take(8)}: ${e.error}" }
                return true
            }
        onPairing(message, rumor.createdAt)
        return true
    }

    private suspend fun onPairing(
        message: PairingMessage,
        createdAt: Long,
    ) = mutex.withLock {
        if (!started) {
            if (earlyPairing.size >= MAX_EARLY_PAIRING) earlyPairing.removeFirst()
            earlyPairing.addLast(message to createdAt)
            return@withLock
        }
        handlePairing(message, createdAt)
        refreshState()
    }

    private suspend fun handlePairing(
        message: PairingMessage,
        createdAt: Long,
    ) {
        val now = clock()
        val mine = newDevice
        if (mine != null && message.sessionId == mine.sessionId) {
            onNewDeviceMessage(mine, message, now)
        } else if (mine == null || message !is PairingMessage.Request) {
            // A device linking itself answers no one else's request; everything else may be ours.
            onExistingDeviceMessage(message, createdAt, now)
        }
    }

    /**
     * After every Marmot ingest (group event or Welcome): publish what the device group queued,
     * and on a new device that just joined its device group, send the first Commit.
     */
    suspend fun afterIngest() =
        mutex.withLock {
            if (!started) return@withLock
            runStep("outbox") { publishAll(groups.drainOutbox()) }
            runStep("first commit") { firstCommitIfJoined() }
            refreshState()
        }

    private suspend fun firstCommitIfJoined() {
        val t = groups.trust()
        if (!t.trustsDeviceGroup || t.unlinked) return
        val own = groups.roster()?.device(t.ownSlot) ?: return
        if (own.tagSignatureKey != null) {
            if (newDeviceState == NewDeviceLinkState.Joining) newDeviceState = NewDeviceLinkState.Linked
            return
        }
        Log.d(TAG) { "joined the device group: sending the first Commit" }
        groups.applyStaged(groups.stageFirstCommit())
        newDeviceState = NewDeviceLinkState.Linked
        newDevice = null
        // Hand the siblings private KeyPackages right away, so they can add this device to chats.
        publishAll(groups.topUpSiblings(clock()))
    }

    // =============================================================================================
    // New device
    // =============================================================================================

    /**
     * Starts linking this device to the account's others: publishes a fresh KeyPackage (the one the
     * existing device will add) and sends the Request. Watch [state]`.newDevice`.
     */
    suspend fun startLinkAsNewDevice(): NewDeviceLinkState =
        mutex.withLock {
            check(started) { "start() first" }
            val t = groups.trust()
            check(!t.trustsDeviceGroup) { "This device is already linked" }
            newDevice?.cancel()?.let { publishPairing(it) }
            val relays = keyPackageRelays()
            val event = manager.generateKeyPackageEvent(relays.toList())
            check(transport.publishKeyPackage(event)) { "No relay accepted the KeyPackage" }
            manager.confirmKeyPackagePublished(event)
            val ref = KeyPackageUtils.decodeKeyPackage(Base64.decode(event.content)).reference().toHexKey()
            val session = NewDevicePairing.start(signer.pubKey, event.id, ref, clock())
            newDevice = session
            newDeviceState = NewDeviceLinkState.WaitingForAnswer
            publishPairing(session.requestMessage)
            refreshState()
            NewDeviceLinkState.WaitingForAnswer
        }

    private suspend fun onNewDeviceMessage(
        session: NewDevicePairing,
        message: PairingMessage,
        now: Long,
    ) {
        when (val event = session.receive(message, now)) {
            is NewDevicePairing.Event.Ignored -> Log.d(TAG) { "new device ignored ${message.kind}: ${event.reason}" }
            is NewDevicePairing.Event.Reveal -> {
                publishPairing(event.reveal)
                newDeviceState = session.code(now)?.let { code -> NewDeviceLinkState.Comparing(code.map(SasSymbol::of)) }
            }
            NewDevicePairing.Event.PeerConfirmed -> {}
            is NewDevicePairing.Event.Linked -> {
                publishPairing(event.confirm)
                groups.recordLink(event.link, now)
                newDeviceState = NewDeviceLinkState.Joining
            }
            is NewDevicePairing.Event.Ended -> {
                event.cancel?.let { publishPairing(it) }
                groups.cancelLink(session.sessionId)
                newDeviceState = NewDeviceLinkState.Ended(DeviceLinkEndReason.of(event.reason))
                newDevice = null
            }
        }
    }

    /** The user says both screens show the same seven emoji. */
    suspend fun confirmMatch(): NewDeviceLinkState =
        mutex.withLock {
            val session = checkNotNull(newDevice) { "No link in progress" }
            val now = clock()
            when (val c = session.confirmMatch(now)) {
                NewDevicePairing.Confirmation.Waiting -> newDeviceState = NewDeviceLinkState.WaitingForOtherDevice
                is NewDevicePairing.Confirmation.Linked -> {
                    publishPairing(c.confirm)
                    groups.recordLink(c.link, now)
                    newDeviceState = NewDeviceLinkState.Joining
                }
            }
            refreshState()
            checkNotNull(newDeviceState)
        }

    /** The user says the codes differ. */
    suspend fun rejectMatch() =
        mutex.withLock {
            val session = newDevice ?: return@withLock
            publishPairing(session.rejectMatch(clock()))
            newDeviceState = NewDeviceLinkState.Ended(DeviceLinkEndReason.CODES_DIFFER)
            newDevice = null
            refreshState()
        }

    /** The user gives up linking this device. */
    suspend fun cancelLink() =
        mutex.withLock {
            val session = newDevice ?: return@withLock
            session.cancel()?.let { publishPairing(it) }
            groups.cancelLink(session.sessionId)
            newDeviceState = NewDeviceLinkState.Ended(DeviceLinkEndReason.CANCELLED)
            newDevice = null
            refreshState()
        }

    // =============================================================================================
    // Existing device
    // =============================================================================================

    private suspend fun existingPairing(): ExistingDevicePairing = existing ?: ExistingDevicePairing(signer.pubKey, groups.trust().ownSlot).also { existing = it }

    private suspend fun onExistingDeviceMessage(
        message: PairingMessage,
        createdAt: Long,
        now: Long,
    ) {
        val t = groups.trust()
        if (t.unlinked || t.pendingLink != null) return
        val pairing = existingPairing()
        when (val event = pairing.receive(message, createdAt, now)) {
            is ExistingDevicePairing.Event.Ignored -> Log.d(TAG) { "existing device ignored ${message.kind}: ${event.reason}" }
            is ExistingDevicePairing.Event.Requested -> {
                val request = message as PairingMessage.Request
                val prompt = Prompt(request, createdAt)
                prompts[event.sessionId] = prompt
                answer(pairing, prompt, now)
            }
            is ExistingDevicePairing.Event.CodeReady -> {
                val prompt = prompts[event.sessionId] ?: return
                prompt.state = comparingState(pairing, event.sessionId, now) ?: prompt.state
            }
            is ExistingDevicePairing.Event.Linked -> {
                val prompt = prompts[event.link.sessionId] ?: return
                prompt.state =
                    try {
                        approveConfirmedLink(event.link, prompt, now)
                        DeviceLinkPrompt.State.Linked
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "adding the confirmed device failed", e)
                        DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.LINK_FAILED)
                    }
            }
            is ExistingDevicePairing.Event.Ended -> {
                event.cancel?.let { publishPairing(it) }
                prompts[event.sessionId]?.state = DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.of(event.reason))
            }
        }
    }

    private fun markCompeting(now: Long) {
        val live =
            existing
                ?.liveRequests(now)
                ?.map { it.sessionId }
                ?.toSet()
                .orEmpty()
        prompts.forEach { (id, p) -> if (id in live && p.state !is DeviceLinkPrompt.State.Ended) p.state = DeviceLinkPrompt.State.CompetingRequests }
    }

    /** Fetches and checks the KeyPackage the request names, then answers it. */
    private suspend fun answer(
        pairing: ExistingDevicePairing,
        prompt: Prompt,
        now: Long,
    ) {
        val request = prompt.request
        // This device's own Request (an echo, or one from an earlier run of a per-command front end):
        // its KeyPackage may be replaced on the relays already, so recognise it by the ref.
        if (groups.trust().linkedKeyPackageRef == request.keyPackageRef ||
            manager.keyPackageRotationManager.findBundleByRef(request.keyPackageRef.hexToByteArray()) != null
        ) {
            prompts.remove(request.sessionId)
            pairing.cancel(request.sessionId)
            return
        }
        // Not on our relays yet (the new device may still be publishing it): try again on a later tick.
        val keyPackage = transport.fetchOwnKeyPackage(request.keyPackageEventId) ?: return
        val matches =
            keyPackage.pubKey == signer.pubKey &&
                runCatching { KeyPackageUtils.decodeKeyPackage(Base64.decode(keyPackage.content)).reference().toHexKey() }.getOrNull() == request.keyPackageRef
        val ownOrLinked = keyPackage.dTag().let { slot -> slot == groups.trust().ownSlot || groups.roster()?.device(slot) != null }
        if (matches && ownOrLinked) {
            // This device's own Request (an echo, or one from an earlier run of a per-command front
            // end), or a device already in the roster: not a request to answer, and it must not
            // count as a competing one.
            prompts.remove(request.sessionId)
            pairing.cancel(request.sessionId)
            return
        }
        if (!matches) {
            Log.w(TAG) { "link request ${request.sessionId.take(8)} names a KeyPackage this account cannot find or that does not match" }
            prompt.state = DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.CANNOT_LINK)
            pairing.cancel(request.sessionId)
            return
        }
        prompt.keyPackage = keyPackage
        if (pairing.competingRequests(now)) {
            markCompeting(now)
            return
        }
        try {
            publishPairing(pairing.answer(request.sessionId, ExistingDevicePairing.PublishedKeyPackage(keyPackage.id, request.keyPackageRef), now))
        } catch (e: PairingException) {
            prompt.state =
                when (e.error) {
                    PairingError.COMPETING_REQUESTS -> DeviceLinkPrompt.State.CompetingRequests
                    PairingError.RATE_LIMITED -> DeviceLinkPrompt.State.RateLimited(e.retryAt ?: now)
                    PairingError.EXPIRED -> DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.TIMED_OUT)
                    else -> DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.CANNOT_LINK)
                }
        }
    }

    private fun comparingState(
        pairing: ExistingDevicePairing,
        sessionId: HexKey,
        now: Long,
    ): DeviceLinkPrompt.State.Comparing? = pairing.code(sessionId, now)?.let { DeviceLinkPrompt.State.Comparing(it.map(SasSymbol::of), pairing.canOfferLink(sessionId, now)) }

    /**
     * The user compared the codes on both screens and links the new device as [label]. Sends the
     * Confirm; the device is added once the new device's own Confirm verifies.
     */
    suspend fun approveLink(
        sessionId: HexKey,
        label: String,
    ) = mutex.withLock {
        val pairing = checkNotNull(existing) { "No link request" }
        val prompt = checkNotNull(prompts[sessionId]) { "No link request $sessionId" }
        val now = clock()
        check(pairing.canOfferLink(sessionId, now)) { "This request cannot be linked now" }
        val (groupId, key) = groups.confirmTarget()
        prompt.label = label
        publishPairing(pairing.confirmLink(sessionId, groupId, key, now))
        prompt.state = DeviceLinkPrompt.State.Linking
        refreshState()
    }

    /** The user says the codes differ, or dismisses the request. */
    suspend fun rejectLink(sessionId: HexKey) =
        mutex.withLock {
            val pairing = existing ?: return@withLock
            val prompt = prompts[sessionId] ?: return@withLock
            val now = clock()
            val cancel = if (prompt.state is DeviceLinkPrompt.State.Comparing) pairing.rejectLink(sessionId, now) else pairing.cancel(sessionId)
            cancel?.let { publishPairing(it) }
            prompt.state = DeviceLinkPrompt.State.Ended(if (cancel?.reason == CancelReason.MISMATCHED_CODE) DeviceLinkEndReason.CODES_DIFFER else DeviceLinkEndReason.CANCELLED)
            refreshState()
        }

    private suspend fun approveConfirmedLink(
        link: ConfirmedLink,
        prompt: Prompt,
        now: Long,
    ) {
        val keyPackage = checkNotNull(prompt.keyPackage)
        val slot = checkNotNull(keyPackage.dTag().takeIf { it.isNotEmpty() }) { "The KeyPackage has no slot" }
        val applied = groups.applyStaged(groups.approve(link, Base64.decode(keyPackage.content), slot, prompt.label, now))
        applied.welcome?.let { transport.publishToSelf(it.giftWrapEvent) }
        Log.d(TAG) { "approved ${slot.take(8)} into the device group; Welcome ${if (applied.welcome != null) "sent" else "none"}" }
    }

    // =============================================================================================
    // Timers
    // =============================================================================================

    /**
     * Runs every due device-group duty: call on a timer (about once a minute) and after a sync.
     * Nothing runs once a sibling unlinked this device (MDK's `maintenance_passes`).
     */
    suspend fun tick() =
        mutex.withLock {
            if (!started) return@withLock
            val now = clock()
            runStep("pairing expiry") { expirePairing(now) }
            val t = groups.trust()
            if (!t.unlinked && t.trustsDeviceGroup) {
                runStep("refresh") { groups.refresh() }
                runStep("first commit") { firstCommitIfJoined() }
                runStep("heartbeat") {
                    groups.heartbeatDue(now)?.let { if (transport.publishGroupEvent(it)) groups.heartbeatSent(now) }
                }
                runStep("deadlines") { groups.checkLinkDeadlines(now)?.let { groups.applyStaged(it) } }
                runStep("inactivity") { groups.stageInactivity(now)?.let { groups.applyStaged(it) } }
                runStep("removed records") { groups.expireRemovedRecords(now)?.let { groups.applyStaged(it) } }
                runStep("departures") {
                    val gone = groups.pendingDepartures()
                    gone.forEach { groups.applyStaged(groups.stageRemoveDevice(it, now)) }
                    if (gone.isNotEmpty()) publishAll(groups.removeGoneDevicesFromChats())
                }
                runStep("top up") { publishAll(groups.topUpSiblings(now)) }
                runStep("new devices") { addNewDeviceToChatsAndReport(now) }
                runStep("gap fill") {
                    val fill = groups.fillMissingChats(now)
                    publishAll(fill.claims)
                    fill.adds.forEach { publishSiblingAdd(it, now) }
                }
                runStep("sibling welcome repairs") { publishAll(groups.siblingWelcomeRepairs(now)) }
                runStep("broadcast") { publishAll(groups.broadcastDue(now)) }
                if (now - lastRetag > RETAG_EVERY_SECS) {
                    lastRetag = now
                    runStep("retag") { publishAll(groups.retagOwnLeaves()) }
                }
            }
            if (!t.unlinked && now - lastSignInCheck > SIGN_IN_CHECK_EVERY_SECS) {
                lastSignInCheck = now
                runStep("sign-ins") { checkSignIns() }
            }
            runStep("outbox") { publishAll(groups.drainOutbox()) }
            refreshState()
        }

    private suspend fun expirePairing(now: Long) {
        newDevice?.let { session ->
            session.expire(now)?.let { cancel ->
                publishPairing(cancel)
                groups.cancelLink(session.sessionId)
                newDeviceState = NewDeviceLinkState.Ended(DeviceLinkEndReason.TIMED_OUT)
                newDevice = null
            }
        }
        existing?.let { pairing ->
            pairing.expire(now).forEach { cancel ->
                publishPairing(cancel)
                prompts[cancel.sessionId]?.state = DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.TIMED_OUT)
            }
            prompts.forEach { (id, p) ->
                if (p.state is DeviceLinkPrompt.State.Comparing) comparingState(pairing, id, now)?.let { p.state = it }
            }
            // A request whose KeyPackage was not found yet, or held back while another was live,
            // is answered once it can be.
            val live =
                pairing
                    .liveRequests(now)
                    .filter { !it.answered }
                    .map { it.sessionId }
                    .toSet()
            prompts.values
                .filter { it.request.sessionId in live && (it.state == DeviceLinkPrompt.State.Preparing || it.state == DeviceLinkPrompt.State.CompetingRequests) }
                .forEach { answer(pairing, it, now) }
        }
        // A link this device waits on that its Welcome never answered.
        groups.trust().pendingLink?.let { pending ->
            if (now >= pending.waitUntil) {
                groups.cancelLink(pending.link.sessionId)
                if (newDeviceState == NewDeviceLinkState.Joining) newDeviceState = NewDeviceLinkState.Ended(DeviceLinkEndReason.TIMED_OUT)
            }
        }
    }

    /** For every device this one approved whose link is made: add it to the chats, and report what to expect once. */
    private suspend fun addNewDeviceToChatsAndReport(now: Long) {
        val t = groups.trust()
        for (approval in t.approvals) {
            if (approval.outcome != DeviceGroupTrust.Outcome.MADE) continue
            groups.addNewDeviceToChats(approval.slot, now).forEach { publishSiblingAdd(it, now) }
            if (inventorySent.add(approval.slot)) publishAll(groups.chatInventory(approval.slot, now, now))
            // History deliveries (kind 473) for the chats just added would go here.
        }
    }

    private suspend fun checkSignIns() {
        val packages = transport.ownKeyPackages() ?: return
        val publications = packages.mapNotNull { kp -> kp.dTag().takeIf { it.isNotEmpty() }?.let { MarmotDeviceGroupManager.Publication(it, kp.createdAt) } }
        groups.recordFirstCheck(publications.map { it.slot })
        val classes = groups.classifySignIns(publications)
        val signIns =
            publications.mapNotNull { pub ->
                val kind = classes[pub.slot] ?: return@mapNotNull null
                if (kind == MarmotDeviceGroupManager.SignInClass.OWN || kind == MarmotDeviceGroupManager.SignInClass.LINKED) return@mapNotNull null
                SignIn(pub.slot, pub.createdAt, kind)
            }
        _state.value = _state.value.copy(signIns = signIns)
        lastSignIns = signIns
    }

    private var lastSignIns: List<SignIn> = emptyList()

    // =============================================================================================
    // Devices
    // =============================================================================================

    /**
     * Removes a sibling ([slot]) from the device group, takes its leaves out of every chat, and
     * deletes its KeyPackage slot (`device-group.md` "Removing a device"). False when no relay
     * confirmed the removal Commit: nothing changed, and the slot is kept.
     */
    suspend fun removeDevice(slot: HexKey): Boolean =
        mutex.withLock {
            val now = clock()
            require(slot != groups.trust().ownSlot) { "Sign out to remove this device" }
            val applied = groups.applyStaged(groups.stageRemoveDevice(slot, now))
            if (applied.event == null) {
                Log.w(TAG) { "no relay confirmed the removal of ${slot.take(8)}" }
                refreshState()
                return@withLock false
            }
            publishAll(groups.removeGoneDevicesFromChats())
            runStep("slot deletion") { deleteSlot(slot) }
            refreshState()
            true
        }

    /** The removed device's KeyPackage slot goes too, so inviters stop finding it. */
    private suspend fun deleteSlot(slot: HexKey) {
        val published = transport.ownKeyPackages().orEmpty().filter { it.dTag() == slot }
        if (published.isEmpty()) return
        val deletion = signer.sign(DeletionRequestEvent.build(published))
        if (!transport.publishOwnEvent(deletion)) Log.w(TAG) { "no relay accepted the deletion of slot ${slot.take(8)}" }
    }

    /**
     * Signs this device out through its siblings: they remove it (`leaving`). False when no relay
     * confirmed the Commit, so the siblings will not hear of it.
     */
    suspend fun signOutThroughSiblings(): Boolean =
        mutex.withLock {
            val applied = groups.applyStaged(groups.stageSignOut())
            refreshState()
            applied.event != null
        }

    /** "This wasn't me" for a sign-in in [slot]: records the rejection in the roster. */
    suspend fun rejectSignIn(slot: HexKey) =
        mutex.withLock {
            groups.rejectSignIn(slot, clock())?.let { groups.applyStaged(it) }
            lastSignInCheck = 0
            refreshState()
        }

    // =============================================================================================
    // Chats
    // =============================================================================================

    /**
     * Creates a chat that already includes this account's linked devices (their private
     * KeyPackages) and [invites]. Publishes the invitees' Welcomes and the sibling Welcomes, and asks
     * siblings it had no KeyPackage for to hand some over.
     */
    suspend fun createChat(
        nostrGroupId: HexKey,
        relays: List<String>,
        profile: GroupProfileV1?,
        invites: List<MarmotManager.MemberInvite> = emptyList(),
    ): MarmotDeviceGroupManager.SiblingAdd =
        mutex.withLock {
            val now = clock()
            val add = groups.createChat(nostrGroupId, relays, profile, invites, now)
            publishSiblingAdd(add, now)
            refreshState()
            add
        }

    private suspend fun publishSiblingAdd(
        add: MarmotDeviceGroupManager.SiblingAdd,
        now: Long,
    ) {
        add.result.welcomes.forEach { transport.publishWelcome(it) }
        publishAll(add.deviceGroupEvents)
        add.missing.forEach { slot -> transport.publishGroupEvent(groups.requestKeyPackages(slot, REQUESTED_KEY_PACKAGES, now)) }
    }

    /** Before the user's Leave of [nostrGroupId]: the decline that takes siblings out of it too. */
    suspend fun beforeLeave(nostrGroupId: HexKey) =
        mutex.withLock {
            runStep("record leave") { publishAll(groups.recordLeave(nostrGroupId, clock())) }
        }

    /** The user read up to [eventId] (of [epoch], sent at [createdAt]) in [nostrGroupId]. */
    suspend fun markRead(
        nostrGroupId: HexKey,
        epoch: Long,
        createdAt: Long,
        eventId: HexKey,
    ) = mutex.withLock {
        runStep("mark read") { publishAll(groups.markRead(nostrGroupId, epoch, createdAt, eventId, clock())) }
    }

    /** Pin, mute or archive [nostrGroupId] on every device of the account. */
    suspend fun changeSetting(
        nostrGroupId: HexKey,
        setting: ChatSetting,
        value: Boolean,
    ) = mutex.withLock {
        runStep("setting") { publishAll(groups.changeSetting(nostrGroupId, setting.wireName, value.toString(), clock())) }
    }

    enum class ChatSetting(
        val wireName: String,
    ) {
        PIN("pin"),
        MUTE("mute"),
        ARCHIVE("archive"),
    }

    /**
     * Before publishing [event], a KeyPackage of this device: a linked device names it in its roster
     * entry first (`device-group.md` "Slot announcements"), so siblings do not read it as a sign-in.
     */
    suspend fun beforeKeyPackagePublish(event: KeyPackageEvent) =
        mutex.withLock {
            if (!started || !groups.trust().trustsDeviceGroup) return@withLock
            val ref = KeyPackageUtils.decodeKeyPackage(Base64.decode(event.content)).reference().toHexKey()
            if (groups.announcementNeeded(ref)) groups.applyStaged(groups.stageAnnouncement(ref))
        }

    // =============================================================================================
    // Plumbing
    // =============================================================================================

    private suspend fun publishPairing(message: PairingMessage) {
        if (!transport.publishToSelf(PairingDelivery.wrap(message, signer, clock()))) {
            Log.w(TAG) { "no relay accepted pairing kind ${message.kind}" }
        }
    }

    private suspend fun publishAll(events: List<OutboundGroupEvent>) {
        events.forEach { transport.publishGroupEvent(it) }
    }

    private suspend inline fun runStep(
        name: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "device-group step '$name' failed", e)
        }
    }

    private suspend fun refreshState() {
        val t = groups.trust()
        val roster = if (t.trustsDeviceGroup && !t.unlinked) groups.roster() else null
        val devices = roster?.devices.orEmpty().map { LinkedDevice.of(it, t.ownSlot, groups.lastActivity(it.slot)) }
        val now = clock()
        prompts.forEach { (id, p) ->
            if (p.state is DeviceLinkPrompt.State.Comparing) existing?.let { comparingState(it, id, now) }?.let { p.state = it }
        }
        _state.value =
            MarmotDevicesSnapshot(
                ownSlot = t.ownSlot,
                linked = t.trustsDeviceGroup && !t.unlinked,
                unlinked = t.unlinked,
                devices = devices,
                newDevice = newDeviceState,
                prompts = prompts.map { (id, p) -> DeviceLinkPrompt(id, p.keyPackage?.dTag(), p.requestedAt, p.state) },
                remainingChats = if (t.trustsDeviceGroup) groups.remainingExpectedChats() else null,
                signIns = lastSignIns,
            )
    }

    companion object {
        private const val TAG = "MarmotDevices"

        /** How many private KeyPackages to ask a sibling for when a chat had none of its. */
        const val REQUESTED_KEY_PACKAGES = 10

        /** `retagOwnLeaves` spreads its UpdatePaths out; once every six hours is plenty. */
        const val RETAG_EVERY_SECS = 6 * 60 * 60L

        const val SIGN_IN_CHECK_EVERY_SECS = 15 * 60L

        /** Pairing messages kept for [start] when they arrive before it. */
        const val MAX_EARLY_PAIRING = 64
    }
}
