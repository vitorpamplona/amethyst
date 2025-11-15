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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.ZapEventDecryptionResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResult
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent

class DecryptZapResponse {
    companion object {
        fun assemble(event: LnZapPrivateEvent): IntentResult =
            IntentResult(
                result = event.toJson(),
            )

        fun parse(intent: IntentResult): SignerResult.RequestAddressed<ZapEventDecryptionResult> {
            if (intent.rejected == true) {
                return SignerResult.RequestAddressed.ManuallyRejected()
            }
            val eventJson = intent.result
            return if (!eventJson.isNullOrBlank()) {
                if (eventJson.startsWith("{")) {
                    val event = Event.fromJsonOrNull(eventJson) as? LnZapPrivateEvent
                    if (event != null) {
                        SignerResult.RequestAddressed.Successful(ZapEventDecryptionResult(event))
                    } else {
                        SignerResult.RequestAddressed.ReceivedButCouldNotParseEventFromResult(eventJson)
                    }
                } else {
                    SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
                }
            } else {
                SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
            }
        }
    }
}
