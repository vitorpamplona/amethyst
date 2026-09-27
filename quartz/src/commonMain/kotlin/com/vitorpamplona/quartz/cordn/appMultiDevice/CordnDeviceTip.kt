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
package com.vitorpamplona.quartz.cordn.appMultiDevice

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.Tag

/**
 * §6 — the inventory a device publishes so another device can find its documents.
 *
 * ## Two events, two jobs
 *
 * The **outer** event is an addressable kind-[OUTER_KIND] event signed by an
 * ephemeral key that has nothing to do with the owner. It exists to be
 * replaceable on a relay and to leak nothing: an observer sees an unknown
 * account updating an opaque, randomly-`d`-tagged event.
 *
 * The **inner** event — this file's subject — is signed by the owner and sealed
 * inside the outer one's content. It is the authenticity guarantee: the outer
 * signature only says "whoever holds the ephemeral key put this here", which a
 * leaked connection string also lets someone do. What makes a document
 * inventory trustworthy is the owner's signature on the inner event, so a
 * reader MUST verify it and MUST NOT act on the outer event alone.
 *
 * ## Why the ephemeral key is not derived from the owner
 *
 * §6 forbids deriving it. A public derivation would let anyone compute the
 * signing pubkey from an `npub` and query for that person's tip, which is
 * precisely the linkage the design exists to prevent. [CordnConnectionString]
 * mints it randomly for the same reason.
 */
object CordnDeviceTip {
    /** The outer, relayed, addressable event. A coordination detail (§14). */
    const val OUTER_KIND: Kind = 30078

    /** The inner, sealed, never-relayed event. A coordination detail (§14). */
    const val INNER_KIND: Kind = 178

    const val TAG_X = "x"
    const val TAG_DEK = "dek"
    const val TAG_SERVER = "server"
    const val TAG_D = "d"

    /** The `x` tag's 3rd element for a group document. */
    const val KIND_GROUP = "group"

    /** The `x` tag's 3rd element for the meta document. */
    const val KIND_META = "meta"

    private const val DEK_HEX_LENGTH = 64

    /** The tags of the inner event for [inventory]. */
    fun tags(inventory: CordnTipInventory): Array<Tag> =
        buildList {
            inventory.groups.forEach { add(arrayOf(TAG_X, it.address, KIND_GROUP, it.gid)) }
            inventory.meta?.let { add(arrayOf(TAG_X, it, KIND_META)) }
            add(arrayOf(TAG_DEK, inventory.dekPrivateKey))
            // Ordered: §6 says a reader tries them in listed order, so the most
            // reliable host goes first.
            inventory.servers.forEach { add(arrayOf(TAG_SERVER, it)) }
        }.toTypedArray()

    /**
     * Reads a verified inner event.
     *
     * The caller is responsible for having decrypted the outer content and
     * checked the inner signature first — this only reads tags, and a tag set
     * says nothing about who wrote it.
     *
     * @throws CordnDocumentException when the inventory is unusable: no DEK, a
     * malformed DEK, or a `group` entry with no `gid`. A tip that cannot name
     * its documents is not a tip a device can act on partially.
     */
    fun parse(inner: Event): CordnTipInventory {
        if (inner.kind != INNER_KIND) {
            throw CordnDocumentException("tip inner event has kind ${inner.kind}, expected $INNER_KIND")
        }

        val groups = mutableListOf<CordnTipEntry>()
        var meta: String? = null
        var dek: String? = null
        val servers = mutableListOf<String>()

        inner.tags.forEach { tag ->
            if (tag.size < 2) return@forEach
            when (tag[0]) {
                TAG_X ->
                    when (tag.getOrNull(2)) {
                        KIND_GROUP -> {
                            val gid =
                                tag.getOrNull(3)
                                    ?: throw CordnDocumentException("a group x tag carries no gid")
                            groups += CordnTipEntry(address = tag[1], gid = gid)
                        }
                        // §4.3 allows exactly one meta entry. A second is a
                        // malformed tip; take the first and ignore the rest
                        // rather than fail, since either could be the real one
                        // and the address check still gates what we accept.
                        KIND_META -> if (meta == null) meta = tag[1]
                        else -> Unit
                    }
                TAG_DEK -> if (dek == null) dek = tag[1]
                TAG_SERVER -> servers += tag[1]
                else -> Unit
            }
        }

        val dekKey = dek ?: throw CordnDocumentException("tip carries no dek tag")
        if (dekKey.length != DEK_HEX_LENGTH || !dekKey.all { it.isHexDigit() }) {
            throw CordnDocumentException("tip dek is not 64 hex chars")
        }

        return CordnTipInventory(
            groups = groups,
            meta = meta,
            dekPrivateKey = dekKey.lowercase(),
            servers = servers,
        )
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}

/** One `x`-tagged group document in a tip. */
data class CordnTipEntry(
    /** `sha256` of the sealed blob; also its key on the content store. */
    val address: String,
    val gid: String,
)

/**
 * What a tip advertises.
 *
 * [dekPrivateKey] is the whole reason the inner event is sealed to the owner:
 * it is a private key in the clear inside that seal, and it opens every
 * document listed here.
 */
data class CordnTipInventory(
    val groups: List<CordnTipEntry>,
    val meta: String?,
    val dekPrivateKey: HexKey,
    /** Content-store hosts, in the order a reader should try them (§6). */
    val servers: List<String>,
)
