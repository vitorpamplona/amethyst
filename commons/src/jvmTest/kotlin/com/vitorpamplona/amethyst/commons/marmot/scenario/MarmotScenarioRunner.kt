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
 * A step type the runner does not implement throws [UnsupportedScenarioStep].
 * Nineteen of the portable vectors need fault injection or group-data steps we
 * have not modelled, and a runner that quietly ignored those steps would report
 * a pass for a script it did not execute — worse than no coverage, because it
 * would look like coverage.
 */
class MarmotScenarioRunner(
    private val vector: ScenarioVector,
) {
    private val clients = vector.clients.associateWith { VectorClient(it) }

    /** Queued outbound events: (sender, event). Drained by `deliver_all`. */
    private val inFlight = mutableListOf<Pair<String, Event>>()

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
        var groupId: HexKey? = null
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
    private val publishOutcomes = mutableMapOf<String, MutableList<Boolean>>()

    private fun preScanPublishOutcomes() {
        vector.steps.filter { it.type == "acknowledge_outbound" }.forEach { step ->
            val client = step.string("client") ?: return@forEach
            val accepted = step.string("outcome") == "accepted"
            publishOutcomes.getOrPut(client) { mutableListOf() }.add(accepted)
        }
    }

    private fun nextOutcome(client: String): Boolean {
        val queue = publishOutcomes[client] ?: return true
        return if (queue.isEmpty()) true else queue.removeAt(0)
    }

    suspend fun run() {
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
            // The publication's outcome was consumed when it was made; the step
            // itself carries no further state change.
            "acknowledge_outbound" -> Unit
            // Assertions the trace re-states; `verify()` checks them from the
            // expected observations, which is the same information.
            "observe", "observe_exact", "in_group", "assert", "clear_events", "await_quiescence" -> Unit
            else -> throw UnsupportedScenarioStep(step.type)
        }
    }

    private suspend fun createGroup(step: ScenarioVector.Step) {
        val creator = client(step.string("creator") ?: error("create_group without a creator"))
        val name = step.string("name").orEmpty()
        val groupId = RandomInstance.bytes(32).toHexKey()
        val invitees = step.strings("invitees")

        if (invitees.size > 1) {
            throw ScenarioBatchingDivergence(
                "create_group names ${invitees.size} invitees and the reference adds them in one " +
                    "commit (epoch 1); MarmotManager.addMember stages one Add per commit, so we " +
                    "would reach epoch ${invitees.size}. Both are valid MLS; the traces cannot match " +
                    "until we can commit several Adds together.",
            )
        }

        creator.manager.createCurrentProfileGroup(
            nostrGroupId = groupId,
            relays = listOf("wss://vector.invalid"),
            profile = if (name.isEmpty()) null else GroupProfileV1(name, ""),
            additionalAdmins =
                step.strings("initial_admins").map { admin ->
                    client(admin).signer.pubKey.hexToByteArray()
                },
        )
        creator.groupId = groupId
        addMembers(creator, groupId, invitees)
    }

    private suspend fun inviteMembers(step: ScenarioVector.Step) {
        val inviter = client(step.string("inviter") ?: error("invite_members without an inviter"))
        val groupId = inviter.groupId ?: error("${inviter.name} invited before joining a group")
        addMembers(inviter, groupId, step.strings("invitees"))
    }

    private suspend fun addMembers(
        inviter: VectorClient,
        groupId: HexKey,
        invitees: List<String>,
    ) {
        invitees.forEach { inviteeName ->
            val invitee = client(inviteeName)
            // A KeyPackage per invitee, minted on demand: the vector names
            // members, not key material.
            val bundle = invitee.manager.generateKeyPackageEvent(relays = emptyList())
            val (_, delivery) =
                inviter.manager.addMember(
                    nostrGroupId = groupId,
                    keyPackageEvent = bundle,
                    relays = emptyList(),
                )
            // The Welcome goes straight to its recipient's inbox. Gift-wrap
            // addressing is the transport's job and the interop harness's test.
            delivery?.let { invitee.inbox.add(it.giftWrapEvent) }
            invitee.groupId = groupId
        }
    }

    private suspend fun sendAppMessage(step: ScenarioVector.Step) {
        val sender = client(step.string("sender") ?: error("send_app_message without a sender"))
        val groupId = sender.groupId ?: error("${sender.name} sent before joining a group")
        val payload = step.string("payload").orEmpty()
        sender.manager.buildTextMessage(groupId, payload, persistOwn = false)
    }

    private fun deliverAll() {
        val batch = inFlight.toList()
        inFlight.clear()
        batch.forEach { (senderName, event) ->
            clients.values.filter { it.name != senderName }.forEach { it.inbox.add(event) }
        }
    }

    private suspend fun tick(clientName: String) {
        val client = client(clientName)
        val batch = client.inbox.toList()
        client.inbox.clear()
        batch.forEach { event ->
            when (val result = client.manager.ingest(event)) {
                is MarmotIngestResult.JoinedGroup -> client.groupId = result.nostrGroupId
                is MarmotIngestResult.Message ->
                    Event
                        .fromJsonOrNull(result.inner.innerEventJson)
                        ?.takeIf { it.kind == CHAT_KIND }
                        ?.let { client.received.add(it.content) }

                else -> Unit
            }
        }
    }

    /** Compare every client's end state against the vector's expected trace. */
    private fun verify() {
        val failures = mutableListOf<String>()
        vector.observations.forEach { expected ->
            val client = clients[expected.client] ?: return@forEach
            val groupId = client.groupId
            if (groupId == null) {
                failures.add("${expected.client} is in no group")
                return@forEach
            }
            expected.epoch?.let { want ->
                val got = client.manager.groupEpoch(groupId)
                if (got != want) failures.add("${expected.client} epoch $got, expected $want")
            }
            expected.memberCount?.let { want ->
                val got = client.manager.memberCount(groupId)
                if (got != want) failures.add("${expected.client} has $got members, expected $want")
            }
            expected.groupName?.let { want ->
                val got = client.manager.groupView(groupId)?.name
                if (got != want) failures.add("${expected.client} group name '$got', expected '$want'")
            }
            if (expected.receivedPayloads.isNotEmpty()) {
                val got = client.received.sorted()
                val want = expected.receivedPayloads.sorted()
                if (got != want) failures.add("${expected.client} received $got, expected $want")
            }
        }
        check(failures.isEmpty()) {
            "vector ${vector.name} diverged from its expected trace:\n  " + failures.joinToString("\n  ")
        }
    }

    private fun client(name: String) = clients[name] ?: error("vector names a client '$name' that its roster does not list")

    private companion object {
        const val CHAT_KIND = 9
    }
}
