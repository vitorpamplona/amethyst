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

    /**
     * Whether executing this request **signs an event as the user**. The shell — never the napplet
     * — holds the key and does the signing (matching `@napplet/shim`, which has no `sign()`). This
     * flag lets the broker defer the per-signature prompt to a remote/external signer that runs its
     * own consent UI, instead of double-prompting.
     */
    val signsAsUser: Boolean get() = false

    /** Read the active user's public key. */
    data object GetPublicKey : NappletRequest {
        override val capability get() = NappletCapability.IDENTITY
    }

    /**
     * Read a non-key identity datum (`identity.getProfile`/`getRelays`/`getFollows`/`getMutes`/…).
     * [method] is the bare method name and [argument] carries an optional parameter (e.g. the list
     * type for `getList`). The shell answers from the active account; a method this shell does not
     * implement resolves to Unsupported. No private key material is ever returned.
     */
    data class IdentityRead(
        val method: String,
        val argument: String? = null,
    ) : NappletRequest {
        override val capability get() = NappletCapability.IDENTITY
    }

    /** `shell.supports(domain, protocol?)` — capability negotiation; always answerable, no consent. */
    data class ShellSupports(
        val domain: String,
        val protocol: String? = null,
    ) : NappletRequest {
        override val capability get() = NappletCapability.SHELL
    }

    /**
     * Publish an event built from an **unsigned template**. The napplet supplies only `kind`,
     * `tags`, and `content`; the shell sets `pubkey` from the real signer, stamps `created_at`,
     * signs, and broadcasts — so a napplet can never sign as another identity, backdate, nor
     * obtain a raw signature. This is the *only* signing path exposed to napplets.
     */
    data class Publish(
        val kind: Int,
        val tags: Array<Array<String>>,
        val content: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RELAY
        override val signsAsUser get() = true

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Publish) return false
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

    /**
     * Encrypt [content] to [recipient] with [encryption] (`"nip44"`, default, or `"nip04"`), then
     * build, sign, and publish the event. The shell holds the key and performs both the encryption
     * and the signing; the napplet supplies only plaintext.
     */
    data class PublishEncrypted(
        val kind: Int,
        val tags: Array<Array<String>>,
        val content: String,
        val recipient: HexKey,
        val encryption: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RELAY
        override val signsAsUser get() = true

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PublishEncrypted) return false
            if (kind != other.kind) return false
            if (content != other.content) return false
            if (recipient != other.recipient) return false
            if (encryption != other.encryption) return false
            if (tags.size != other.tags.size) return false
            for (i in tags.indices) if (!tags[i].contentEquals(other.tags[i])) return false
            return true
        }

        override fun hashCode(): Int {
            var result = kind
            result = 31 * result + content.hashCode()
            result = 31 * result + recipient.hashCode()
            result = 31 * result + encryption.hashCode()
            result = 31 * result + tags.sumOf { it.contentHashCode() }
            return result
        }
    }

    /** Read events matching [filters] (from the cache and/or a bounded relay fetch). */
    data class QueryEvents(
        val filters: List<Filter>,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RELAY
    }

    /**
     * Subscribe to events matching [filters]. The shell opens a live relay subscription and pushes
     * `relay.event`/`relay.eose`/`relay.closed` keyed by the applet's `subId`.
     */
    data class Subscribe(
        val filters: List<Filter>,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RELAY
    }

    /** Read a value from this napplet's sandboxed key-value store (`storage.getItem`). */
    data class StorageGet(
        val key: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.STORAGE
    }

    /** Write a value to this napplet's sandboxed key-value store (`storage.setItem`). */
    data class StorageSet(
        val key: String,
        val value: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.STORAGE
    }

    /** Remove a value from this napplet's sandboxed key-value store (`storage.removeItem`). */
    data class StorageRemove(
        val key: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.STORAGE
    }

    /** List the keys this napplet has stored (`storage.keys`). */
    data object StorageKeys : NappletRequest {
        override val capability get() = NappletCapability.STORAGE
    }

    /**
     * `keys.registerAction` — register a named keyboard/command action. This is a shell-mediated UI
     * affordance, **not** signing (the `keys` domain has no key access). [actionId] is the napplet's
     * action id (echoed back); [label] is its display name.
     */
    data class RegisterAction(
        val actionId: String,
        val label: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.KEYS
    }

    /** `keys.unregisterAction` — drop a previously registered action (fire-and-forget). */
    data class UnregisterAction(
        val actionId: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.KEYS
    }

    /**
     * Pay a BOLT-11 invoice from the user's wallet (`value.payInvoice`). This is an
     * **Amethyst-specific extension** — the upstream `value` domain models zaps/value-transfer
     * differently — kept because a real napplet built against `@napplet/shim` never calls it, so
     * it cannot conflict.
     */
    data class PayInvoice(
        val invoice: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.VALUE
    }

    /** Fetch the bytes of an https/blossom/nostr/data resource (`resource.bytes`). */
    data class ResourceBytes(
        val url: String,
    ) : NappletRequest {
        override val capability get() = NappletCapability.RESOURCE
    }

    /** Upload a blob to the user's Blossom server (`upload.upload`). */
    data class UploadBlob(
        val bytes: ByteArray,
        val contentType: String,
        val filename: String? = null,
    ) : NappletRequest {
        override val capability get() = NappletCapability.UPLOAD

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UploadBlob) return false
            return contentType == other.contentType && filename == other.filename && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * (31 * contentType.hashCode() + filename.hashCode()) + bytes.contentHashCode()
    }
}
