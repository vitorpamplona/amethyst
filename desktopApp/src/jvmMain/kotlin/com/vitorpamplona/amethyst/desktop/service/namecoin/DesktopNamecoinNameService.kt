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
package com.vitorpamplona.amethyst.desktop.service.namecoin

import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinResolveState
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.CompositeNamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.DEFAULT_ELECTRUMX_SERVERS
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxNameBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.IElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NameShowResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinLookupCache
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNostrResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.RpcProbeResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.net.SocketFactory

/**
 * Desktop application-level singleton for Namecoin name resolution.
 *
 * Same functionality as the Android `NamecoinNameService` but instantiated
 * directly (no Koin/Hilt DI). Uses plain JVM sockets for ElectrumX (no
 * Tor support on Desktop yet) and an injectable [OkHttpClient] provider
 * for Namecoin Core JSON-RPC traffic.
 *
 * Backend dispatch mirrors Android's `AppModules.kt#buildNamecoinBackend`
 * exactly: for every lookup the service builds a fresh
 * [CompositeNamecoinBackend] from the current [NamecoinSettings] so that
 * changes in the Settings UI take effect immediately, without restarting
 * the app.
 */
class DesktopNamecoinNameService(
    private val preferencesProvider: () -> NamecoinSettings = { NamecoinSettings.DEFAULT },
    pinnedCertsProvider: () -> List<String> = { emptyList() },
    /**
     * OkHttpClient provider for Namecoin Core JSON-RPC traffic. The lambda
     * receives the target URL so a Tor-routing implementation can pick
     * the right client (direct vs. proxy) per request. When `null`, the
     * Core RPC backend isn't built and resolution behaves exactly as
     * before (ElectrumX only).
     */
    private val coreRpcHttpClientProvider: ((String) -> OkHttpClient)? = null,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val electrumxClient =
        ElectrumXClient(
            socketFactory = { SocketFactory.getDefault() },
        ).also { client ->
            // Push any persisted TOFU-pinned certs into the client at startup so
            // user-accepted pins survive process restart. Mirrors Android's
            // AppModules.kt path.
            scope.launch {
                try {
                    val pinned = pinnedCertsProvider()
                    if (pinned.isNotEmpty()) {
                        client.setDynamicCerts(pinned)
                    }
                } catch (_: Exception) {
                    // Non-fatal — defaults still work, user can re-pin via Settings.
                }
            }
        }

    /**
     * Long-lived Namecoin Core JSON-RPC client. Lazy because callers that
     * never enable the Core RPC backend shouldn't pay for it. Bootstraps
     * the active config + pinned trust store from the same shared
     * preferences entry the ElectrumX client uses, exactly like Android's
     * `AppModules.namecoinCoreRpcClient` path.
     */
    private val namecoinCoreRpcClient: NamecoinCoreRpcClient? =
        coreRpcHttpClientProvider?.let { provider ->
            NamecoinCoreRpcClient(httpClientForUrl = provider).also { client ->
                scope.launch {
                    try {
                        client.setConfig(preferencesProvider().namecoinCoreRpc)
                        val pinned = pinnedCertsProvider()
                        if (pinned.isNotEmpty()) {
                            client.setDynamicCerts(pinned)
                        }
                    } catch (_: Exception) {
                        // Non-fatal — user can re-pin via Settings.
                    }
                }
            }
        }

    /**
     * Expose the underlying ElectrumX client so the Settings UI can push a
     * newly-accepted cert in via [ElectrumXClient.setDynamicCerts] without
     * waiting for an app restart, and so a Test Connection button can call
     * [ElectrumXClient.testServer] directly.
     */
    val client: ElectrumXClient get() = electrumxClient

    /**
     * Expose the underlying Namecoin Core RPC client so the Settings UI
     * can call [NamecoinCoreRpcClient.probe] and push newly-accepted
     * pins in via [NamecoinCoreRpcClient.setDynamicCerts]. `null` when
     * the service was constructed without a Core RPC HTTP provider
     * (Core RPC backend disabled).
     */
    val rpcClient: NamecoinCoreRpcClient? get() = namecoinCoreRpcClient

    /**
     * Compose the active Namecoin lookup backend based on user settings.
     *
     * Builds a fresh composite per call so settings changes take effect
     * immediately. Same shape as Android's `AppModules.buildNamecoinBackend()`,
     * minus the Tor-server-list branch which Desktop doesn't have yet.
     */
    private fun buildNamecoinBackend(): IElectrumXClient {
        val settings = preferencesProvider()
        val custom = settings.toElectrumxServers()
        val defaults = DEFAULT_ELECTRUMX_SERVERS

        val customExBackend =
            custom?.let { servers -> ElectrumxNameBackend(electrumxClient) { servers } }
        val defaultExBackend = ElectrumxNameBackend(electrumxClient) { defaults }

        return when (settings.backend) {
            NamecoinBackend.NAMECOIN_CORE_RPC -> {
                val rpc = namecoinCoreRpcClient
                if (rpc == null) {
                    // No HTTP provider plumbed in — degrade gracefully to
                    // ElectrumX so the user isn't stranded. Mirrors the
                    // "primary unreachable" path inside CompositeNamecoinBackend.
                    buildElectrumxComposite(customExBackend, defaultExBackend, settings)
                } else {
                    // Refresh client config in case the user just saved it.
                    rpc.setConfig(settings.namecoinCoreRpc)
                    CompositeNamecoinBackend(
                        primary = rpc,
                        customElectrumx = customExBackend,
                        defaultElectrumx = defaultExBackend,
                        policy = settings.toFallbackPolicy(),
                        isPrimaryCoreRpc = true,
                    )
                }
            }
            NamecoinBackend.ELECTRUMX -> buildElectrumxComposite(customExBackend, defaultExBackend, settings)
        }
    }

    private fun buildElectrumxComposite(
        customExBackend: NamecoinNameBackend?,
        defaultExBackend: NamecoinNameBackend,
        settings: NamecoinSettings,
    ): IElectrumXClient {
        // Custom servers first (if any). If the user only has the public
        // defaults configured, primary == defaultElectrumx and the
        // fallback toggle is moot.
        val primary: NamecoinNameBackend = customExBackend ?: defaultExBackend
        return CompositeNamecoinBackend(
            primary = primary,
            customElectrumx = null,
            defaultElectrumx = if (customExBackend != null) defaultExBackend else null,
            policy = settings.toFallbackPolicy(),
            isPrimaryCoreRpc = false,
        )
    }

    private val resolver =
        NamecoinNameResolver(
            electrumxClient =
                object : IElectrumXClient {
                    override suspend fun nameShowWithFallback(
                        identifier: String,
                        servers: List<ElectrumxServer>,
                    ): NameShowResult? = buildNamecoinBackend().nameShowWithFallback(identifier, servers)
                },
            serverListProvider = {
                // Kept for compatibility with NamecoinNameResolver's API; the
                // composite backend ignores this and consults settings directly.
                preferencesProvider().toElectrumxServers() ?: DEFAULT_ELECTRUMX_SERVERS
            },
        )

    private val cache = NamecoinLookupCache()

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Resolve a Namecoin identifier to a Nostr pubkey.
     *
     * Returns cached results when available. This is the primary method
     * that the search bar and NIP-05 verifier should call.
     *
     * @param identifier e.g. "alice@example.bit", "id/bob", "example.bit"
     * @return [NamecoinNostrResult] or null
     */
    suspend fun resolve(identifier: String): NamecoinNostrResult? {
        val cached = cache.get(identifier)
        if (cached != null) return cached.result

        val result = resolver.resolve(identifier)
        cache.put(identifier, result)
        return result
    }

    /**
     * Resolve and return just the hex pubkey, or null.
     * Convenience for follow-import integration.
     */
    suspend fun resolvePubkey(identifier: String): String? = resolve(identifier)?.pubkey

    /**
     * Resolve with detailed outcome for error reporting.
     */
    suspend fun resolveDetailed(identifier: String): NamecoinResolveOutcome = resolver.resolveDetailed(identifier)

    /**
     * Verify that a Namecoin name maps to the expected pubkey.
     */
    suspend fun verifyNip05(
        nip05Address: String,
        expectedPubkeyHex: String,
    ): Boolean {
        if (!NamecoinNameResolver.isNamecoinIdentifier(nip05Address)) return false
        val result = resolve(nip05Address) ?: return false
        return result.pubkey.equals(expectedPubkeyHex, ignoreCase = true)
    }

    /**
     * Perform a lookup and emit results via a StateFlow.
     *
     * Useful for composable UIs that observe resolution state.
     */
    fun resolveLive(
        identifier: String,
        scope: CoroutineScope = this.scope,
    ): StateFlow<NamecoinResolveState> {
        val state = MutableStateFlow<NamecoinResolveState>(NamecoinResolveState.Loading)
        scope.launch {
            try {
                val result = resolve(identifier)
                state.value =
                    if (result != null) {
                        NamecoinResolveState.Resolved(result)
                    } else {
                        NamecoinResolveState.NotFound
                    }
            } catch (e: Exception) {
                state.value = NamecoinResolveState.Error(e.message ?: "Unknown error")
            }
        }
        return state
    }

    /**
     * Probe a candidate Namecoin Core RPC config (URL + creds) without
     * mutating the persisted client config. Used by the Settings
     * "Test RPC" button. Returns null when no Core RPC HTTP provider
     * was plumbed in — callers should hide the Test RPC UI in that case.
     */
    suspend fun probeCoreRpc(cfg: NamecoinCoreRpcConfig): RpcProbeResult? = namecoinCoreRpcClient?.probe(cfg)

    /**
     * Clear the resolution cache.
     */
    suspend fun clearCache() = cache.clear()
}
