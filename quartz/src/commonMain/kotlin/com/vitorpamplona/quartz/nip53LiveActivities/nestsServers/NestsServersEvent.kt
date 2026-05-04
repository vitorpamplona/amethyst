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
package com.vitorpamplona.quartz.nip53LiveActivities.nestsServers

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * One audio-room server entry as published in `kind:10112`. Each entry
 * names a deployment by its **two** distinct URLs: the moq-relay
 * WebTransport endpoint ([relay], goes into the kind-30312 `streaming`
 * tag) and the moq-auth sidecar base URL ([auth], goes into the
 * kind-30312 `auth` tag). The deployed nostrnests reference puts these
 * on different hosts (`moq.nostrnests.com:4443` for the relay,
 * `moq-auth.nostrnests.com` for auth) so the pair MUST be carried
 * together — collapsing them to a single URL breaks the JWT-mint flow
 * (the auth host doesn't accept TCP on the relay port and vice-versa).
 */
@Immutable
data class NestsServer(
    val relay: String,
    val auth: String,
)

/**
 * Replaceable event listing the audio-room (NIP-53 / nests) MoQ host
 * servers a user prefers to publish their kind-30312 spaces against.
 *
 * Kind: **10112** — declared by nostrnests's reference README under
 * "User-published audio server lists". Wire shape mirrors
 * BlossomServersEvent's 10063 layout, but every `server` entry carries
 * **three** elements (tag name, relay URL, auth URL):
 *
 *     {
 *       "kind": 10112,
 *       "tags": [
 *         ["alt", "Audio-room (nests) MoQ servers used by the author"],
 *         ["server", "https://moq.nostrnests.com:4443", "https://moq-auth.nostrnests.com"],
 *         ["server", "https://relay.example.org:4443",  "https://moq-auth.example.org"],
 *         ...
 *       ],
 *       "content": ""
 *     }
 *
 * On the read side we tolerate two legacy shapes the deployed
 * nostrnests reference also accepts:
 *   - First-element name `relay` instead of `server` (earliest
 *     iteration of the React app).
 *   - 2-element form `["server", relay]` with the auth URL omitted.
 *     Receivers MUST derive the auth host: replace a leading `moq.`
 *     with `moq-auth.`, or prepend `moq-auth.` when no `moq.` prefix
 *     is present.
 *
 * Authoritative reference: NestsUI-v2 `useMoqServerList` hook.
 */
@Immutable
class NestsServersEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * Decode every well-formed `server` (or legacy `relay`) tag into a
     * [NestsServer] pair. A 2-element form gets its auth URL derived
     * via [deriveAuthUrl]; a fully-malformed tag is dropped.
     */
    fun servers(): List<NestsServer> =
        tags.mapNotNull { tag ->
            if (tag.size < 2) return@mapNotNull null
            if (tag[0] != "server" && tag[0] != "relay") return@mapNotNull null
            val relay = tag[1].takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val auth = tag.getOrNull(2)?.takeIf { it.isNotBlank() } ?: deriveAuthUrl(relay)
            NestsServer(relay = relay, auth = auth ?: return@mapNotNull null)
        }

    companion object {
        const val KIND = 10112
        const val ALT = "Audio-room (nests) MoQ servers used by the author"

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun createAddressTag(pubKey: HexKey): String = Address.assemble(KIND, pubKey, FIXED_D_TAG)

        fun createTagArray(servers: List<NestsServer>): Array<Array<String>> =
            servers
                .map { arrayOf("server", it.relay, it.auth) }
                .plusElement(AltTag.assemble(ALT))
                .toTypedArray()

        suspend fun updateRelayList(
            earlierVersion: NestsServersEvent,
            servers: List<NestsServer>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): NestsServersEvent {
            val tags =
                earlierVersion.tags
                    .filter { it[0] != "server" && it[0] != "relay" }
                    .plus(servers.map { arrayOf("server", it.relay, it.auth) })
                    .toTypedArray()

            return signer.sign(createdAt, KIND, tags, earlierVersion.content)
        }

        suspend fun createFromScratch(
            servers: List<NestsServer>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = create(servers, signer, createdAt)

        suspend fun create(
            servers: List<NestsServer>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = signer.sign<NestsServersEvent>(createdAt, KIND, createTagArray(servers), "")

        /**
         * Derive the moq-auth base URL from a moq-relay URL when a
         * legacy 2-element `server` tag omits the auth URL. Mirrors
         * the deployed nostrnests fallback in
         * `NestsUI-v2/useMoqServerList`:
         *
         *  - Replace a leading `moq.` host label with `moq-auth.`
         *  - Otherwise prepend `moq-auth.` to the existing host
         *  - Strip the relay's explicit port (the auth sidecar always
         *    lives on regular HTTPS / port 443)
         *
         * Returns null when the input isn't a parseable
         * `<scheme>://<host>[:port][/...]` URL — caller drops the entry.
         */
        fun deriveAuthUrl(relayUrl: String): String? {
            val schemeEnd = relayUrl.indexOf("://")
            if (schemeEnd <= 0) return null
            val scheme = relayUrl.substring(0, schemeEnd)
            val rest = relayUrl.substring(schemeEnd + 3)
            val pathSlash = rest.indexOf('/')
            val authority = if (pathSlash < 0) rest else rest.substring(0, pathSlash)
            if (authority.isEmpty()) return null
            val portColon = authority.indexOf(':')
            val host = if (portColon < 0) authority else authority.substring(0, portColon)
            if (host.isEmpty()) return null
            val derivedHost =
                if (host.startsWith("moq.")) {
                    "moq-auth." + host.substring("moq.".length)
                } else {
                    "moq-auth.$host"
                }
            return "$scheme://$derivedHost"
        }
    }
}
