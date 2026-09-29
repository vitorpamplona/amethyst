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
package com.vitorpamplona.quartz.nipXXPushNotifications.registration

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nipXXPushNotifications.PushServiceEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.app
import com.vitorpamplona.quartz.nipXXPushNotifications.pushService
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Registers a device's push token with a push service (kind 3079).
 *
 * Clients publish it when the push session becomes ready and again whenever the platform rotates
 * the token. An `expiration` tag is allowed for relay housekeeping, but the reference service does
 * not use it to decide how long the token stays valid.
 */
@Immutable
class PushRegistrationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PushServiceEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    suspend fun decrypt(signer: NostrSigner): PushToken = json.decodeFromString(PushToken.serializer(), decryptContent(signer))

    companion object {
        const val KIND = 3079

        suspend fun build(
            token: PushToken,
            pushService: HexKey,
            app: String,
            signer: NostrSigner,
            expiresAt: Long? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PushRegistrationEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, signer.nip44Encrypt(json.encodeToString(PushToken.serializer(), token), pushService), createdAt) {
            pushService(pushService)
            app(app)
            expiresAt?.let { expiration(it) }
            initializer()
        }
    }
}
