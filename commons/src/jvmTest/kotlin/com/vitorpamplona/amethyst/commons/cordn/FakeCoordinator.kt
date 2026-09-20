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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.cordn.spec00Coordinator.AvailableKeyPackage
import com.vitorpamplona.quartz.cordn.spec00Coordinator.ConsumedJoinRequestRef
import com.vitorpamplona.quartz.cordn.spec00Coordinator.ConsumedWelcomeRef
import com.vitorpamplona.quartz.cordn.spec00Coordinator.GroupMessage
import com.vitorpamplona.quartz.cordn.spec00Coordinator.ICoordinator
import com.vitorpamplona.quartz.cordn.spec00Coordinator.JoinRequest
import com.vitorpamplona.quartz.cordn.spec00Coordinator.PendingWelcome
import com.vitorpamplona.quartz.cordn.spec00Coordinator.PostedMessage
import com.vitorpamplona.quartz.cordn.spec00Coordinator.PublishedKeyPackage
import com.vitorpamplona.quartz.cordn.spec00Coordinator.TakenKeyPackage
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A coordinator that behaves, in memory.
 *
 * It models the two things `spec/00.md` makes the coordinator responsible for
 * and that the manager's correctness depends on: a **monotonic per-group
 * cursor** and **per-account Welcome inboxes**. Everything else it stores
 * verbatim, which is also what a real one does — §8: it cannot parse payloads
 * and does not try.
 *
 * Wire shapes are not this fake's job. Those are pinned against cordn's own zod
 * schemas in `quartz`'s `CoordinatorContractVectorTest`; here the point is the
 * manager's behaviour on top of them.
 */
class FakeCoordinator(
    /** The account the manager under test is calling as, for Welcome routing. */
    private val callerPubKey: HexKey,
) : ICoordinator {
    /**
     * A second account's view of *this* coordinator's storage.
     *
     * The caller is fixed at construction because Welcome routing depends on
     * it, so two accounts on one coordinator need two instances over one set
     * of maps rather than one instance with a mutable caller. Everything
     * stored is shared; only who is asking differs — which is exactly the
     * situation a join request exists to handle.
     */
    fun viewAs(otherPubKey: HexKey): FakeCoordinator =
        FakeCoordinator(otherPubKey).also {
            it.keyPackages = keyPackages
            it.welcomes = welcomes
            it.streams = streams
            it.joinRequests = joinRequests
        }

    class StoredKeyPackage(
        val pubKey: HexKey,
        val keyPackageRef: String,
        val base64: String,
        val publicationEvent: Event,
        val lastResort: Boolean = false,
    )

    var keyPackages = mutableMapOf<String, StoredKeyPackage>()
    var welcomes = mutableMapOf<HexKey, MutableList<PendingWelcome>>()
    var streams = mutableMapOf<String, MutableList<GroupMessage>>()
    var joinRequests = mutableMapOf<String, MutableList<JoinRequest>>()

    /** Every method name called, in order. */
    val calls = mutableListOf<String>()

    private var cursor = 0L
    private var clock = 1_757_000_000L

    /** Fails the next [failNext] calls, to exercise the health surface. */
    var failNext = 0

    /** Pre-loads a KeyPackage as if its owner had published it. */
    fun seedKeyPackage(stored: StoredKeyPackage) {
        keyPackages[stored.keyPackageRef] = stored
    }

    /** Everything posted to [gid], oldest first. */
    fun posted(gid: String): List<String> = streams[gid].orEmpty().map { it.sealedBase64 }

    private fun record(method: String) {
        calls += method
        if (failNext > 0) {
            failNext--
            throw IllegalStateException("$method failed")
        }
    }

    override suspend fun publishKeyPackage(
        keyPackageRef: String,
        keyPackageBase64: String,
    ): PublishedKeyPackage {
        record("kp_publish")
        return PublishedKeyPackage(keyPackageRef, false, clock++)
    }

    override suspend fun removeKeyPackages(keyPackageRefs: List<String>): List<String> {
        record("kp_remove")
        return keyPackageRefs.filter { keyPackages.remove(it) != null }
    }

    override suspend fun listKeyPackages(): List<AvailableKeyPackage> {
        record("kp_list")
        return keyPackages.values.map { AvailableKeyPackage(it.pubKey, it.keyPackageRef, it.lastResort, clock) }
    }

    override suspend fun takeKeyPackage(id: String): TakenKeyPackage? {
        record("kp_take")
        // `id` accepts a ref or an account hex, like the real one.
        val stored = keyPackages[id] ?: keyPackages.values.firstOrNull { it.pubKey == id } ?: return null
        return TakenKeyPackage(stored.pubKey, stored.keyPackageRef, stored.lastResort, clock++, stored.publicationEvent)
    }

    override suspend fun storeWelcome(
        targetPubKey: HexKey,
        keyPackageRef: String,
        welcomeBase64: String,
        after: Long?,
    ): Long {
        record("welcome_store")
        val at = clock++
        welcomes.getOrPut(targetPubKey) { mutableListOf() } += PendingWelcome(keyPackageRef, welcomeBase64, at, after)
        return at
    }

    override suspend fun takeWelcomes(consumed: List<ConsumedWelcomeRef>): List<PendingWelcome> {
        record("welcome_take")
        val mine = welcomes[callerPubKey].orEmpty()
        consumed.forEach { ack -> welcomes[callerPubKey]?.removeAll { it.keyPackageRef == ack.keyPackageRef && it.at == ack.at } }
        return mine.toList()
    }

    override suspend fun storeJoinRequest(
        gid: String,
        keyPackageRef: String,
    ): Long {
        record("join_request_store")
        val at = clock++
        // Keyed by group, not by caller: any member of the group can serve it,
        // which is what `spec/01.md` §5.3 leaves open.
        joinRequests.getOrPut(gid) { mutableListOf() } += JoinRequest(gid, callerPubKey, keyPackageRef, at)
        return at
    }

    override suspend fun takeJoinRequests(
        gids: List<String>,
        consumed: List<ConsumedJoinRequestRef>,
    ): List<JoinRequest> {
        record("join_request_take_many")
        consumed.forEach { ack ->
            joinRequests[ack.gid]?.removeAll { it.gid == ack.gid && it.pubKey == ack.pubKey && it.at == ack.at }
        }
        return gids.flatMap { joinRequests[it].orEmpty() }
    }

    override suspend fun postMessage(
        gid: String,
        sealedBase64: String,
    ): PostedMessage {
        record("msg_post")
        // Monotonic across the whole coordinator, which is stricter than
        // spec/00.md §4 requires (per group) and so a safe stand-in.
        val assigned = ++cursor
        streams.getOrPut(gid) { mutableListOf() } += GroupMessage(gid, assigned, sealedBase64, clock++)
        return PostedMessage(gid, assigned, clock)
    }

    override suspend fun fetchMessages(cursors: Map<String, Long?>): List<GroupMessage> {
        record("msg_fetch_many")
        return cursors.entries
            .flatMap { (gid, after) -> streams[gid].orEmpty().filter { it.cursor > (after ?: 0L) } }
            .sortedBy { it.cursor }
    }

    override suspend fun subscribeMessages(
        cursors: Map<String, Long?>,
        timeoutMs: Long,
        onMessage: (GroupMessage) -> Unit,
    ) {
        record("msg_sub_many")
        fetchMessages(cursors).forEach(onMessage)
    }
}
