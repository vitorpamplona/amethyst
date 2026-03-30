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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NostrServerAuthTest {
    private val pubkey = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    private val pubkey2 = "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245"
    private val sig = "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce"
    private val relayUrl = NormalizedRelayUrl("wss://relay.example.com/")

    private fun hexId(n: Int): String = n.toString().padStart(64, '0')

    private fun testEvent(
        id: String = hexId(1),
        kind: Int = 1,
        createdAt: Long = 1000L,
        content: String = "hello",
        tags: Array<Array<String>> = emptyArray(),
    ) = Event(id, pubkey, createdAt, kind, tags, content, sig)

    /**
     * Creates a server using the given dispatcher so coroutines run eagerly
     * in tests (UnconfinedTestDispatcher).
     */
    private fun createServer(
        dispatcher: CoroutineDispatcher,
        store: IEventStore = EventStore(null),
        policyBuilder: () -> IRelayPolicy = { FullAuthPolicy(relayUrl) },
    ): NostrServer =
        NostrServer(
            store = store,
            policyBuilder = policyBuilder,
            parentContext = dispatcher,
        )

    private suspend fun RelaySession.insert(event: Event) {
        val cmd = EventCmd(event)
        this.receive(OptimizedJsonMapper.toJson(cmd))
    }

    /** Collects sent JSON messages for a connection. */
    private class MessageCollector {
        val messages = mutableListOf<String>()

        val sendCallback: (String) -> Unit = { messages.add(it) }

        /**
         * Parses messages that can be round-tripped (EVENT, EOSE, NOTICE,
         * CLOSED). OkMessage and CountMessage serialization uses formats
         * incompatible with the client-side deserializer, so check those
         * via [rawMessagesContaining].
         */
        fun parsedEventMessages() =
            messages
                .filter { it.startsWith("[\"EVENT\"") || it.startsWith("[\"EOSE\"") }
                .map { OptimizedJsonMapper.fromJsonToMessage(it) }

        fun rawMessagesContaining(label: String) = messages.filter { it.contains("\"$label\"") }
    }

    /**
     * Builds a kind 22242 auth event for testing. Because verify = { true },
     * the id and signature don't need to be real.
     */
    private fun authEvent(
        challenge: String,
        relay: String = relayUrl.url,
        pubKey: String = pubkey,
        createdAt: Long = TimeUtils.now(),
    ) = RelayAuthEvent(
        id = hexId(99),
        pubKey = pubKey,
        createdAt = createdAt,
        tags =
            arrayOf(
                arrayOf("relay", relay),
                arrayOf("challenge", challenge),
            ),
        content = "",
        sig = sig,
    )

    private fun authJson(event: RelayAuthEvent): String {
        val cmd = AuthCmd(event)
        return OptimizedJsonMapper.toJson(cmd)
    }

    private fun authJson(event: Event) = """["AUTH",${event.toJson()}]"""

    @Test
    fun authChallengeIsSentOnRequest() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("\"AUTH\""))

            server.close()
        }

    @Test
    fun authSucceedsWithValidEvent() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("\"AUTH\""))
            val msg = OptimizedJsonMapper.fromJsonToMessage(collector.messages[0]) as AuthMessage

            val event = authEvent(challenge = msg.challenge)
            session.receive(authJson(event))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))
            assertTrue((session.policy as FullAuthPolicy).isAuthenticated())
            assertTrue(session.policy.authenticatedUsers.contains(pubkey))

            server.close()
        }

    @Test
    fun authFailsWithWrongChallenge() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            val event = authEvent(challenge = "wrong-challenge")
            session.receive(authJson(event))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"false\""))
            assertTrue(okMessages[0].contains("challenge"))
            assertFalse((session.policy as FullAuthPolicy).isAuthenticated())

            server.close()
        }

    @Test
    fun authFailsWithWrongRelay() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("\"AUTH\""))
            val msg = OptimizedJsonMapper.fromJsonToMessage(collector.messages[0]) as AuthMessage

            val event = authEvent(challenge = msg.challenge, relay = "wss://wrong.relay.com/")
            session.receive(authJson(event))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"false\""))
            assertTrue(okMessages[0].contains("relay url"))
            assertFalse((session.policy as FullAuthPolicy).isAuthenticated())

            server.close()
        }

    @Test
    fun authFailsWithExpiredTimestamp() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("\"AUTH\""))
            val msg = OptimizedJsonMapper.fromJsonToMessage(collector.messages[0]) as AuthMessage

            val event =
                authEvent(
                    challenge = msg.challenge,
                    createdAt = TimeUtils.now() - 1200L, // 20 minutes ago
                )
            session.receive(authJson(event))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"false\""))
            assertTrue(okMessages[0].contains("created_at"))
            assertFalse((session.policy as FullAuthPolicy).isAuthenticated())

            server.close()
        }

    @Test
    fun authFailsWithWrongKind() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("\"AUTH\""))
            val msg = OptimizedJsonMapper.fromJsonToMessage(collector.messages[0]) as AuthMessage

            // Create an event with wrong kind (1 instead of 22242)
            val event =
                Event(
                    id = hexId(99),
                    pubKey = pubkey,
                    createdAt = TimeUtils.now(),
                    kind = 1,
                    tags =
                        arrayOf(
                            arrayOf("relay", relayUrl.url),
                            arrayOf("challenge", msg.challenge),
                        ),
                    content = "",
                    sig = sig,
                )
            session.receive(authJson(event))

            val okMessages = collector.rawMessagesContaining("NOTICE")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("error"))
            assertTrue(okMessages[0].contains("could not parse message"))
            assertFalse((session.policy as FullAuthPolicy).isAuthenticated())

            server.close()
        }

    @Test
    fun multipleUsersCanAuthenticate() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("\"AUTH\""))
            val msg = OptimizedJsonMapper.fromJsonToMessage(collector.messages[0]) as AuthMessage

            // First user authenticates
            val event1 = authEvent(challenge = msg.challenge, pubKey = pubkey)
            session.receive(authJson(event1))

            // Second user authenticates on the same session
            val event2 = authEvent(challenge = msg.challenge, pubKey = pubkey2)
            session.receive(authJson(event2))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(2, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))
            assertTrue(okMessages[1].contains("\"true\""))

            val authedPubkeys = (session.policy as FullAuthPolicy).authenticatedUsers
            assertEquals(2, authedPubkeys.size)
            assertTrue(authedPubkeys.contains(pubkey))
            assertTrue(authedPubkeys.contains(pubkey2))

            server.close()
        }

    // -- NIP-42: requireAuth ---------------------------------------------------

    @Test
    fun requireAuthRejectsEventWithoutAuth() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            val event = testEvent()
            session.receive("""["EVENT",${event.toJson()}]""")

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"false\""))
            assertTrue(okMessages[0].contains("auth-required:"))

            server.close()
        }

    @Test
    fun requireAuthRejectsReqWithoutAuth() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)
            session.receive("""["REQ","sub1",{"kinds":[1]}]""")

            val closedMessages = collector.rawMessagesContaining("CLOSED")
            assertEquals(1, closedMessages.size)
            assertTrue(closedMessages[0].contains("auth-required:"))

            server.close()
        }

    @Test
    fun requireAuthRejectsCountWithoutAuth() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)
            session.receive("""["COUNT","q1",{"kinds":[1]}]""")

            val closedMessages = collector.rawMessagesContaining("CLOSED")
            assertEquals(1, closedMessages.size)
            assertTrue(closedMessages[0].contains("auth-required:"))

            server.close()
        }

    @Test
    fun requireAuthAllowsCommandsAfterAuth() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)

            val server = createServer(dispatcher = dispatcher)
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("\"AUTH\""))
            val msg = OptimizedJsonMapper.fromJsonToMessage(collector.messages[0]) as AuthMessage

            // Authenticate first
            val authEv = authEvent(challenge = msg.challenge)
            session.receive(authJson(authEv))

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))

            // Now EVENT should work
            val event = testEvent()
            session.receive("""["EVENT",${event.toJson()}]""")

            val allOk = collector.rawMessagesContaining("OK")
            assertEquals(2, allOk.size)
            assertTrue(allOk[1].contains("\"true\""))

            // REQ should work
            session.receive("""["REQ","sub1",{"kinds":[1]}]""")

            val eoseMessages = collector.rawMessagesContaining("EOSE")
            assertTrue(eoseMessages.isNotEmpty())

            server.close()
        }

    @Test
    fun noAuthRequiredAllowsCommandsWithoutAuth() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher, policyBuilder = { EmptyPolicy })
            val collector = MessageCollector()

            val session = server.connect(collector.sendCallback)

            // EVENT should work without auth when using OpenPolicy
            val event = testEvent()
            session.receive("""["EVENT",${event.toJson()}]""")

            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(1, okMessages.size)
            assertTrue(okMessages[0].contains("\"true\""))

            server.close()
        }

    // -- Custom AuthPolicy tests -----------------------------------------------

    @Test
    fun customPolicyRejectsSpecificEventKinds() =
        runTest {
            // Policy that blocks kind 4 (DMs) from unauthenticated users.
            val policy =
                object : FullAuthPolicy(relayUrl) {
                    override fun accept(cmd: EventCmd) =
                        if (cmd.event.kind == 4 && authenticatedUsers.isEmpty()) {
                            PolicyResult.Rejected("auth-required: kind 4 events require authentication")
                        } else {
                            PolicyResult.Accepted(cmd)
                        }

                    override fun accept(cmd: ReqCmd) = PolicyResult.Accepted(cmd)

                    override fun accept(cmd: CountCmd) = PolicyResult.Accepted(cmd)
                }

            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher = dispatcher, policyBuilder = { policy })
            val collector = MessageCollector()
            val session = server.connect(collector.sendCallback)

            // Kind 1 should be accepted without auth
            val note = testEvent(hexId(1), kind = 1)
            session.receive("""["EVENT",${note.toJson()}]""")
            assertTrue(collector.rawMessagesContaining("OK")[0].contains("\"true\""))

            // Kind 4 should be rejected without auth
            val dm = testEvent(hexId(2), kind = 4)
            session.receive("""["EVENT",${dm.toJson()}]""")
            val okMessages = collector.rawMessagesContaining("OK")
            assertEquals(2, okMessages.size)
            assertTrue(okMessages[1].contains("\"false\""))
            assertTrue(okMessages[1].contains("auth-required:"))

            server.close()
        }

    @Test
    fun customPolicyRewritesFilters() =
        runTest {
            // Policy that restricts kind 4 queries to the authed user's own messages.
            val policy =
                object : FullAuthPolicy(relayUrl) {
                    override fun accept(cmd: EventCmd) = PolicyResult.Accepted(cmd)

                    override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> {
                        val hasDmFilter = cmd.filters.any { it.kinds?.contains(4) == true }
                        if (!hasDmFilter) return PolicyResult.Accepted(cmd)
                        if (authenticatedUsers.isEmpty()) {
                            return PolicyResult.Rejected("auth-required: kind 4 requires auth")
                        }
                        // Rewrite: restrict to authed user's pubkey as author
                        val rewritten =
                            cmd.filters.map { filter ->
                                if (filter.kinds?.contains(4) == true) {
                                    filter.copy(authors = authenticatedUsers.toList())
                                } else {
                                    filter
                                }
                            }
                        return PolicyResult.Accepted(ReqCmd(cmd.subId, rewritten))
                    }

                    override fun accept(cmd: CountCmd) = PolicyResult.Accepted(cmd)
                }

            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val store = EventStore(null)
            val server = createServer(store = store, dispatcher = dispatcher, policyBuilder = { policy })

            // Insert DMs from two different authors
            store.insert(testEvent(hexId(1), kind = 4, createdAt = 100L)) // from pubkey
            store.insert(
                Event(hexId(2), pubkey2, 200L, 4, emptyArray(), "secret", sig),
            ) // from pubkey2

            val collector = MessageCollector()
            val session = server.connect(collector.sendCallback)

            assertEquals(1, collector.messages.size)
            assertTrue(collector.messages[0].contains("\"AUTH\""))
            val msg = OptimizedJsonMapper.fromJsonToMessage(collector.messages[0]) as AuthMessage

            // Authenticate as pubkey
            val auth = authEvent(challenge = msg.challenge, pubKey = pubkey)
            session.receive(authJson(auth))

            // Query kind 4 — policy should rewrite to only return pubkey's events
            session.receive("""["REQ","sub1",{"kinds":[4]}]""")

            val events = collector.parsedEventMessages().filterIsInstance<EventMessage>()
            assertEquals(1, events.size)
            assertEquals(pubkey, events[0].event.pubKey)

            server.close()
        }
}
