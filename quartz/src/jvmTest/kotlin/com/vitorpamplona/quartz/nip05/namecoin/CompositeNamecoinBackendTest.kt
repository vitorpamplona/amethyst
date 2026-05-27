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

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.CompositeNamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NameShowResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinFallbackPolicy
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinLookupException
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeNamecoinBackendTest {
    private fun ok(
        name: String,
        value: String,
    ) = NameShowResult(name = name, value = value)

    @Test
    fun `primary success short-circuits chain`() =
        runBlocking {
            val calls = mutableListOf<String>()
            val primary =
                NamecoinNameBackend {
                    calls += "primary"
                    ok(it, """{"nostr":"deadbeef"}""")
                }
            val custom =
                NamecoinNameBackend {
                    calls += "custom"
                    null
                }
            val composite =
                CompositeNamecoinBackend(
                    primary = primary,
                    customElectrumx = custom,
                    defaultElectrumx = null,
                    policy = NamecoinFallbackPolicy(fallbackToCustomElectrumx = true),
                    isPrimaryCoreRpc = true,
                )

            val r = composite.nameShowWithFallback("d/example", emptyList())
            assertEquals("d/example", r?.name)
            assertEquals(listOf("primary"), calls)
        }

    @Test
    fun `name-not-found from primary is authoritative and does not cascade`() =
        runBlocking {
            val calls = mutableListOf<String>()
            val primary =
                NamecoinNameBackend {
                    calls += "primary"
                    throw NamecoinLookupException.NameNotFound("d/example")
                }
            val custom =
                NamecoinNameBackend {
                    calls += "custom"
                    ok(it, """{"nostr":"deadbeef"}""")
                }
            val composite =
                CompositeNamecoinBackend(
                    primary = primary,
                    customElectrumx = custom,
                    defaultElectrumx = null,
                    policy =
                        NamecoinFallbackPolicy(
                            fallbackToCustomElectrumx = true,
                            fallbackToDefaultElectrumx = true,
                        ),
                    isPrimaryCoreRpc = true,
                )

            assertThrows(NamecoinLookupException.NameNotFound::class.java) {
                runBlocking { composite.nameShowWithFallback("d/example", emptyList()) }
            }
            assertEquals(listOf("primary"), calls)
        }

    @Test
    fun `unreachable primary cascades to custom electrumx when policy permits`() =
        runBlocking {
            val calls = mutableListOf<String>()
            val primary =
                NamecoinNameBackend {
                    calls += "primary"
                    throw NamecoinLookupException.ServersUnreachable()
                }
            val custom =
                NamecoinNameBackend {
                    calls += "custom"
                    ok(it, """{"nostr":"fa11"}""")
                }
            val composite =
                CompositeNamecoinBackend(
                    primary = primary,
                    customElectrumx = custom,
                    defaultElectrumx = null,
                    policy = NamecoinFallbackPolicy(fallbackToCustomElectrumx = true),
                    isPrimaryCoreRpc = true,
                )

            val r = composite.nameShowWithFallback("d/example", emptyList())
            assertTrue(r != null)
            assertEquals(listOf("primary", "custom"), calls)
        }

    @Test
    fun `unreachable primary does NOT cascade when policy forbids`() =
        runBlocking {
            val calls = mutableListOf<String>()
            val primary =
                NamecoinNameBackend {
                    calls += "primary"
                    throw NamecoinLookupException.ServersUnreachable()
                }
            val custom =
                NamecoinNameBackend {
                    calls += "custom"
                    ok(it, """{"nostr":"fa11"}""")
                }
            val composite =
                CompositeNamecoinBackend(
                    primary = primary,
                    customElectrumx = custom,
                    defaultElectrumx = null,
                    policy = NamecoinFallbackPolicy.NONE,
                    isPrimaryCoreRpc = true,
                )

            assertThrows(NamecoinLookupException.ServersUnreachable::class.java) {
                runBlocking { composite.nameShowWithFallback("d/example", emptyList()) }
            }
            assertEquals(listOf("primary"), calls)
        }

    @Test
    fun `cascades all the way to defaults when both fallbacks enabled`() =
        runBlocking {
            val calls = mutableListOf<String>()
            val primary =
                NamecoinNameBackend {
                    calls += "primary"
                    throw NamecoinLookupException.ServersUnreachable()
                }
            val custom =
                NamecoinNameBackend {
                    calls += "custom"
                    throw NamecoinLookupException.ServersUnreachable()
                }
            val defaults =
                NamecoinNameBackend {
                    calls += "defaults"
                    ok(it, """{"nostr":"ab"}""")
                }
            val composite =
                CompositeNamecoinBackend(
                    primary = primary,
                    customElectrumx = custom,
                    defaultElectrumx = defaults,
                    policy =
                        NamecoinFallbackPolicy(
                            fallbackToCustomElectrumx = true,
                            fallbackToDefaultElectrumx = true,
                        ),
                    isPrimaryCoreRpc = true,
                )

            composite.nameShowWithFallback("d/example", emptyList())
            assertEquals(listOf("primary", "custom", "defaults"), calls)
        }

    @Test
    fun `when primary is electrumx the customElectrumx tier is skipped`() =
        runBlocking {
            // ElectrumX-primary mode: customElectrumx slot is unused because the primary
            // already IS the custom-electrumx backend. defaultElectrumx still applies.
            val calls = mutableListOf<String>()
            val primary =
                NamecoinNameBackend {
                    calls += "primary"
                    throw NamecoinLookupException.ServersUnreachable()
                }
            val custom =
                NamecoinNameBackend {
                    calls += "custom-should-not-run"
                    null
                }
            val defaults =
                NamecoinNameBackend {
                    calls += "defaults"
                    ok(it, """{"nostr":"cd"}""")
                }
            val composite =
                CompositeNamecoinBackend(
                    primary = primary,
                    customElectrumx = custom,
                    defaultElectrumx = defaults,
                    policy =
                        NamecoinFallbackPolicy(
                            fallbackToCustomElectrumx = true, // should be ignored
                            fallbackToDefaultElectrumx = true,
                        ),
                    isPrimaryCoreRpc = false,
                )

            composite.nameShowWithFallback("d/example", emptyList())
            assertEquals(listOf("primary", "defaults"), calls)
        }

    @Test
    fun `expired primary result is authoritative`() =
        runBlocking {
            val calls = mutableListOf<String>()
            val primary =
                NamecoinNameBackend {
                    calls += "primary"
                    throw NamecoinLookupException.NameExpired("d/example")
                }
            val custom =
                NamecoinNameBackend {
                    calls += "custom"
                    ok(it, """{"nostr":"x"}""")
                }
            val composite =
                CompositeNamecoinBackend(
                    primary = primary,
                    customElectrumx = custom,
                    defaultElectrumx = null,
                    policy =
                        NamecoinFallbackPolicy(
                            fallbackToCustomElectrumx = true,
                            fallbackToDefaultElectrumx = true,
                        ),
                    isPrimaryCoreRpc = true,
                )

            assertThrows(NamecoinLookupException.NameExpired::class.java) {
                runBlocking { composite.nameShowWithFallback("d/example", emptyList()) }
            }
            assertEquals(listOf("primary"), calls)
        }

    @Test
    fun `random exceptions from primary cascade to next tier`() =
        runBlocking {
            val calls = mutableListOf<String>()
            val primary =
                NamecoinNameBackend {
                    calls += "primary"
                    throw RuntimeException("transport boom")
                }
            val defaults =
                NamecoinNameBackend {
                    calls += "defaults"
                    ok(it, """{"nostr":"de"}""")
                }
            val composite =
                CompositeNamecoinBackend(
                    primary = primary,
                    customElectrumx = null,
                    defaultElectrumx = defaults,
                    policy = NamecoinFallbackPolicy(fallbackToDefaultElectrumx = true),
                    isPrimaryCoreRpc = true,
                )
            val r = composite.nameShowWithFallback("d/example", emptyList())
            assertTrue(r != null)
            assertEquals(listOf("primary", "defaults"), calls)
        }
}
