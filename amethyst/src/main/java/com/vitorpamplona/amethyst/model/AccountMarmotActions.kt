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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * Marmot (MLS encrypted groups) orchestration for an [Account]: group create/
 * leave/reset, member add/remove via key-package fetch, admin grant/revoke,
 * metadata updates, group messaging, and key-package publishing. MLS state
 * lives in [MarmotManager]; this class wires it to the account's signer, relay
 * client, and relay lists. Functions live here (not a ViewModel) so headless
 * callers - notification receivers, background workers - can drive them.
 */
class AccountMarmotActions(
    private val account: Account,
) {
    /**
     * Resolve the relay set for a Marmot group. Prefer the relays carried in
     * the MLS GroupContext metadata so every member converges on the same
     * canonical set; fall back to the account's outbox relays if the group
     * has none (e.g. a group joined before MIP-01 metadata existed).
     *
     * Lives on Account (not AccountViewModel) so that headless callers —
     * notifications' BroadcastReceiver, background workers — can resolve
     * relays without spinning up a ViewModel.
     */
    fun marmotGroupRelays(nostrGroupId: HexKey): Set<NormalizedRelayUrl> {
        val groupRelays =
            account.marmotManager
                ?.groupMetadata(nostrGroupId)
                ?.relays
                ?.mapNotNull {
                    com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
                        .normalizeOrNull(it)
                }?.toSet()
        return if (!groupRelays.isNullOrEmpty()) groupRelays else account.outboxRelays.flow.value
    }

    /**
     * Send a message to a Marmot MLS group.
     * Encrypts the inner event and publishes the GroupEvent to group relays.
     */
    suspend fun sendMarmotGroupMessage(
        nostrGroupId: HexKey,
        innerEvent: Event,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        Log.d("MarmotDbg") {
            "sendMarmotGroupMessage: group=${nostrGroupId.take(8)}… innerKind=${innerEvent.kind} innerId=${innerEvent.id.take(8)}… " +
                "→ ${groupRelays.size} relay(s): ${groupRelays.map { it.url }}"
        }
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val outbound = manager.buildGroupMessage(nostrGroupId, innerEvent)
        Log.d("MarmotDbg") {
            "sendMarmotGroupMessage: built outer kind:${outbound.signedEvent.kind} id=${outbound.signedEvent.id.take(8)}…"
        }
        // Link the envelope to the inner message we just encrypted so relay
        // OK acceptances drill down to the note the chat renders (see
        // LocalCache.addRelayToNoteAndInners).
        outbound.signedEvent.innerEventId = innerEvent.id
        account.cache.justConsumeMyOwnEvent(outbound.signedEvent)
        // Sending a message moves the group out of "New Requests" into
        // "Known" — do this eagerly before relay round-trip so the UI
        // updates immediately.
        account.marmotGroupList.markAsKnown(nostrGroupId)
        if (groupRelays.isEmpty()) {
            Log.w("MarmotDbg") {
                "sendMarmotGroupMessage: NO group relays for group=${nostrGroupId.take(8)}… — message will be silently dropped"
            }
        }
        account.client.publish(outbound.signedEvent, groupRelays)
    }

    /**
     * Fetch a user's KeyPackage from relays and add them to a Marmot group.
     * Returns a status message describing the outcome.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun fetchKeyPackageAndAddMember(
        nostrGroupId: HexKey,
        memberPubKey: HexKey,
    ): String {
        Log.d("MarmotDbg") {
            "fetchKeyPackageAndAddMember: group=${nostrGroupId.take(8)}… member=${memberPubKey.take(8)}…"
        }
        val manager = account.marmotManager ?: return "Error: Marmot not initialized"
        if (!account.isWriteable()) return "Error: Account is read-only"

        // Per MIP-00, invitees advertise the relays that host their
        // KeyPackages in a kind:10051 KeyPackageRelayListEvent. Look
        // there first, then fall back to the invitee's NIP-65 outbox
        // (where KeyPackages typically also land), and finally union
        // with our own outbox so we still find packages that ended up
        // on a shared relay.
        val myOutbox = account.outboxRelays.flow.value
        val memberKeyPackageRelays =
            (
                account.cache
                    .getAddressableNoteIfExists(
                        com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
                            .createAddress(memberPubKey),
                    )?.event as? com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
            )?.relays()?.toSet().orEmpty()
        val memberOutbox =
            account.cache
                .getOrCreateUser(memberPubKey)
                .outboxRelays()
                ?.toSet()
                .orEmpty()
        val fetchRelays =
            com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
                .fetchRelaysFor(memberKeyPackageRelays, memberOutbox, myOutbox)

        Log.d("MarmotDbg") {
            "fetchKeyPackageAndAddMember: querying ${fetchRelays.size} relay(s) for ${memberPubKey.take(8)}… KeyPackage " +
                "(memberKeyPackageRelays=${memberKeyPackageRelays.size}, memberOutbox=${memberOutbox.size}, myOutbox=${myOutbox.size}): ${fetchRelays.map { it.url }}"
        }

        val event =
            com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
                .fetchKeyPackage(account.client, memberPubKey, fetchRelays)

        if (event == null) {
            Log.w("MarmotDbg") {
                "fetchKeyPackageAndAddMember: NO KeyPackage found for ${memberPubKey.take(8)}… on any of ${fetchRelays.size} relay(s)"
            }
            return "Error: No KeyPackage found for this user. They may not have published one yet."
        }

        Log.d("MarmotDbg") {
            "fetchKeyPackageAndAddMember: got KeyPackage event id=${event.id.take(8)}… kind=${event.kind} authored=${event.pubKey.take(8)}…"
        }

        val keyPackageBase64 = event.keyPackageBase64()
        if (keyPackageBase64.isBlank()) {
            Log.w("MarmotDbg") { "fetchKeyPackageAndAddMember: KeyPackage event has empty content" }
            return "Error: KeyPackage event has empty content"
        }

        // The relays embedded in the WelcomeEvent tell the new member
        // where to subscribe for subsequent GroupEvents. Use our own
        // outbox — that's where we will publish them.
        val groupRelays = myOutbox.toList()

        Log.d("MarmotDbg") {
            "fetchKeyPackageAndAddMember: addMarmotGroupMember → groupRelays=${groupRelays.size}: ${groupRelays.map { it.url }}"
        }

        addMarmotGroupMember(
            nostrGroupId = nostrGroupId,
            keyPackageEvent = event,
            groupRelays = groupRelays,
        )

        return "Success: Member added to group"
    }

    /**
     * Add a member to a Marmot MLS group.
     * Publishes the commit GroupEvent, then sends the Welcome gift wrap.
     */
    suspend fun addMarmotGroupMember(
        nostrGroupId: HexKey,
        keyPackageEvent: com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent,
        groupRelays: List<NormalizedRelayUrl>,
    ) {
        val memberPubKey = keyPackageEvent.pubKey
        Log.d("MarmotDbg") {
            "addMarmotGroupMember: group=${nostrGroupId.take(8)}… member=${memberPubKey.take(8)}… " +
                "groupRelays=${groupRelays.size}"
        }
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val (commitEvent, welcomeDelivery) =
            manager.addMember(
                nostrGroupId = nostrGroupId,
                keyPackageEvent = keyPackageEvent,
                relays = groupRelays,
            )

        // The MLS commit has already been applied to the local group state —
        // surface the new member list in the chatroom now so observers (e.g.
        // MarmotGroupInfoScreen) update without waiting for our own commit to
        // loop back through the relay.
        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)

        Log.d("MarmotDbg") {
            "addMarmotGroupMember: built commit kind=${commitEvent.signedEvent.kind} id=${commitEvent.signedEvent.id.take(8)}… " +
                "welcomeDelivery=${if (welcomeDelivery != null) "present(giftWrapId=${welcomeDelivery.giftWrapEvent.id.take(8)}…)" else "null"}"
        }

        // Publish commit first (critical ordering)
        Log.d("MarmotDbg") {
            "addMarmotGroupMember: publishing commit kind:${commitEvent.signedEvent.kind} to ${groupRelays.size} relay(s): ${groupRelays.map { it.url }}"
        }
        account.client.publish(commitEvent.signedEvent, groupRelays.toSet())

        // Then send the Welcome gift wrap to the new member.
        //
        // Use the same delivery path that NIP-17 DMs (kind:1059) take —
        // computeRelayListToBroadcast() — which has fallbacks for kind:10050
        // → NIP-65 read → relay hints. Empirically, NIP-17 DMs reach the
        // invitee, so this path is the one we know works. We also union
        // with our own outbox + the recipient's dmInboxRelays() as a
        // belt-and-braces measure in case the cache hasn't been hydrated
        // yet for this contact.
        if (welcomeDelivery != null) {
            val computed = account.broadcaster.computeRelayListToBroadcast(welcomeDelivery.giftWrapEvent)
            val recipientInbox =
                account.cache
                    .getOrCreateUser(memberPubKey)
                    .dmInboxRelays()
                    .orEmpty()
            val relayList = computed + account.outboxRelays.flow.value + recipientInbox
            Log.d("MarmotDbg") {
                "addMarmotGroupMember: welcome gift wrap relay sources " +
                    "computeRelayListToBroadcast=${computed.size} myOutbox=${account.outboxRelays.flow.value.size} " +
                    "recipientInbox=${recipientInbox.size} → union=${relayList.size}"
            }
            if (relayList.isEmpty()) {
                Log.w("MarmotDbg") {
                    "addMarmotGroupMember: NO relays to deliver welcome gift wrap to ${memberPubKey.take(8)}… — welcome will be silently dropped"
                }
            } else {
                Log.d("MarmotDbg") {
                    "addMarmotGroupMember: publishing welcome gift wrap id=${welcomeDelivery.giftWrapEvent.id.take(8)}… " +
                        "kind:${welcomeDelivery.giftWrapEvent.kind} → ${relayList.size} relay(s): ${relayList.map { it.url }}"
                }
            }
            account.client.publish(welcomeDelivery.giftWrapEvent, relayList)
        } else {
            Log.w("MarmotDbg") {
                "addMarmotGroupMember: welcomeDelivery is NULL — invitee ${memberPubKey.take(8)}… will receive nothing!"
            }
        }
    }

    /**
     * Relays where this account publishes kind:30443 KeyPackage events.
     * Per MIP-00: prefer kind:10051 KeyPackage Relay List; fall back to NIP-65 outbox.
     */
    fun keyPackagePublishRelays(): Set<NormalizedRelayUrl> =
        com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
            .publishRelaysFor(account.keyPackageRelayList.flow.value, account.outboxRelays.flow.value)

    /**
     * Publish or rotate KeyPackage events.
     */
    suspend fun publishMarmotKeyPackages() {
        val manager =
            account.marmotManager ?: run {
                Log.w("MarmotDbg") { "publishMarmotKeyPackages: account.marmotManager is NULL — no-op" }
                return
            }
        if (!account.isWriteable()) {
            Log.w("MarmotDbg") { "publishMarmotKeyPackages: account is not writeable — no-op" }
            return
        }

        val relays = keyPackagePublishRelays()
        val needsRotation = manager.needsKeyPackageRotation()
        Log.d("MarmotDbg") {
            "publishMarmotKeyPackages: needsRotation=$needsRotation relays=${relays.size}"
        }

        if (needsRotation) {
            val rotatedEvents = manager.rotateConsumedKeyPackages(relays.toList())
            Log.d("MarmotDbg") {
                "publishMarmotKeyPackages: rotateConsumedKeyPackages produced ${rotatedEvents.size} event(s)"
            }
            rotatedEvents.forEach { event ->
                account.cache.justConsumeMyOwnEvent(event)
                Log.d("MarmotDbg") {
                    "publishMarmotKeyPackages: publishing rotated kind:${event.kind} id=${event.id.take(8)}… " +
                        "→ ${relays.size} relay(s): ${relays.map { it.url }}"
                }
                account.client.publish(event, relays)
            }
        }
    }

    /**
     * Generate and publish initial KeyPackage for this account.
     */
    suspend fun publishMarmotKeyPackage() {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val relays = keyPackagePublishRelays()
        Log.d("MarmotDbg") {
            "publishMarmotKeyPackage: generating + publishing KeyPackage event → ${relays.size} relay(s): ${relays.map { it.url }}"
        }
        val event = manager.generateKeyPackageEvent(relays.toList())
        Log.d("MarmotDbg") {
            "publishMarmotKeyPackage: signed kind:${event.kind} id=${event.id.take(8)}… authored=${event.pubKey.take(8)}…"
        }
        account.cache.justConsumeMyOwnEvent(event)
        account.client.publish(event, relays)
    }

    /**
     * Ensure the local user has at least one active KeyPackage bundle and
     * a published KeyPackage event on relays. Called from [init] after
     * Marmot state has been restored from disk.
     *
     * - If [KeyPackageRotationManager] already has an active bundle (from
     *   the persisted snapshot), we trust the previous session and do
     *   nothing. The matching kind:30443 should already be on relays from
     *   when the bundle was first generated.
     * - Otherwise we generate a fresh bundle (which is now persisted to
     *   disk by [KeyPackageRotationManager.generateKeyPackage]) and
     *   publish the corresponding event.
     *
     * Best-effort: failures are logged but never propagated. We don't want
     * a flaky relay or missing outbox config at startup to crash account
     * initialization.
     */
    internal suspend fun ensureMarmotKeyPackagePublished() {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return
        try {
            val hasBundle = manager.hasActiveKeyPackages()
            Log.d("MarmotDbg") {
                "ensureMarmotKeyPackagePublished: hasActiveKeyPackages=$hasBundle for ${account.signer.pubKey.take(8)}…"
            }
            if (hasBundle) {
                return
            }
            Log.d("MarmotDbg") {
                "ensureMarmotKeyPackagePublished: no active bundle — generating + publishing now"
            }
            publishMarmotKeyPackage()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("MarmotDbg", "ensureMarmotKeyPackagePublished failed: ${e.message}", e)
        }
    }

    /**
     * Check if a KeyPackage has been published in this session.
     * The d-tag is a randomly-generated value stored in the KeyPackageRotationManager's
     * persisted snapshot, so there is no fixed address to query in the cache.
     */
    suspend fun hasPublishedKeyPackage(): Boolean {
        val manager = account.marmotManager ?: return false
        return manager.hasActiveKeyPackages()
    }

    /**
     * Create a new Marmot MLS group.
     */
    suspend fun createMarmotGroup(nostrGroupId: HexKey) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return
        manager.createGroup(nostrGroupId)
        // Creator owns the group — mark it as "known" immediately so it
        // doesn't appear under "New Requests" before the first message.
        account.marmotGroupList.markAsKnown(nostrGroupId)
    }

    /**
     * Leave a Marmot MLS group.
     * Publishes the SelfRemove proposal and removes local state.
     *
     * MIP-01/MIP-03: admins MUST first publish a GroupContextExtensions
     * commit dropping themselves from `admin_pubkeys` before issuing a
     * SelfRemove proposal. Without that, [MlsGroup.selfRemove] throws
     * `IllegalStateException("Admin must self-demote via GroupContextExtensions
     * before SelfRemove (MIP-01)")` and the leave aborts. Demote commit and
     * SelfRemove proposal both go to the same group relays, demote first so
     * peers apply it before they see the SelfRemove.
     */
    suspend fun leaveMarmotGroup(
        nostrGroupId: HexKey,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val metadata = manager.groupMetadata(nostrGroupId)
        if (metadata != null && metadata.adminPubkeys.contains(account.signer.pubKey)) {
            val remaining = metadata.adminPubkeys.filter { it != account.signer.pubKey }.toMutableList()
            // MIP-03 also rejects any GCE commit that leaves the group with zero
            // admins. If we're the only one, promote an arbitrary non-self
            // member to admin before stepping down.
            if (remaining.isEmpty()) {
                val heir =
                    manager
                        .memberPubkeys(nostrGroupId)
                        .map { it.pubkey }
                        .firstOrNull { it != account.signer.pubKey }
                if (heir != null) remaining.add(heir)
            }
            if (remaining.isNotEmpty()) {
                val demoted = metadata.copy(adminPubkeys = remaining)
                val demoteCommit = manager.updateGroupMetadata(nostrGroupId, demoted)
                account.client.publish(demoteCommit.signedEvent, groupRelays)
            }
        }

        val outbound = manager.leaveGroup(nostrGroupId)
        // manager.leaveGroup already wiped MLS state, relay subscriptions and
        // the persisted message log. Drop the in-memory chatroom too — that
        // releases the strong refs to the decrypted inner notes so LocalCache
        // (which holds them weakly) can GC them, and the Notification feed
        // (which iterates account.marmotGroupList.rooms) stops surfacing the group.
        account.marmotGroupList.removeGroup(nostrGroupId)
        account.client.publish(outbound.signedEvent, groupRelays)
    }

    /**
     * User-initiated "nuclear" reset for the Marmot subsystem.
     *
     * Wipes every MLS group, every retained epoch secret, every persisted
     * KeyPackage bundle, every relay subscription and every in-memory
     * chatroom associated with this account. Does NOT broadcast any
     * SelfRemove/leave commits to peers — if the user is in this flow at
     * all, local state may already be unusable and a graceful leave is
     * probably not possible. Peers will see the user as unresponsive until
     * their next commit evicts the stale leaf.
     *
     * A fresh KeyPackage will be republished lazily on the next
     * `ensureMarmotKeyPackagePublished` cycle, so the account remains
     * reachable for future group invites.
     */
    suspend fun resetMarmotState() {
        Log.w("MarmotDbg") { "resetMarmotState(): wiping all Marmot state for ${account.signer.pubKey.take(8)}…" }
        account.marmotManager?.resetAllState()
        for (groupId in account.marmotGroupList.allGroupIds()) {
            account.marmotGroupList.removeGroup(groupId)
        }
    }

    /**
     * Remove a member from a Marmot MLS group.
     * Publishes the commit GroupEvent to group relays.
     */
    suspend fun removeMarmotGroupMember(
        nostrGroupId: HexKey,
        targetLeafIndex: Int,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        Log.d("MarmotDbg") {
            "removeMarmotGroupMember: group=${nostrGroupId.take(8)}… targetLeafIndex=$targetLeafIndex " +
                "groupRelays=${groupRelays.size}"
        }
        val manager =
            account.marmotManager ?: run {
                Log.w("MarmotDbg") { "removeMarmotGroupMember: account.marmotManager is NULL — no-op" }
                return
            }
        if (!account.isWriteable()) {
            Log.w("MarmotDbg") { "removeMarmotGroupMember: account is not writeable — no-op" }
            return
        }

        val outbound = manager.removeMember(nostrGroupId, targetLeafIndex)
        Log.d("MarmotDbg") {
            "removeMarmotGroupMember: built commit kind=${outbound.signedEvent.kind} id=${outbound.signedEvent.id.take(8)}…"
        }
        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)
        Log.d("MarmotDbg") {
            "removeMarmotGroupMember: publishing commit id=${outbound.signedEvent.id.take(8)}… " +
                "to ${groupRelays.size} relay(s): ${groupRelays.map { it.url }}"
        }
        account.client.publish(outbound.signedEvent, groupRelays)
    }

    /**
     * Update a Marmot MLS group's metadata (name, description, etc.).
     * Publishes the commit GroupEvent to group relays.
     */
    suspend fun updateMarmotGroupMetadata(
        nostrGroupId: HexKey,
        metadata: com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val outbound = manager.updateGroupMetadata(nostrGroupId, metadata)
        // The MLS commit has already been applied locally — surface the new
        // metadata in the chatroom now so the UI reflects it without waiting
        // for the relay round-trip.
        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)
        account.client.publish(outbound.signedEvent, groupRelays)
    }

    /**
     * Grant admin privileges to [targetPubKey] in a Marmot MLS group by
     * appending them to `admin_pubkeys` via a GroupContextExtensions commit.
     *
     * No-op if the group has no prior metadata (shouldn't happen outside the
     * first bootstrap commit) or the target is already an admin. Callers
     * must be an admin themselves — the MLS engine enforces this via the
     * MIP-03 authorization gate in `enforceAuthorizedProposalSet`.
     */
    suspend fun grantMarmotGroupAdmin(
        nostrGroupId: HexKey,
        targetPubKey: HexKey,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val metadata = manager.groupMetadata(nostrGroupId) ?: return
        if (metadata.adminPubkeys.contains(targetPubKey)) return

        val outboxRelayStrings =
            account.outboxRelays.flow.value
                .map { it.url }
        val updated =
            metadata
                .copy(adminPubkeys = metadata.adminPubkeys + targetPubKey)
                .withMergedRelays(outboxRelayStrings)
        updateMarmotGroupMetadata(nostrGroupId, updated, groupRelays)
    }

    /**
     * Revoke admin privileges from [targetPubKey]. Rejects any change that
     * would leave the group with zero admins — MIP-03's admin-depletion guard
     * in [com.vitorpamplona.quartz.marmot.mls.group.MlsGroup] would otherwise
     * throw at commit time.
     */
    suspend fun revokeMarmotGroupAdmin(
        nostrGroupId: HexKey,
        targetPubKey: HexKey,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = account.marmotManager ?: return
        if (!account.isWriteable()) return

        val metadata = manager.groupMetadata(nostrGroupId) ?: return
        if (!metadata.adminPubkeys.contains(targetPubKey)) return
        val remaining = metadata.adminPubkeys.filter { it != targetPubKey }
        check(remaining.isNotEmpty()) {
            "Cannot revoke the last admin from a Marmot group (MIP-03)"
        }

        val outboxRelayStrings =
            account.outboxRelays.flow.value
                .map { it.url }
        val updated =
            metadata
                .copy(adminPubkeys = remaining)
                .withMergedRelays(outboxRelayStrings)
        updateMarmotGroupMetadata(nostrGroupId, updated, groupRelays)
    }
}
