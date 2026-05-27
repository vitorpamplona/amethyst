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
package com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin

import kotlinx.coroutines.CancellationException

/**
 * Lightweight backend abstraction for a single name lookup.
 *
 * `null` means "this backend says: name not found (or unsupported)"; throw
 * [NamecoinLookupException.ServersUnreachable] when none of the underlying
 * endpoints could be reached and the caller should consider falling back to
 * the next tier.
 */
fun interface NamecoinNameBackend {
    suspend fun nameShow(identifier: String): NameShowResult?
}

/**
 * Backend that talks the ElectrumX light-client protocol.
 *
 * Adapts the existing [IElectrumXClient] to the [NamecoinNameBackend] shape
 * by wrapping it with a server-list provider.
 */
class ElectrumxNameBackend(
    private val client: IElectrumXClient,
    private val serversProvider: () -> List<ElectrumxServer>,
) : NamecoinNameBackend {
    override suspend fun nameShow(identifier: String): NameShowResult? {
        val servers = serversProvider()
        if (servers.isEmpty()) return null
        return client.nameShowWithFallback(identifier, servers)
    }
}

/**
 * Composite backend that chains a primary backend with optional ElectrumX
 * fallbacks. Implements [IElectrumXClient] so the resolver code stays
 * unchanged — the `servers` argument from the resolver is ignored here
 * because each underlying backend already knows where to look.
 *
 * The fallback chain is:
 *
 * 1. **primary** — Core RPC or custom ElectrumX (per user choice)
 * 2. **customElectrumx** (only when primary == Core RPC AND
 *    `policy.fallbackToCustomElectrumx`)
 * 3. **defaultElectrumx** (when `policy.fallbackToDefaultElectrumx`)
 *
 * Semantics:
 * - A "name not found" answer from a tier is **authoritative**: it short-
 *   circuits the chain and is returned to the caller. We do not silently
 *   replay the query against another backend, because that defeats the
 *   privacy intent (the lookup already happened on the chosen backend).
 * - A "servers unreachable" failure means we couldn't get a definitive
 *   answer; we cascade to the next configured tier.
 * - Any other thrown exception is also treated as a transport failure and
 *   triggers cascade, except for [CancellationException] which propagates.
 */
class CompositeNamecoinBackend(
    private val primary: NamecoinNameBackend,
    private val customElectrumx: NamecoinNameBackend? = null,
    private val defaultElectrumx: NamecoinNameBackend? = null,
    private val policy: NamecoinFallbackPolicy = NamecoinFallbackPolicy.NONE,
    private val isPrimaryCoreRpc: Boolean = false,
) : IElectrumXClient {
    override suspend fun nameShowWithFallback(
        identifier: String,
        servers: List<ElectrumxServer>,
    ): NameShowResult? {
        val chain = buildChain()
        var lastUnreachable: NamecoinLookupException.ServersUnreachable? = null

        for (backend in chain) {
            try {
                // Authoritative result (found or not-found) → return now.
                return backend.nameShow(identifier)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NamecoinLookupException.NameNotFound) {
                // Authoritative negative; do NOT cascade.
                throw e
            } catch (e: NamecoinLookupException.NameExpired) {
                // Authoritative; do NOT cascade.
                throw e
            } catch (e: NamecoinLookupException.ServersUnreachable) {
                lastUnreachable = e
                // fall through to next tier
            } catch (e: Exception) {
                // Treat as transport-level failure; cascade.
                lastUnreachable =
                    NamecoinLookupException.ServersUnreachable(lastError = e)
            }
        }

        // Exhausted the chain without an authoritative answer.
        throw lastUnreachable
            ?: NamecoinLookupException.ServersUnreachable(
                lastError = IllegalStateException("No Namecoin backend configured"),
            )
    }

    private fun buildChain(): List<NamecoinNameBackend> {
        val chain = mutableListOf<NamecoinNameBackend>()
        chain += primary
        if (isPrimaryCoreRpc && policy.fallbackToCustomElectrumx && customElectrumx != null) {
            chain += customElectrumx
        }
        if (policy.fallbackToDefaultElectrumx && defaultElectrumx != null) {
            chain += defaultElectrumx
        }
        return chain
    }
}
