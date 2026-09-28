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
package com.vitorpamplona.quartz.nip47WalletConnect.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip47WalletConnect.tags.EncryptionTag
import com.vitorpamplona.quartz.nip47WalletConnect.tags.ExtensionsTag
import com.vitorpamplona.quartz.nip47WalletConnect.tags.NotificationsTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class NwcInfoEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun capabilities(): List<String> = content.split(" ").filter { it.isNotBlank() }

    fun supportsMethod(method: String): Boolean = capabilities().contains(method)

    /**
     * Whether the wallet sends NWC-02 notifications (kind 23197/23196).
     *
     * Three signals count, since wallets predate and postdate the move of
     * notifications out of NIP-47 core into the NWC-02 extension:
     * - legacy: the bare `notifications` token in the content;
     * - current: `02` in the `extensions` tag;
     * - either: a non-empty `notifications` tag listing the notification types.
     */
    fun supportsNotifications(): Boolean =
        capabilities().contains(ExtensionsTag.LEGACY_NOTIFICATIONS_CAPABILITY) ||
            supportsExtension(ExtensionsTag.NOTIFICATIONS) ||
            notificationTypes().isNotEmpty()

    // NIP-47 carries the schemes/types as a single space-separated string in one
    // tag value (e.g. ["encryption", "nip44_v2 nip04"]). Split on whitespace so we
    // return individual tokens, while still tolerating a multi-element tag.
    private fun spaceSeparatedTag(parse: (Array<String>) -> List<String>?) =
        tags
            .mapNotNull(parse)
            .flatten()
            .flatMap { it.split(" ") }
            .filter { it.isNotBlank() }

    fun encryptionSchemes() = spaceSeparatedTag(EncryptionTag::parse)

    fun notificationTypes() = spaceSeparatedTag(NotificationsTag::parse)

    /** The optional NWC extension specs this wallet advertises (eg. `["05", "06"]`). */
    fun extensions() = spaceSeparatedTag(ExtensionsTag::parse)

    /**
     * Whether the wallet advertises a given NWC extension spec.
     *
     * A wallet that says nothing reads as **no**. That direction is deliberate:
     * the caller is deciding whether to send something the wallet may not
     * understand, so silence must not be read as permission.
     */
    fun supportsExtension(id: String) = extensions().contains(id)

    /** Whether the wallet publishes an `extensions` tag at all (a post-extensions NIP-47 wallet). */
    fun advertisesExtensions() = tags.any { ExtensionsTag.parse(it) != null }

    /**
     * Whether a client should send [method], a method defined by NWC extension [extension]
     * (see [ExtensionsTag.forMethod]). Core methods (no extension) always pass.
     *
     * - `true` when the content lists [method] or the `extensions` tag lists [extension].
     * - `false` only when the wallet publishes an `extensions` tag that lacks [extension]
     *   AND the content does not list [method]: that wallet speaks the extension-aware
     *   NIP-47 and has told us it does not implement it.
     * - `true` otherwise: a pre-extensions wallet that simply doesn't mention the method
     *   is given the benefit of the doubt, as clients always did, and answers with
     *   `NOT_IMPLEMENTED` if it really can't.
     *
     * Unlike [supportsExtension] this errs towards sending, because the cost of a wrong
     * guess is a clean error response rather than a changed request.
     */
    fun mayUseExtensionMethod(
        method: String,
        extension: String? = ExtensionsTag.forMethod(method),
    ): Boolean {
        if (extension == null || supportsMethod(method) || supportsExtension(extension)) return true
        return !advertisesExtensions()
    }

    companion object {
        const val KIND = 13194

        fun build(
            capabilities: List<String>,
            encryptionSchemes: List<String>? = null,
            notificationTypes: List<String>? = null,
            extensions: List<String>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<NwcInfoEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, capabilities.joinToString(" "), createdAt) {
            encryptionSchemes?.let { addUnique(EncryptionTag.assemble(it)) }
            notificationTypes?.let { addUnique(NotificationsTag.assemble(it)) }
            extensions?.let { addUnique(ExtensionsTag.assemble(it)) }
            initializer()
        }
    }
}
