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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * `marmot.transport.nostr.routing.v1`, component `0x8004` — where a
 * Nostr-routed group's kind:445 traffic goes.
 *
 * ```text
 * struct { opaque url<1..512>; } MarmotNostrRelayV1;
 * struct {
 *   opaque nostr_group_id[32];
 *   MarmotNostrRelayV1 relays<V>;
 * } MarmotNostrRoutingV1;
 * ```
 *
 * `nostr_group_id` is 32 RAW bytes with no length prefix, followed by a
 * var-bytes vector whose payload is a sequence of var-bytes URLs.
 *
 * The routing id is a delivery address, not an identity: it MUST come from
 * cryptographically secure randomness and MUST NOT be derived from any account
 * id, member id, public key, MLS group id, KeyPackage id, message id, or relay
 * URL. Deriving it would let relays link a group to its members.
 *
 * ## Rotation
 *
 * Changing `nostr_group_id` is a rotation, and the commit carrying it must be
 * published to the PRIOR epoch's address — that is where members are listening.
 * Members keep accepting traffic at a prior address while any epoch that used
 * it is still inside a retained-history window, so a client has to be able to
 * map several routing ids to one group.
 *
 * Rotating after a removal does not hide anything by itself: a removed member
 * can decrypt the removal commit under the source epoch and read a routing
 * update carried in it. Hiding a replacement id takes two commits — remove
 * first, rotate from the post-removal epoch.
 */
data class NostrRoutingV1(
    val nostrGroupId: ByteArray,
    /** Sorted, unique, 1..16 relay URLs, byte-compared exactly. */
    val relays: List<String>,
) {
    init {
        require(nostrGroupId.size == GROUP_ID_SIZE) {
            "nostr_group_id must be $GROUP_ID_SIZE bytes, was ${nostrGroupId.size}"
        }
        require(relays.isNotEmpty()) { "Nostr routing relay list must not be empty" }
        require(relays.size <= MAX_RELAYS) { "Nostr routing relay list exceeds $MAX_RELAYS entries" }
        relays.forEach { requireValidRelayUrl(it) }
        for (i in 1 until relays.size) {
            require(relays[i - 1] != relays[i]) { "Nostr routing relay list contains a duplicate" }
            require(relays[i - 1] < relays[i]) { "Nostr routing relay list must be sorted" }
        }
    }

    val nostrGroupIdHex: HexKey get() = nostrGroupId.toHexKey()

    fun encode(): ByteArray {
        val entries = TlsWriter()
        relays.forEach { entries.putOpaqueVarInt(it.encodeToByteArray()) }

        val writer = TlsWriter()
        writer.putBytes(nostrGroupId)
        writer.putOpaqueVarInt(entries.toByteArray())
        return writer.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NostrRoutingV1) return false
        return nostrGroupId.contentEquals(other.nostrGroupId) && relays == other.relays
    }

    override fun hashCode(): Int = 31 * nostrGroupId.contentHashCode() + relays.hashCode()

    companion object {
        const val COMPONENT_ID = AppComponentIds.NOSTR_ROUTING_V1
        const val GROUP_ID_SIZE = 32
        const val MAX_RELAYS = 16
        const val MAX_RELAY_URL_BYTES = 512

        /** Build from arbitrary relay URLs, sorting and de-duplicating. */
        fun of(
            nostrGroupId: ByteArray,
            relays: Collection<String>,
        ) = NostrRoutingV1(nostrGroupId, relays.distinct().sorted())

        fun decode(bytes: ByteArray): NostrRoutingV1 {
            require(bytes.size >= GROUP_ID_SIZE) { "Nostr routing component is missing nostr_group_id" }
            val reader = TlsReader(bytes)
            val groupId = reader.readBytes(GROUP_ID_SIZE)
            val relayVector = reader.readOpaqueVarInt()
            require(!reader.hasRemaining) { "Nostr routing component has trailing bytes" }

            val relayReader = TlsReader(relayVector)
            val relays = mutableListOf<String>()
            while (relayReader.hasRemaining) {
                relays.add(relayReader.readOpaqueVarInt().decodeToString())
            }
            return NostrRoutingV1(groupId, relays)
        }

        /**
         * The Nostr relay URL profile from `transports/nostr.md`.
         *
         * Deliberately a hand-rolled check rather than a pass through this
         * project's relay-URL normalizer: the component's bytes are signed
         * group state, and a client MUST NOT rewrite them while applying it.
         * Anything that could normalize is the wrong tool here.
         */
        fun requireValidRelayUrl(url: String) {
            val bytes = url.encodeToByteArray()
            require(bytes.isNotEmpty()) { "relay URL must not be empty" }
            require(bytes.size <= MAX_RELAY_URL_BYTES) {
                "relay URL exceeds $MAX_RELAY_URL_BYTES bytes"
            }
            val scheme =
                when {
                    url.startsWith("wss://") -> "wss://"
                    url.startsWith("ws://") -> "ws://"
                    else -> throw IllegalArgumentException("relay URL scheme must be ws or wss: $url")
                }
            require(!url.contains('#')) { "relay URL must not carry a fragment: $url" }

            val afterScheme = url.substring(scheme.length)
            val authority = afterScheme.takeWhile { it != '/' && it != '?' }
            require(authority.isNotEmpty()) { "relay URL must have a host: $url" }
            require(!authority.contains('@')) {
                "relay URL must not carry a username or password: $url"
            }
        }
    }
}
