/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.experimental.decoupling.setup

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decoupling.setup.tags.KeyTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventUpdate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class EncryptionKeyListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun keys() = tags.mapNotNull(KeyTag::parse)

    companion object {
        const val KIND = 10044
        const val ALT = "Encryption keys"

        fun add(
            key: KeyTag,
            currentEvent: EncryptionKeyListEvent,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EncryptionKeyListEvent>.() -> Unit = {},
        ) = eventUpdate(currentEvent, createdAt) {
            alt(ALT)
            key(key)
            initializer()
        }

        fun remove(
            key: KeyTag,
            currentEvent: EncryptionKeyListEvent,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EncryptionKeyListEvent>.() -> Unit = {},
        ) = eventUpdate(currentEvent, createdAt) {
            alt(ALT)
            removeIf(KeyTag::isSameKey, key.toTagArray())
            initializer()
        }

        fun build(
            keys: List<KeyTag>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EncryptionKeyListEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT)
            keys(keys)
            initializer()
        }
    }
}
