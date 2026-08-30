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
package com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.responses

import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignPsbtResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResult

/**
 * Parses the external signer's `sign_psbt` Intent reply. The `result` field
 * carries the updated (signed, not finalized) PSBT as lowercase hex.
 */
class SignPsbtResponse {
    companion object {
        fun assemble(signedPsbtHex: String): IntentResult =
            IntentResult(
                result = signedPsbtHex,
            )

        fun parse(intent: IntentResult): SignerResult.RequestAddressed<SignPsbtResult> {
            if (intent.rejected == true) {
                return SignerResult.RequestAddressed.ManuallyRejected()
            }
            val signedPsbtHex = intent.result
            return if (!signedPsbtHex.isNullOrBlank()) {
                SignerResult.RequestAddressed.Successful(SignPsbtResult(signedPsbtHex))
            } else {
                SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
            }
        }
    }
}
