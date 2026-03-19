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
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip47WalletConnect.tags.EncryptionTag
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

    fun supportsNotifications(): Boolean = capabilities().contains("notifications")

    fun encryptionSchemes() = tags.mapNotNull(EncryptionTag::parse).flatten()

    fun notificationTypes() = tags.mapNotNull(NotificationsTag::parse).flatten()

    companion object {
        const val KIND = 13194
        const val ALT_DESCRIPTION = "Wallet service info"

        fun build(
            capabilities: List<String>,
            encryptionSchemes: List<String>? = null,
            notificationTypes: List<String>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<NwcInfoEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, capabilities.joinToString(" "), createdAt) {
            alt(ALT_DESCRIPTION)
            encryptionSchemes?.let { addUnique(EncryptionTag.assemble(it)) }
            notificationTypes?.let { addUnique(NotificationsTag.assemble(it)) }
            initializer()
        }
    }
}
