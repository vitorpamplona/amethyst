/**
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
package com.vitorpamplona.quartz.nip55AndroidSigner.api.background.queries

import android.content.ContentResolver
import androidx.core.net.toUri
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.utils.getStringByName
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.utils.query
import com.vitorpamplona.quartz.utils.EventFactory

class SignQuery(
    val loggedInUser: HexKey,
    val packageName: String,
    val contentResolver: ContentResolver,
) {
    val uri = "content://$packageName.${CommandType.SIGN_EVENT}".toUri()

    fun query(unsignedEvent: Event): SignerResult<SignResult> =
        contentResolver.query(
            uri,
            arrayOf(unsignedEvent.toJson(), unsignedEvent.pubKey, loggedInUser),
        ) { cursor ->
            val eventJson = cursor.getStringByName("event")
            if (!eventJson.isNullOrBlank()) {
                if (eventJson.startsWith("{")) {
                    val event = Event.fromJsonOrNull(eventJson)
                    if (event != null) {
                        if (event.verify()) {
                            SignerResult.RequestAddressed.Successful(SignResult(event))
                        } else {
                            SignerResult.RequestAddressed.ReceivedButCouldNotVerifyResultingEvent(event)
                        }
                    } else {
                        SignerResult.RequestAddressed.ReceivedButCouldNotParseEventFromResult(eventJson)
                    }
                } else {
                    SignerResult.RequestAddressed.ReceivedButCouldNotParseEventFromResult(eventJson)
                }
            } else {
                val signature = cursor.getStringByName("result")
                if (!signature.isNullOrBlank()) {
                    val event: Event =
                        EventFactory.create(
                            id = unsignedEvent.id,
                            pubKey = unsignedEvent.pubKey,
                            createdAt = unsignedEvent.createdAt,
                            kind = unsignedEvent.kind,
                            tags = unsignedEvent.tags,
                            content = unsignedEvent.content,
                            sig = signature,
                        )
                    if (event.verify()) {
                        SignerResult.RequestAddressed.Successful(SignResult(event))
                    } else {
                        SignerResult.RequestAddressed.ReceivedButCouldNotVerifyResultingEvent(event)
                    }
                } else {
                    SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
                }
            }
        }
}
