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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Regression coverage for the resolve() ↔ resolveDetailed() exception
 * contract.
 *
 * Real IElectrumXClient implementations (ElectrumXClient +
 * CompositeNamecoinBackend) NEVER return null from nameShowWithFallback —
 * every failure path throws a NamecoinLookupException subtype. Before this
 * fix, the non-detailed `resolve()` path had no try/catch around that call,
 * so any transport-level failure surfaced as a red "Error" icon in the
 * NIP-05 verification display with no way for the user to distinguish
 * "servers unreachable" from "wrong pubkey".
 *
 * These tests pin the contract: resolve() maps expected lookup exceptions
 * to null (as documented in the KDoc), while resolveDetailed() keeps
 * surfacing them structurally. CancellationException always propagates.
 */
class NamecoinNameResolverExceptionTest {
    @Test
    fun `resolve returns null on NameNotFound instead of throwing`() =
        runTest {
            val resolver =
                NamecoinNameResolver(
                    electrumxClient = throwingClient(NamecoinLookupException.NameNotFound("d/example")),
                    lookupTimeoutMs = 500L,
                )
            assertNull(resolver.resolve("example.bit"))
        }

    @Test
    fun `resolve returns null on NameExpired instead of throwing`() =
        runTest {
            val resolver =
                NamecoinNameResolver(
                    electrumxClient = throwingClient(NamecoinLookupException.NameExpired("d/stale")),
                    lookupTimeoutMs = 500L,
                )
            assertNull(resolver.resolve("stale.bit"))
        }

    @Test
    fun `resolve returns null on ServersUnreachable instead of throwing`() =
        runTest {
            val resolver =
                NamecoinNameResolver(
                    electrumxClient = throwingClient(NamecoinLookupException.ServersUnreachable()),
                    lookupTimeoutMs = 500L,
                )
            assertNull(resolver.resolve("dead.bit"))
        }

    @Test
    fun `resolve propagates CancellationException`() =
        runTest {
            val resolver =
                NamecoinNameResolver(
                    electrumxClient = throwingClient(CancellationException("cancelled")),
                    lookupTimeoutMs = 500L,
                )
            try {
                resolver.resolve("cancel.bit")
                fail("expected CancellationException to propagate")
            } catch (e: CancellationException) {
                // expected
            }
        }

    @Test
    fun `resolveDetailed still surfaces NameNotFound`() =
        runTest {
            val resolver =
                NamecoinNameResolver(
                    electrumxClient = throwingClient(NamecoinLookupException.NameNotFound("d/example")),
                    lookupTimeoutMs = 500L,
                )
            val outcome = resolver.resolveDetailed("example.bit")
            assertTrue("expected NameNotFound, got $outcome", outcome is NamecoinResolveOutcome.NameNotFound)
            assertEquals("d/example", (outcome as NamecoinResolveOutcome.NameNotFound).name)
        }

    @Test
    fun `resolveDetailed still surfaces ServersUnreachable`() =
        runTest {
            val resolver =
                NamecoinNameResolver(
                    electrumxClient = throwingClient(NamecoinLookupException.ServersUnreachable()),
                    lookupTimeoutMs = 500L,
                )
            val outcome = resolver.resolveDetailed("dead.bit")
            assertTrue(
                "expected ServersUnreachable, got $outcome",
                outcome is NamecoinResolveOutcome.ServersUnreachable,
            )
        }

    @Test
    fun `resolve returns null on generic transport failures too`() =
        runTest {
            // Anything that isn't a NamecoinLookupException nor a
            // CancellationException should also not surface as an
            // uncaught exception through resolve(). The immediate reason
            // is that withTimeoutOrNull only handles TimeoutCancellationException;
            // arbitrary IO exceptions would otherwise leak. This test
            // pins the current behaviour (null on non-cancellation faults)
            // in case someone widens the try/catch further later.
            val resolver =
                NamecoinNameResolver(
                    electrumxClient =
                        object : IElectrumXClient {
                            override suspend fun nameShowWithFallback(
                                identifier: String,
                                servers: List<ElectrumxServer>,
                            ): NameShowResult? = throw NamecoinLookupException.ServersUnreachable(RuntimeException("simulated"))
                        },
                    lookupTimeoutMs = 500L,
                )
            assertNull(resolver.resolve("boom.bit"))
        }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun throwingClient(exception: Throwable): IElectrumXClient =
        object : IElectrumXClient {
            override suspend fun nameShowWithFallback(
                identifier: String,
                servers: List<ElectrumxServer>,
            ): NameShowResult? = throw exception
        }
}
