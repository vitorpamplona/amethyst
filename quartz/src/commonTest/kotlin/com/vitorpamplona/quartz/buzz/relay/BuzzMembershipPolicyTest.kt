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
package com.vitorpamplona.quartz.buzz.relay

import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.tags.AuthTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class BuzzMembershipPolicyTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://work.example.com")!!
    private val owner = KeyPair()
    private val agent = KeyPair()
    private val stranger = KeyPair()
    private val ownerPub = owner.pubKey.toHexKey()
    private val agentPub = agent.pubKey.toHexKey()
    private val strangerPub = stranger.pubKey.toHexKey()

    private class Ctx(
        override val authenticatedUsers: Set<HexKey>,
    ) : RequestContext {
        override val connectionId = 1L
        override val policy: IRelayPolicy = EmptyPolicy
    }

    /** A fresh policy whose connection has [authed] recorded as authenticated. */
    private fun policyOn(authed: Set<HexKey>): BuzzMembershipPolicy = BuzzMembershipPolicy(relay, members = setOf(ownerPub)).apply { onConnect(Ctx(authed)) {} }

    private fun event(
        author: HexKey,
        kind: Int = 40002,
    ) = EventCmd(Event("00", author, 0, kind, emptyArray(), "", "sig"))

    private fun req() = ReqCmd("sub", emptyList())

    private fun accepted(r: PolicyResult<*>) = r is PolicyResult.Accepted

    private fun reason(r: PolicyResult<*>) = (r as PolicyResult.Rejected).reason

    @Test
    fun memberMayPublish() {
        assertTrue(accepted(policyOn(setOf(ownerPub)).accept(event(ownerPub))))
    }

    @Test
    fun authenticatedNonMemberIsRejected() {
        val r = policyOn(setOf(strangerPub)).accept(event(strangerPub))
        assertTrue(reason(r).startsWith("restricted"))
    }

    @Test
    fun unauthenticatedIsRejected() {
        val r = policyOn(emptySet()).accept(event(ownerPub))
        assertTrue(reason(r).startsWith("auth-required"))
    }

    @Test
    fun readsAreMemberGated() {
        assertTrue(accepted(policyOn(setOf(ownerPub)).accept(req())))
        // Authenticated non-member: restricted. Unauthenticated: auth-required (so the client
        // runs NIP-42 and retries, rather than silently getting nothing).
        assertTrue(reason(policyOn(setOf(strangerPub)).accept(req())).startsWith("restricted"))
        assertTrue(reason(policyOn(emptySet()).accept(req())).startsWith("auth-required"))
    }

    @Test
    fun allowedKindsRestrictsMembers() {
        val policy = BuzzMembershipPolicy(relay, setOf(ownerPub), allowedKinds = setOf(40002)).apply { onConnect(Ctx(setOf(ownerPub))) {} }
        assertTrue(accepted(policy.accept(event(ownerPub, kind = 40002))))
        assertTrue(reason(policy.accept(event(ownerPub, kind = 1))).startsWith("restricted"))
    }

    @Test
    fun nipOaAgentIsGrantedMembershipForTheConnection() =
        runTest {
            // The owner (a member) attests the un-enrolled agent key.
            val attestation = OwnerAttestation.sign(agentPub, "", owner.privKey!!)
            val authEvent = RelayAuthEvent("00", agentPub, 0, arrayOf(AuthTag.assemble(attestation)), "", "sig")

            val policy = policyOn(setOf(agentPub))
            policy.onAuthenticated(authEvent) // engine calls this after the NIP-42 proof verifies

            assertTrue(accepted(policy.accept(event(agentPub))), "attested agent may publish")
            assertTrue(accepted(policy.accept(req())), "attested agent may read")
        }

    @Test
    fun attestationFromANonMemberOwnerIsIgnored() =
        runTest {
            // The attestation is validly signed, but by an owner who is NOT a workspace member.
            val outsider = KeyPair()
            val attestation = OwnerAttestation.sign(agentPub, "", outsider.privKey!!)
            val authEvent = RelayAuthEvent("00", agentPub, 0, arrayOf(AuthTag.assemble(attestation)), "", "sig")

            val policy = policyOn(setOf(agentPub))
            policy.onAuthenticated(authEvent)

            assertTrue(reason(policy.accept(event(agentPub))).startsWith("restricted"), "unauthorized agent is rejected")
        }

    @Test
    fun tamperedAttestationIsIgnored() =
        runTest {
            // Owner IS a member, but the signature doesn't match the agent (tampered).
            val real = OwnerAttestation.sign(strangerPub, "", owner.privKey!!) // signed for a DIFFERENT key
            val forged = OwnerAttestation(ownerPub, real.conditions, real.sig)
            val authEvent = RelayAuthEvent("00", agentPub, 0, arrayOf(AuthTag.assemble(forged)), "", "sig")

            val policy = policyOn(setOf(agentPub))
            policy.onAuthenticated(authEvent)

            assertTrue(reason(policy.accept(event(agentPub))).startsWith("restricted"))
        }
}
