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
package com.vitorpamplona.amethyst.commons.marmot.scenario

import com.vitorpamplona.amethyst.commons.marmot.MarmotIngestResult
import com.vitorpamplona.amethyst.commons.marmot.MarmotManager
import com.vitorpamplona.amethyst.commons.marmot.MarmotPublisher
import com.vitorpamplona.amethyst.commons.marmot.SnapshotBundleStore
import com.vitorpamplona.amethyst.commons.marmot.SnapshotMessageStore
import com.vitorpamplona.amethyst.commons.marmot.SnapshotStateStore
import com.vitorpamplona.amethyst.commons.marmot.ingest
import com.vitorpamplona.quartz.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * Replays a CGKA conformance scenario vector against our own MLS stack.
 *
 * The vectors are the reference implementation's, marked `portable` in its
 * manifest, and that word is the whole point: a script written for one engine
 * and replayed by another. Our interop harness proves we can talk to `wn` over
 * a relay; this proves we reach the same GROUP STATE from the same sequence of
 * events, which is a different claim and the one MLS conformance is actually
 * about.
 *
 * ## What is simulated, and what is not
 *
 * There is no relay and no gift-wrap round trip here — the harness covers that.
 * Publishing is captured into a queue, `deliver_all` moves the queue into every
 * other client's inbox, and `tick` drains an inbox. That mirrors the vector's
 * own model, where delivery and processing are separate steps precisely so a
 * script can hold a message back.
 *
 * ## Refusing rather than skipping
 *
 * A step type the runner does not implement throws [UnsupportedScenarioStep],
 * and an expected outcome it cannot check throws [UnsupportedScenarioOutcome].
 * Nineteen of the portable vectors need fault injection or group-data steps we
 * have not modelled, and a runner that quietly ignored those steps would report
 * a pass for a script it did not execute — worse than no coverage, because it
 * would look like coverage. The same is true one level up: a vector whose
 * conclusion is a `convergence_decision` we do not model is refused, not passed
 * on the parts that happen to be checkable.
 */
class MarmotScenarioRunner(
    private val vector: ScenarioVector,
) {
    private val clients = vector.clients.associateWith { VectorClient(it) }

    /** Queued outbound events: (sender, event). Drained by `deliver_all`. */
    private val inFlight = mutableListOf<Pair<String, Event>>()

    /**
     * The group label the current step runs against.
     *
     * `in_group` wraps a step and names a label; everything else runs against
     * [DEFAULT_GROUP]. A client can be in several groups at once and the
     * isolation vectors are entirely about what does NOT cross between them,
     * so a single group per client would test the opposite of the point.
     */
    private var currentGroup = DEFAULT_GROUP

    /** Which label each created group id belongs to, so joiners can be filed. */
    private val labelByGroupId = mutableMapOf<HexKey, String>()

    private class VectorClient(
        val name: String,
    ) {
        val signer = NostrSignerInternal(KeyPair())
        val mlsStore = SnapshotStateStore()
        val messageStore = SnapshotMessageStore()
        lateinit var manager: MarmotManager

        /** Delivered but not yet processed — `tick` is what processes. */
        val inbox = mutableListOf<Event>()
        val received = mutableListOf<String>()

        /** Group label -> nostr group id, for every group this client is in. */
        val groups = mutableMapOf<String, HexKey>()

        /** Members this client watched join, by pubkey, since the last `clear_events`. */
        val sawJoin = mutableListOf<HexKey>()
    }

    /**
     * Whether the next publication by this client should be accepted.
     *
     * The vector acknowledges a publication in a step AFTER the one that
     * created it, but our `commitAndPublish` decides synchronously — publish
     * before apply means the relay's answer is what makes the commit canonical.
     * So the outcome is read ahead out of the matching `acknowledge_outbound`
     * step. It is the same information in the same order, just consulted when
     * this implementation needs it.
     */
    private val publishOutcomes = mutableMapOf<String, MutableList<Pair<String, Boolean>>>()

    /** Publication label -> whether OUR gate confirmed it, for `pending_resolution`. */
    private val resolved = mutableMapOf<String, Boolean>()

    private fun preScanPublishOutcomes() {
        vector.steps.filter { it.type == "acknowledge_outbound" }.forEach { step ->
            val client = step.string("client") ?: return@forEach
            val accepted = step.string("outcome") == "accepted"
            publishOutcomes
                .getOrPut(client) { mutableListOf() }
                .add(step.string("publication").orEmpty() to accepted)
        }
    }

    private fun nextOutcome(client: String): Boolean {
        val queue = publishOutcomes[client] ?: return true
        if (queue.isEmpty()) return true
        val (label, accepted) = queue.removeAt(0)
        if (label.isNotEmpty()) resolved[label] = accepted
        return accepted
    }

    suspend fun run() {
        vector.unmodelledOutcomes.firstOrNull()?.let { throw UnsupportedScenarioOutcome(it.type) }
        preScanPublishOutcomes()
        clients.values.forEach { client ->
            client.manager =
                MarmotManager(
                    client.signer,
                    client.mlsStore,
                    client.messageStore,
                    SnapshotBundleStore(),
                    publisher =
                        MarmotPublisher { event, _ ->
                            val accepted = nextOutcome(client.name)
                            if (accepted) inFlight.add(client.name to event)
                            accepted
                        },
                )
        }

        vector.steps.forEach { step -> execute(step) }
        verify()
    }

    private suspend fun execute(step: ScenarioVector.Step) {
        when (step.type) {
            "create_group" -> createGroup(step)
            "invite_members" -> inviteMembers(step)
            "send_app_message" -> sendAppMessage(step)
            "deliver_all" -> deliverAll()
            "tick" -> step.strings("clients").ifEmpty { vector.clients }.forEach { tick(it) }
            "in_group" -> inGroup(step)
            "clear_events" -> clearEvents(step)
            "assert" -> assertPredicate(step)
            // The publication's outcome was consumed when it was made; the step
            // itself carries no further state change.
            "acknowledge_outbound" -> Unit
            // Assertions the trace re-states; `verify()` checks them from the
            // expected observations, which is the same information.
            "observe", "observe_exact", "await_quiescence" -> Unit
            else -> throw UnsupportedScenarioStep(step.type)
        }
    }

    /** Run the wrapped step against the named group label. */
    private suspend fun inGroup(step: ScenarioVector.Step) {
        val action = step.step("action") ?: error("in_group without an action")
        val previous = currentGroup
        currentGroup = step.string("group") ?: DEFAULT_GROUP
        try {
            execute(action)
        } finally {
            currentGroup = previous
        }
    }

    /**
     * Reset the observation counters, as the reference's `clear_events` does.
     *
     * It is what makes a later `received_payloads` mean "since this point"
     * rather than "ever", so dropping it would make every post-clear
     * expectation fail against a list that still holds the setup traffic.
     */
    private fun clearEvents(step: ScenarioVector.Step) {
        step.strings("clients").ifEmpty { vector.clients }.forEach {
            client(it).received.clear()
            client(it).sawJoin.clear()
        }
    }

    /**
     * Check an inline `assert` predicate.
     *
     * The only predicate our vectors use is `payload_count`, and every one of
     * them asserts a count of ZERO: it is how forward secrecy and multigroup
     * isolation are stated — a payload this client must NOT hold. That makes it
     * the highest-value assertion in the set and the last one that should be
     * skipped.
     */
    private fun assertPredicate(step: ScenarioVector.Step) {
        val assertion = step.obj("assertion") ?: error("assert without an assertion")
        val predicate = assertion.step("predicate") ?: error("assertion without a predicate")
        when (predicate.type) {
            "payload_count" -> {
                val who = predicate.string("client") ?: error("payload_count without a client")
                val payload = predicate.string("payload").orEmpty()
                val want = predicate.int("count") ?: 0
                val got = client(who).received.count { it == payload }
                check(got == want) {
                    "vector ${vector.name}: $who holds $got copies of '$payload', expected $want"
                }
            }

            else -> throw UnsupportedScenarioStep("assert/${predicate.type}")
        }
    }

    private suspend fun createGroup(step: ScenarioVector.Step) {
        val creator = client(step.string("creator") ?: error("create_group without a creator"))
        val name = step.string("name").orEmpty()
        val groupId = RandomInstance.bytes(32).toHexKey()
        val invitees = step.strings("invitees")

        creator.manager.createCurrentProfileGroup(
            nostrGroupId = groupId,
            relays = listOf("wss://vector.invalid"),
            profile = if (name.isEmpty()) null else GroupProfileV1(name, ""),
            additionalAdmins =
                step.strings("initial_admins").map { admin ->
                    client(admin).signer.pubKey.hexToByteArray()
                },
        )
        labelByGroupId[groupId] = currentGroup
        creator.groups[currentGroup] = groupId
        addMembers(creator, groupId, invitees)
    }

    private suspend fun inviteMembers(step: ScenarioVector.Step) {
        val inviter = client(step.string("inviter") ?: error("invite_members without an inviter"))
        val groupId = inviter.groups[currentGroup] ?: error("${inviter.name} invited before joining a group")
        addMembers(inviter, groupId, step.strings("invitees"))
    }

    private suspend fun addMembers(
        inviter: VectorClient,
        groupId: HexKey,
        invitees: List<String>,
    ) {
        if (invitees.isEmpty()) return

        // A KeyPackage per invitee, minted on demand: the vector names
        // members, not key material.
        val bundles =
            invitees.map { inviteeName ->
                val invitee = client(inviteeName)
                invitee to invitee.manager.generateKeyPackageEvent(relays = emptyList())
            }

        // ONE commit for the whole batch, as the reference does — N Adds in a
        // single Commit and a single Welcome carrying N EncryptedGroupSecrets.
        // Adding them one at a time would burn an epoch per invitee and the
        // traces would no longer line up.
        val (_, deliveries) =
            inviter.manager.addMembers(
                nostrGroupId = groupId,
                keyPackageEvents = bundles.map { it.second },
                relays = emptyList(),
            )

        val byPubKey = bundles.associate { (invitee, _) -> invitee.signer.pubKey to invitee }
        deliveries.forEach { delivery ->
            // The Welcome goes straight to its recipient's inbox. Gift-wrap
            // addressing is the transport's job and the interop harness's test.
            byPubKey[delivery.recipientPubKey]?.inbox?.add(delivery.giftWrapEvent)
        }
    }

    private suspend fun sendAppMessage(step: ScenarioVector.Step) {
        val sender = client(step.string("sender") ?: error("send_app_message without a sender"))
        val groupId = sender.groups[currentGroup] ?: error("${sender.name} sent before joining a group")
        val payload = step.string("payload").orEmpty()
        // An application message does NOT go through the publish gate — it
        // advances nothing and has nothing to roll back — so the runner queues
        // it itself. Leaving that out meant every `send_app_message` built an
        // event nobody ever delivered.
        val bundle = sender.manager.buildTextMessage(groupId, payload, persistOwn = false)
        inFlight.add(sender.name to bundle.outbound.signedEvent)
    }

    private fun deliverAll() {
        val batch = inFlight.toList()
        inFlight.clear()
        batch.forEach { (senderName, event) ->
            // Broadcast to everyone else, including clients who are not in the
            // sending group. Their engine refusing that traffic is precisely
            // what multigroup isolation asserts.
            clients.values.filter { it.name != senderName }.forEach { it.inbox.add(event) }
        }
    }

    private suspend fun tick(clientName: String) {
        val client = client(clientName)
        val batch = client.inbox.toList()
        client.inbox.clear()

        // Snapshot membership of the groups this client is ALREADY in, so a
        // commit processed below can be attributed as "saw N join". A group
        // joined during this tick has no before-state and contributes nothing:
        // the joiner did not watch anyone join, it arrived to a membership.
        val before = client.groups.values.associateWith { membersOf(client, it) }

        batch.forEach { event ->
            when (val result = client.manager.ingest(event)) {
                is MarmotIngestResult.JoinedGroup ->
                    client.groups[labelByGroupId[result.nostrGroupId] ?: DEFAULT_GROUP] = result.nostrGroupId

                is MarmotIngestResult.Message ->
                    Event
                        .fromJsonOrNull(result.inner.innerEventJson)
                        ?.takeIf { it.kind == CHAT_KIND }
                        ?.let { client.received.add(it.content) }

                else -> Unit
            }
        }

        before.forEach { (groupId, was) ->
            client.sawJoin.addAll(membersOf(client, groupId) - was)
        }
    }

    private fun membersOf(
        client: VectorClient,
        groupId: HexKey,
    ): Set<HexKey> =
        client.manager
            .memberPubkeys(groupId)
            .map { it.pubkey }
            .toSet()

    /** Compare every client's end state against the vector's expected trace. */
    private fun verify() {
        val failures = mutableListOf<String>()

        vector.pendingResolutions.forEach { expected ->
            val confirmed = resolved[expected.publication]
            val want = expected.resolution == "confirmed"
            if (confirmed == null) {
                failures.add("publication '${expected.publication}' never happened")
            } else if (confirmed != want) {
                failures.add(
                    "publication '${expected.publication}' resolved " +
                        "${if (confirmed) "confirmed" else "rolled_back"}, expected ${expected.resolution}",
                )
            }
        }

        vector.quiescentClients.forEach { name ->
            val client = clients[name] ?: return@forEach
            if (client.inbox.isNotEmpty()) failures.add("$name still has ${client.inbox.size} events unprocessed")
        }
        if (vector.quiescentClients.isNotEmpty() && inFlight.isNotEmpty()) {
            failures.add("${inFlight.size} events are still undelivered")
        }

        vector.observations.forEach { expected ->
            val client = clients[expected.client] ?: return@forEach
            if (client.groups.isEmpty()) {
                failures.add("${expected.client} is in no group")
                return@forEach
            }
            // A per-group fact stated once applies to EVERY group the client
            // holds — the isolation vectors put a client in several groups and
            // state one epoch and one member count for all of them.
            client.groups.forEach { (label, groupId) ->
                expected.epoch?.let { want ->
                    val got = client.manager.groupEpoch(groupId)
                    if (got != want) failures.add("${expected.client}[$label] epoch $got, expected $want")
                }
                expected.memberCount?.let { want ->
                    val got = client.manager.memberCount(groupId)
                    if (got != want) failures.add("${expected.client}[$label] has $got members, expected $want")
                }
                expected.groupName?.let { want ->
                    val got = client.manager.groupView(groupId)?.name
                    if (got != want) failures.add("${expected.client}[$label] group name '$got', expected '$want'")
                }
            }
            if (expected.receivedPayloads.isNotEmpty()) {
                val got = client.received.sorted()
                val want = expected.receivedPayloads.sorted()
                if (got != want) failures.add("${expected.client} received $got, expected $want")
            }
            expected.addedMembers?.let { want ->
                val got = client.sawJoin.mapNotNull { pubkey -> clients.values.firstOrNull { it.signer.pubKey == pubkey }?.name }
                if (got.sorted() != want.sorted()) {
                    failures.add("${expected.client} saw $got join, expected $want")
                }
            }
        }
        check(failures.isEmpty()) {
            "vector ${vector.name} diverged from its expected trace:\n  " + failures.joinToString("\n  ")
        }
    }

    private fun client(name: String) = clients[name] ?: error("vector names a client '$name' that its roster does not list")

    private companion object {
        const val CHAT_KIND = 9

        /** The label for a vector that never says `in_group` — most of them. */
        const val DEFAULT_GROUP = "default"
    }
}
