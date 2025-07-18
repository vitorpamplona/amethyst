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
package com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.responses

import android.content.Intent
import com.vitorpamplona.quartz.EventFactory
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.verify
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult

class SignResponse {
    companion object {
        fun parse(
            intent: Intent,
            unsignedEvent: Event,
        ): SignerResult.RequestAddressed<SignResult> {
            val eventJson = intent.getStringExtra("event")
            return if (eventJson != null) {
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
                    SignerResult.RequestAddressed.ReceivedButCouldNotPerform(eventJson)
                }
            } else {
                val signature = intent.getStringExtra("result")
                if (signature != null && signature.length == 128) {
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
                    SignerResult.RequestAddressed.ReceivedButCouldNotPerform(signature)
                }
            }
        }
    }
}
