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
package com.vitorpamplona.marmotbench

import com.vitorpamplona.amethyst.commons.marmot.MarmotManager
import com.vitorpamplona.amethyst.commons.marmot.ingest
import com.vitorpamplona.quartz.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.runBlocking

/**
 * The quartz half of the head-to-head against MDK's
 * `cgka-engine --bench group_lifecycle`.
 *
 * Case for case, the two sides measure the same span of work: MDK's benches
 * exclude transport crypto and run over in-memory storage, and so do these.
 * Where MDK uses criterion's `iter_batched(.., BatchSize::PerIteration)`, the
 * setup here is likewise outside the measured window — otherwise we would be
 * timing group construction instead of the operation under test.
 *
 * Every body runs through `runBlocking` on the harness thread on purpose. The
 * allocation counter is per-thread, so work handed to another dispatcher would
 * not be counted and the operation would read as cheaper than it is.
 */
class Client(
    val name: String,
) {
    val signer = NostrSignerInternal(KeyPair())
    val manager = MarmotManager(signer, MemStateStore(), MemMessageStore(), MemBundleStore(), publisher = ACCEPTING_RELAY)
}

private fun newGroupId(): HexKey = RandomInstance.bytes(32).toHexKey()

private suspend fun keyPackagesFor(count: Int): Pair<List<Client>, List<KeyPackageEvent>> {
    val invitees = (0 until count).map { Client("invitee-$it") }
    return invitees to invitees.map { it.manager.generateKeyPackageEvent(relays = emptyList()) }
}

/**
 * `create_group` with N invitees — MDK's `bench_create_group`.
 *
 * N Add proposals in ONE commit on both sides, and one Welcome carrying N
 * `EncryptedGroupSecrets`. The shapes are not identical and the comparison
 * should not pretend otherwise: MDK folds the invitees into the founding
 * group (`FoundingGroupCreated`), while we create at epoch 0 and add in a
 * second commit to epoch 1. That costs us one extra commit here, which is a
 * real difference worth seeing rather than hiding by measuring only the add.
 */
fun benchCreateGroup(invitees: Int): BenchResult =
    measure(
        name = "create_group/$invitees invitees",
        iterations = 30,
        warmup = 10,
        setup = {
            runBlocking {
                val alice = Client("alice")
                val (_, kps) = keyPackagesFor(invitees)
                Triple(alice, kps, newGroupId())
            }
        },
    ) { (alice, kps, groupId) ->
        runBlocking {
            alice.manager.createCurrentProfileGroup(
                nostrGroupId = groupId,
                relays = listOf(BENCH_RELAY),
                profile = GroupProfileV1("bench", ""),
            )
            if (kps.isNotEmpty()) alice.manager.addMembers(groupId, kps, emptyList())
        }
    }

/**
 * A group with [members] invitees already joined, at epoch 1.
 *
 * Built once per benchmark rather than per iteration where the operation under
 * test does not consume it, because assembling a 32-member group costs more
 * than everything being measured.
 */
private suspend fun groupWithMembers(members: Int): Triple<Client, List<Client>, HexKey> {
    val alice = Client("alice")
    val groupId = newGroupId()
    alice.manager.createCurrentProfileGroup(groupId, listOf(BENCH_RELAY), GroupProfileV1("bench", ""))
    val invitees = (0 until members).map { Client("member-$it") }
    if (invitees.isNotEmpty()) {
        val kps = invitees.map { it.manager.generateKeyPackageEvent(relays = emptyList()) }
        val (_, welcomes) = alice.manager.addMembers(groupId, kps, emptyList())
        welcomes.forEach { delivery ->
            invitees
                .first { it.signer.pubKey == delivery.recipientPubKey }
                .manager
                .ingest(delivery.giftWrapEvent)
        }
    }
    return Triple(alice, invitees, groupId)
}

/**
 * `ingest_commit/N` — receiving someone else's Commit.
 *
 * Nothing in this suite measured this, and neither does MDK's. It is the one
 * operation every member pays on every membership or settings change, and the
 * one that genuinely scales with group size: the UpdatePath it carries has a
 * node per level of the ratchet tree, so the receiver's cost grows with
 * log2(N) HPKE opens on top of the tree bookkeeping.
 *
 * Setup produces a FRESH commit per iteration — the group is built once, then
 * Alice changes the profile each time — because ingesting the same commit
 * twice is a no-op and would measure the dedup path instead.
 */
fun benchIngestCommit(members: Int): BenchResult {
    val (alice, invitees, groupId) =
        runBlocking { groupWithMembers(members) }
    val bob = invitees.first()
    var round = 0
    return measure(
        name = "ingest_commit/$members members",
        iterations = if (members >= 32) 40 else 100,
        warmup = if (members >= 32) 10 else 30,
        setup = {
            runBlocking {
                round++
                alice.manager.setGroupProfile(groupId, "bench-$round", "", emptyList())
            }
        },
    ) { commit ->
        runBlocking { bob.manager.ingest(commit.signedEvent) }
    }
}

/** `join_welcome` — MDK's `bench_join_welcome`. The invitee's side of the add. */
fun benchJoinWelcome(): BenchResult =
    measure(
        name = "join_welcome",
        iterations = 50,
        warmup = 15,
        setup = {
            runBlocking {
                val alice = Client("alice")
                val bob = Client("bob")
                val groupId = newGroupId()
                alice.manager.createCurrentProfileGroup(groupId, listOf(BENCH_RELAY), GroupProfileV1("bench", ""))
                val kp = bob.manager.generateKeyPackageEvent(relays = emptyList())
                val (_, welcome) = alice.manager.addMember(groupId, kp, emptyList())
                bob to welcome!!.giftWrapEvent
            }
        },
    ) { (bob, wrap) ->
        runBlocking { bob.manager.ingest(wrap as GiftWrapEvent) }
    }

/**
 * `send_app_message/N` — MDK's `bench_app_message_send`. Encrypt + persist.
 *
 * Parameterised by group size, which the single-member version of this
 * benchmark hid. The MLS half is O(1) in the member count — an application
 * message is sealed under the sender's own ratchet and never touches the tree
 * — but `MlsGroupManager.encrypt` calls `persistGroup`, and THAT serialises
 * the whole group state, ratchet tree included, on every send. So the cost per
 * message has a term that grows with the group while the cryptography does
 * not, and a one-member number is the flattering one.
 *
 * A fresh group per iteration, as before: sending accumulates rows in the
 * message store, and reusing one group would measure that growth instead.
 */
fun benchSendAppMessage(members: Int): BenchResult =
    measure(
        name = "send_app_message/$members members",
        iterations = if (members >= 32) 50 else 300,
        warmup = if (members >= 32) 15 else 100,
        setup = {
            runBlocking {
                val (alice, _, groupId) = groupWithMembers(members)
                alice to groupId
            }
        },
    ) { (alice, groupId) ->
        runBlocking { alice.manager.buildTextMessage(groupId, PAYLOAD) }
    }

/**
 * `ingest_app_message/N` — MDK's `bench_app_message_ingest`. Decrypt + persist.
 *
 * Parameterised for the same reason as [benchSendAppMessage]: decryption is
 * O(1) in the member count, but the receiver persists its group state too, and
 * that is not.
 */
fun benchIngestAppMessage(members: Int): BenchResult =
    measure(
        name = "ingest_app_message/$members members",
        iterations = if (members >= 32) 50 else 200,
        warmup = if (members >= 32) 15 else 60,
        setup = {
            runBlocking {
                val (alice, invitees, groupId) = groupWithMembers(members)
                val bob = invitees.first()
                val sent = alice.manager.buildTextMessage(groupId, PAYLOAD, persistOwn = false)
                bob to sent.outbound.signedEvent
            }
        },
    ) { (bob, event) ->
        runBlocking { bob.manager.ingest(event as GroupEvent) }
    }

private const val BENCH_RELAY = "wss://bench.invalid"

private const val PAYLOAD = "marmot benchmark payload — the same 64-ish byte body both sides send"

/**
 * Every benchmark, as name -> thunk, so a run can be narrowed to one row.
 *
 * Narrowing matters for profiling: a CPU profile of the whole suite mixes
 * `create_group` samples with everything else, and the interesting question
 * is usually about one operation at a time.
 */
private val ALL: List<Pair<String, () -> BenchResult>> =
    buildList {
        // The same invitee counts MDK's `bench_create_group` uses, plus 0 as
        // the founding-only baseline, so the rows line up for comparison.
        listOf(0, 1, 8, 32).forEach { n -> add("create_group/$n" to { benchCreateGroup(n) }) }
        add("join_welcome" to { benchJoinWelcome() })
        // Group sizes on the message path, because its persistence cost scales
        // with the member count even though its cryptography does not.
        listOf(0, 1, 8, 32).forEach { n -> add("send_app_message/$n" to { benchSendAppMessage(n) }) }
        listOf(1, 8, 32).forEach { n -> add("ingest_app_message/$n" to { benchIngestAppMessage(n) }) }
        listOf(1, 8, 32).forEach { n -> add("ingest_commit/$n" to { benchIngestCommit(n) }) }
        addAll(primitiveBenchmarks())
    }

/** Runs every benchmark whose name contains [only], or all of them when null. */
fun allBenchmarks(only: String? = null): List<BenchResult> =
    ALL
        .filter { (name, _) -> only == null || name.contains(only) }
        .map { (_, run) -> run() }
