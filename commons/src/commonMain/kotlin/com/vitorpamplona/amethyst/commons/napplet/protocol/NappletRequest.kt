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
package com.vitorpamplona.amethyst.commons.napplet.protocol

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * A capability request from a napplet, after it has crossed the postMessage + IPC edge
 * and been deserialized into a typed object. Each variant declares the [capability] the
 * broker must check before running it; the broker never trusts a value the applet sends
 * for that — it is derived here from the request type.
 *
 * Wire (JSON over postMessage, then a string across Binder) is marshalled at the Android
 * edge so this layer stays KMP-pure and unit-testable. Identity-bearing fields the applet
 * could try to spoof (e.g. "sign as someone else") are constrained by the broker, not here.
 */
sealed interface NappletRequest {
    val capability: NappletCapability

    /** Read the active user's public key. */
    data object GetPublicKey : NappletRequest {
        override val capability get() = NappletCapability.IDENTITY
    }

    /**
     * Build and sign an event **as the active user**. The applet supplies only the template
     * fields; the broker sets `pubkey` from the real signer and stamps `created_at`, so the
     * applet can never sign as another identity nor backdate.
     */
    data class SignEvent(
        val kind: Int,
        val tags: Array<Array<String>>,
        val content: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.IDENTITY

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SignEvent) return false
            if (kind != other.kind) return false
            if (content != other.content) return false
            if (tags.size != other.tags.size) return false
            for (i in tags.indices) if (!tags[i].contentEquals(other.tags[i])) return false
            return true
        }

        override fun hashCode(): Int {
            var result = kind
            result = 31 * result + content.hashCode()
            result = 31 * result + tags.sumOf { it.contentHashCode() }
            return result
        }
    }

    data class Nip04Encrypt(
        val peerPubKey: HexKey,
        val plaintext: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.IDENTITY
    }

    data class Nip04Decrypt(
        val peerPubKey: HexKey,
        val ciphertext: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.IDENTITY
    }

    data class Nip44Encrypt(
        val peerPubKey: HexKey,
        val plaintext: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.IDENTITY
    }

    data class Nip44Decrypt(
        val peerPubKey: HexKey,
        val ciphertext: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.IDENTITY
    }

    /**
     * Publish an already-signed [event] to the user's relays. The broker verifies the
     * signature and that the event belongs to the active user before publishing.
     */
    data class Publish(
        val event: Event,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RELAY
    }

    /** Read events matching [filter] (from the cache and/or a bounded relay fetch). */
    data class QueryEvents(
        val filter: Filter,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RELAY
    }

    /** Read a value from this napplet's sandboxed key-value store. */
    data class StorageGet(
        val key: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.STORAGE
    }

    /** Write a value to this napplet's sandboxed key-value store. */
    data class StorageSet(
        val key: String,
        val value: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.STORAGE
    }

    /** Remove a value from this napplet's sandboxed key-value store. */
    data class StorageRemove(
        val key: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.STORAGE
    }

    /** Pay a BOLT-11 invoice from the user's wallet. */
    data class PayInvoice(
        val invoice: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.WALLET
    }
}
