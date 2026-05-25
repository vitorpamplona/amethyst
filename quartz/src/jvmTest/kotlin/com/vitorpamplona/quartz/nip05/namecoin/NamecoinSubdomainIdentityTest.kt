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
package com.vitorpamplona.quartz.nip05.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.IElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NameShowResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinLookupException
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the NIP-05 identity path's handling of multi-label `.bit`
 * inputs.
 *
 * Before this change, `alice@relay.testls.bit` was mapped to a literal
 * `d/relay.testls` ElectrumX query, which is non-spec (the `d/`
 * namespace is single-label per ifa-0001) and never resolves on a real
 * blockchain. After this change, the resolver looks up the registered
 * single-label parent (`d/testls`) and walks the value's `map` tree to
 * find the effective Domain Name Object for the subdomain, then reads
 * `nostr.names.<localPart>` from that node \u2014 the same convention the
 * relay path uses.
 */
class NamecoinSubdomainIdentityTest {
    // \u2500\u2500 parseIdentifierFlat \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    @Test
    fun `flat parser splits alice at relay testls bit into d testls plus relay`() {
        val r = NamecoinNameResolver.parseIdentifierFlat("alice@relay.testls.bit")
        assertEquals("d/testls", r!!.namecoinName)
        assertEquals("alice", r.localPart)
        assertEquals(listOf("relay"), r.subdomainLabels)
        assertEquals(false, r.isIdentityNamespace)
    }

    @Test
    fun `flat parser leaves alice at testls bit untouched`() {
        val r = NamecoinNameResolver.parseIdentifierFlat("alice@testls.bit")
        assertEquals("d/testls", r!!.namecoinName)
        assertEquals("alice", r.localPart)
        assertEquals(emptyList<String>(), r.subdomainLabels)
    }

    @Test
    fun `flat parser preserves DNS order for deeply nested host`() {
        val r = NamecoinNameResolver.parseIdentifierFlat("user@a.b.c.testls.bit")
        assertEquals("d/testls", r!!.namecoinName)
        assertEquals("user", r.localPart)
        assertEquals(listOf("a", "b", "c"), r.subdomainLabels)
    }

    @Test
    fun `flat parser bare relay testls bit is anonymous user under relay subdomain`() {
        val r = NamecoinNameResolver.parseIdentifierFlat("relay.testls.bit")
        assertEquals("d/testls", r!!.namecoinName)
        assertEquals("_", r.localPart)
        assertEquals(listOf("relay"), r.subdomainLabels)
    }

    @Test
    fun `flat parser literal d-slash inputs do not split`() {
        // `d/relay.testls` is malformed per ifa-0001 (the `d/` namespace
        // is single-label) but if a caller hands us that literal, we hand
        // it back unchanged \u2014 it's their problem to register / not register.
        val r = NamecoinNameResolver.parseIdentifierFlat("d/relay.testls")
        assertEquals("d/relay.testls", r!!.namecoinName)
        assertEquals(emptyList<String>(), r.subdomainLabels)
    }

    @Test
    fun `flat parser literal id-slash inputs do not split`() {
        val r = NamecoinNameResolver.parseIdentifierFlat("id/alice")
        assertEquals("id/alice", r!!.namecoinName)
        assertEquals(emptyList<String>(), r.subdomainLabels)
        assertEquals(true, r.isIdentityNamespace)
    }

    @Test
    fun `toNamecoinName returns single-label parent for multi-label bit`() {
        // The previous behaviour returned `d/relay.testls`, which is
        // non-spec and never resolves. Now it returns `d/testls`, the
        // actually-queryable parent. The companion helper's purpose is
        // \u201cwhat name should I query\u201d, so this is the correct fix.
        assertEquals(
            "d/testls",
            NamecoinNameResolver.toNamecoinName("relay.testls.bit"),
        )
        assertEquals(
            "d/testls",
            NamecoinNameResolver.toNamecoinName("alice@relay.testls.bit"),
        )
    }

    // \u2500\u2500 End-to-end resolve(): multi-label NIP-05 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    @Test
    fun `alice at relay testls bit resolves through d testls map relay`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """
                        {
                          "map": {
                            "relay": {
                              "nostr": {
                                "names": {
                                  "alice": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1",
                                  "_":     "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa0"
                                }
                              }
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                    // Trap: if the resolver ever falls back to multi-label
                    // queries, we want the test to catch it.
                    register(
                        "d/relay.testls",
                        """{"nostr":"deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"}""",
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)

            val outcome = resolver.resolveDetailed("alice@relay.testls.bit")
            assertTrue("expected Success, got $outcome", outcome is NamecoinResolveOutcome.Success)
            outcome as NamecoinResolveOutcome.Success
            assertEquals(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1",
                outcome.result.pubkey,
            )
            // d/relay.testls was NEVER queried.
            assertEquals(listOf("d/testls"), client.queriedNames)
        }

    @Test
    fun `bare relay testls bit resolves to root entry under relay subdomain`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """
                        {
                          "map": {
                            "relay": {
                              "nostr": {
                                "names": {
                                  "_": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa0"
                                }
                              }
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)

            val outcome = resolver.resolveDetailed("relay.testls.bit")
            assertTrue(outcome is NamecoinResolveOutcome.Success)
            outcome as NamecoinResolveOutcome.Success
            assertEquals(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa0",
                outcome.result.pubkey,
            )
            assertEquals(listOf("d/testls"), client.queriedNames)
        }

    @Test
    fun `multi-label nip05 falls back to wildcard subdomain`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """
                        {
                          "map": {
                            "*": {
                              "nostr": {
                                "names": {
                                  "alice": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"
                                }
                              }
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)

            // No exact `mqtt` subdomain, falls through to the wildcard.
            val outcome = resolver.resolveDetailed("alice@mqtt.testls.bit")
            assertTrue(outcome is NamecoinResolveOutcome.Success)
            outcome as NamecoinResolveOutcome.Success
            assertEquals(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1",
                outcome.result.pubkey,
            )
        }

    @Test
    fun `multi-label nip05 does NOT inherit nostr from parent`() =
        runTest {
            // Spec safety: the parent's `nostr.names.alice` MUST NOT
            // silently authorise alice@<sub>.<parent>.bit. Each subdomain
            // declares its own `nostr` (or a wildcard does).
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """
                        {
                          "nostr": {
                            "names": {
                              "alice": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"
                            }
                          },
                          "map": {
                            "relay": { "ip": ["1.2.3.4"] }
                          }
                        }
                        """.trimIndent(),
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)

            val outcome = resolver.resolveDetailed("alice@relay.testls.bit")
            assertTrue(
                "expected NoNostrField since the relay subdomain has no nostr field, got $outcome",
                outcome is NamecoinResolveOutcome.NoNostrField,
            )
        }

    @Test
    fun `multi-label nip05 with no matching subdomain returns NoNostrField`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """{"nostr":{"names":{"alice":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"}}}""",
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)

            // No `map` at all; the `relay` subdomain doesn't exist in the
            // record, so we must return NoNostrField rather than silently
            // serving the parent's pubkey.
            val outcome = resolver.resolveDetailed("alice@relay.testls.bit")
            assertTrue(
                "expected NoNostrField, got $outcome",
                outcome is NamecoinResolveOutcome.NoNostrField,
            )
        }

    @Test
    fun `bare-host nip05 still works (back-compat)`() =
        runTest {
            // The single-label NIP-05 path must keep behaving exactly as
            // before. `alice@testls.bit` reads `nostr.names.alice` from
            // the top-level value object.
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """
                        {
                          "nostr": {
                            "names": {
                              "alice": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val outcome = resolver.resolveDetailed("alice@testls.bit")
            assertTrue(outcome is NamecoinResolveOutcome.Success)
            outcome as NamecoinResolveOutcome.Success
            assertEquals(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1",
                outcome.result.pubkey,
            )
        }

    @Test
    fun `multi-label resolution uses ONE ElectrumX call regardless of subdomain depth`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """
                        {"map":{"c":{"map":{"b":{"map":{"a":
                          {"nostr":{"names":{"_":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa0"}}}
                        }}}}}}
                        """.trimIndent(),
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val outcome = resolver.resolveDetailed("a.b.c.testls.bit")
            assertTrue(outcome is NamecoinResolveOutcome.Success)
            assertEquals(1, client.callCount)
            assertEquals(listOf("d/testls"), client.queriedNames)
        }

    @Test
    fun `convenience resolve also handles multi-label`() =
        runTest {
            // `resolve()` is the thin success-only wrapper around
            // resolveDetailed(). Spot-check it for the same multi-label
            // input so callers using the simpler API also benefit.
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """{"map":{"relay":{"nostr":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"}}}""",
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val result = resolver.resolve("relay.testls.bit")
            assertEquals(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1",
                result?.pubkey,
            )
        }

    @Test
    fun `missing parent record returns NameNotFound, never tries multi-label fallback`() =
        runTest {
            val client = FakeElectrumXClient() // empty; everything fails
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val outcome = resolver.resolveDetailed("alice@relay.testls.bit")
            assertTrue(
                "expected ServersUnreachable or NameNotFound, got $outcome",
                outcome is NamecoinResolveOutcome.ServersUnreachable ||
                    outcome is NamecoinResolveOutcome.NameNotFound,
            )
            // Only the parent was consulted.
            assertEquals(listOf("d/testls"), client.queriedNames)
        }

    // \u2500\u2500 Helpers \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    /**
     * Same fake the [BitRelayResolverTest] uses, copied to keep these
     * tests self-contained. Records every queried name so we can assert
     * "only `d/<parent>`, never `d/<sub>.<parent>`".
     */
    private class FakeElectrumXClient : IElectrumXClient {
        val records = mutableMapOf<String, String>()
        val failureFor = mutableMapOf<String, NamecoinLookupException>()
        var callCount = 0
            private set
        val queriedNames: MutableList<String> = mutableListOf()

        fun register(
            name: String,
            value: String,
        ) {
            records[name] = value
        }

        override suspend fun nameShowWithFallback(
            identifier: String,
            servers: List<ElectrumxServer>,
        ): NameShowResult? {
            callCount++
            queriedNames += identifier
            failureFor[identifier]?.let { throw it }
            val value = records[identifier] ?: return null
            return NameShowResult(name = identifier, value = value)
        }
    }
}
