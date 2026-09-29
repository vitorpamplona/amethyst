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
package com.vitorpamplona.quartz.nipXXPushNotifications.preferences

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nipXXPushNotifications.PushServiceEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.app
import com.vitorpamplona.quartz.nipXXPushNotifications.pushService
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Which notifications a user wants from a push service (kind 3083). Optional: a service that never
 * receives one sends everything it supports.
 */
@Immutable
class PushPreferencesEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PushServiceEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    suspend fun decrypt(signer: NostrSigner): PushPreferences = json.decodeFromString(PushPreferences.serializer(), decryptContent(signer))

    companion object {
        const val KIND = 3083

        suspend fun build(
            preferences: PushPreferences,
            pushService: HexKey,
            app: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PushPreferencesEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, signer.nip44Encrypt(json.encodeToString(PushPreferences.serializer(), preferences), pushService), createdAt) {
            pushService(pushService)
            app(app)
            initializer()
        }
    }
}
