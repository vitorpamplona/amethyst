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

import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthApprovalScope
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthApprovalStore
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.sha256.sha256
import java.util.prefs.Preferences

/**
 * Desktop persistence backend for [AuthApprovalStore] using
 * `java.util.prefs.Preferences`.
 *
 * Trade-offs vs the full SQLite `auth_approvals` table proposed in the plan:
 *
 * - **Pro**: zero new dependencies, no schema migration, already proven for
 *   other small desktop settings (per memory: `SearchHistoryStore`,
 *   `DesktopPreferences`).
 * - **Con**: flat key/value, no transactions, no native TTL. Acceptable here
 *   because the approval set per account is small (≪50 relays for any user)
 *   and the read pattern is "look up before signing AUTH" — once per relay
 *   per session, easily cached in memory by the [AuthApprovalPolicy] layer.
 *
 * Per-account scoping is by Preferences node: each account gets its own node
 * at `/com/vitorpamplona/amethyst/desktop/auth/<full-pubkey>/`. Logout calls
 * [clear] which `removeNode()`s the per-account subtree.
 *
 * `ONCE` scope is never persisted — that's the in-memory contract enforced
 * by the [AuthApprovalStore] interface. This implementation only writes
 * `ALWAYS` and `BLOCKED`.
 *
 * **Key length:** `java.util.prefs.Preferences` caps keys at
 * [Preferences.MAX_KEY_LENGTH] (80 chars) and throws `IllegalArgumentException`
 * from [Preferences.put] for anything longer. Relay URLs routinely exceed that
 * — e.g. an outbox-proxy URL that embeds an npub and a query string
 * (`wss://filter.nostr.wine/npub1…?broadcast=true`, 100+ chars). Storing such a
 * URL raw made [setScope] throw; the caller (`RelayAuthenticator`) swallows the
 * exception, so the `ALWAYS` / `BLOCKED` grant was silently never persisted and
 * the AUTH banner re-appeared on every challenge. [keyFor] folds any over-long
 * URL into a bounded 64-char SHA-256 hex key to stay under the cap.
 */
class PreferencesAuthApprovalStore(
    private val accountPubKeyHex: String,
) : AuthApprovalStore {
    private val node: Preferences =
        Preferences.userRoot().node(
            "/com/vitorpamplona/amethyst/desktop/auth/$accountPubKeyHex",
        )

    /**
     * The Preferences key for a relay URL, guaranteed to fit within
     * [Preferences.MAX_KEY_LENGTH].
     *
     * Short URLs are stored verbatim (readable, and backward-compatible with
     * grants written before this fix). URLs at or over the limit are hashed to
     * a `sha256:`-prefixed 64-char hex digest (71 chars total, under the 80
     * cap). The prefix keeps the hashed keyspace disjoint from raw relay URLs,
     * which always start with `ws://` / `wss://`, so the two can never collide.
     */
    private fun keyFor(relayUrl: NormalizedRelayUrl): String {
        val url = relayUrl.url
        return if (url.length <= Preferences.MAX_KEY_LENGTH) {
            url
        } else {
            "sha256:" + sha256(url.encodeToByteArray()).toHexKey()
        }
    }

    override suspend fun getScope(relayUrl: NormalizedRelayUrl): AuthApprovalScope? {
        val raw = node.get(keyFor(relayUrl), null) ?: return null
        return runCatching { AuthApprovalScope.valueOf(raw) }.getOrNull()
    }

    override suspend fun setScope(
        relayUrl: NormalizedRelayUrl,
        scope: AuthApprovalScope,
    ) {
        if (scope == AuthApprovalScope.ONCE) {
            // ONCE is the session-only contract from AuthApprovalStore — must
            // not touch the persistent store, otherwise it would silently
            // upgrade to "until next clear()".
            return
        }
        node.put(keyFor(relayUrl), scope.name)
        node.flush()
    }

    override suspend fun clear() {
        node.removeNode()
        node.flush()
    }
}
