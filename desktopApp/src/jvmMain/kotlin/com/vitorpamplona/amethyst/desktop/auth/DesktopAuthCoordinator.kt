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
package com.vitorpamplona.amethyst.desktop.auth

import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthApprovalDecision
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthApprovalPolicy
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthApprovalRequests
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthApprovalScope
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthApprovalStore
import com.vitorpamplona.amethyst.commons.relayClient.auth.PendingAuthApproval
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Desktop NIP-42 AUTH wiring.
 *
 * Today the desktop has NO AUTH wiring — relays demanding AUTH from desktop
 * users get silently ignored. This coordinator closes that gap, but does it
 * the security-conscious way:
 *
 * - **Tier 1 (auto-allow):** the relay is in the active account's NIP-17 DM
 *   inbox set (kind:10050). Sign immediately, no prompt.
 * - **Tier 2 (prompt):** anything else. Surface a [PendingAuthApproval] on
 *   [pendingApprovals]; the (forthcoming) inline AUTH banner reads from
 *   there and calls [resolve] with the user's `[Once] [Always] [Never]`
 *   pick.
 *
 * **Until the banner UI lands**, tier-2 approvals accumulate in
 * [pendingApprovals] but nothing resolves them — so tier-2 relays don't get
 * an AUTH response. Behaviour-wise that's the same outcome as the pre-this-
 * commit world (no AUTH at all). The improvement is tier-1: own DM-inbox
 * relays now AUTH automatically.
 *
 * Persisted `ALWAYS` / `BLOCKED` decisions are scoped per-account via
 * [PreferencesAuthApprovalStore].
 *
 * Lifecycle: bind to [AccountState] from the host (Main.kt) — call [onLogin]
 * when an account becomes [AccountState.LoggedIn] and [onLogout] on logout /
 * account-switch. Each call tears down the prior [RelayAuthenticator] and
 * cancels any pending deferreds.
 */
class DesktopAuthCoordinator(
    private val relayManager: RelayConnectionManager,
    private val localCache: DesktopLocalCache,
    private val scope: CoroutineScope,
) {
    private val lock = Any()

    @Volatile
    private var active: ActiveAuth? = null

    private val requests = AuthApprovalRequests()

    /**
     * Tier-2 AUTH challenges awaiting the user's `[Once] [Always] [Never]`
     * decision. The banner UI subscribes and calls [resolve] to settle each.
     */
    val pendingApprovals: StateFlow<PersistentMap<NormalizedRelayUrl, PendingAuthApproval>> = requests.pending

    /** Wire AUTH for a newly logged-in account. Idempotent. */
    fun onLogin(account: AccountState.LoggedIn) {
        synchronized(lock) {
            if (active?.pubKeyHex == account.pubKeyHex) return
            tearDownLocked()
            val store = PreferencesAuthApprovalStore(account.pubKeyHex)
            val policy =
                AuthApprovalPolicy(
                    selfApprovedRelays = { selfApprovedRelaysFor(account.pubKeyHex) },
                    store = store,
                    onPromptRequired = { pending -> requests.add(pending) },
                )
            val authenticator =
                RelayAuthenticator(
                    client = relayManager.client,
                    scope = scope,
                    signWithAllLoggedInUsers = { relayUrl, template, _ ->
                        val signed = signWithPolicy(account, relayUrl, template, policy)
                        signed?.let { listOf(it) } ?: emptyList()
                    },
                )
            active = ActiveAuth(account.pubKeyHex, store, policy, authenticator)
            Log.d("DesktopAuthCoordinator") { "AUTH wired for ${account.pubKeyHex.take(8)}" }
        }
    }

    /** Tear down AUTH on logout / account switch. */
    fun onLogout() {
        synchronized(lock) { tearDownLocked() }
    }

    /**
     * Resolve a tier-2 [PendingAuthApproval] from the banner UI. The suspended
     * signer wakes up exactly once with the user's pick.
     */
    fun resolve(
        relayUrl: NormalizedRelayUrl,
        scope: AuthApprovalScope,
    ) {
        requests.resolve(relayUrl, scope)
    }

    /**
     * Called when the active account's NIP-17 DM-inbox (kind:10050) set loads
     * or changes. Auto-approves any pending tier-2 prompt whose relay is now in
     * that set, fixing the cold-boot race where an own inbox relay challenges
     * for AUTH before kind:10050 has been fetched and gets a spurious prompt
     * that nothing would otherwise re-evaluate.
     *
     * [trusted] must be the strict kind:10050 inbox set (same tier-1 source as
     * [selfApprovedRelaysFor]) — never the lenient NIP-65 fallback.
     */
    fun onSelfApprovedRelaysChanged(trusted: Set<NormalizedRelayUrl>) {
        requests.autoApproveNowTrusted(trusted)
    }

    private fun tearDownLocked() {
        val prev = active ?: return
        prev.authenticator.destroy()
        // Cancel any in-flight tier-2 prompts so suspended signers wake up.
        requests.cancelAll()
        active = null
    }

    private fun selfApprovedRelaysFor(pubKeyHex: String): Set<NormalizedRelayUrl> {
        // Tier-1 = the user's own NIP-17 DM-inbox (kind:10050). Strict
        // by design — write/read relays (NIP-65 kind:10002) are NOT included,
        // because the user may have read-only relays they don't intend to
        // identify themselves to via AUTH.
        //
        // MUST use dmInboxRelaysStrict (kind:10050 only) rather than the
        // lenient dmInboxRelays helper, which falls back to NIP-65 read
        // relays and would silently expand tier-1 to include every relay
        // in the user's outbox. That defeats the tier-2 prompt for any
        // relay in the user's normal read set.
        val user = localCache.getOrCreateUser(pubKeyHex)
        return user.dmInboxRelaysStrict()?.toSet() ?: emptySet()
    }

    private suspend fun signWithPolicy(
        account: AccountState.LoggedIn,
        relayUrl: NormalizedRelayUrl,
        template: EventTemplate<RelayAuthEvent>,
        policy: AuthApprovalPolicy,
    ): RelayAuthEvent? =
        when (val decision = policy.classify(relayUrl)) {
            AuthApprovalDecision.Allow -> account.signer.sign(template)
            AuthApprovalDecision.Block -> null
            is AuthApprovalDecision.Pending -> {
                val resolved = decision.pending.await()
                if (resolved != AuthApprovalScope.ONCE) {
                    policy.recordDecision(relayUrl, resolved)
                }
                if (resolved == AuthApprovalScope.BLOCKED) null else account.signer.sign(template)
            }
        }

    private data class ActiveAuth(
        val pubKeyHex: String,
        val store: AuthApprovalStore,
        val policy: AuthApprovalPolicy,
        val authenticator: RelayAuthenticator,
    )
}
