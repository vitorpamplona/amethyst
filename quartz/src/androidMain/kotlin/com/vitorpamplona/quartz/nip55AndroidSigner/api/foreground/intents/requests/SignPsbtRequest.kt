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
package com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests

import android.content.Intent
import androidx.core.net.toUri
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType

/**
 * NIP-BC `sign_psbt` foreground Intent request.
 *
 * Carries the PSBT (lowercase hex) as the `nostrsigner:` URI data so the
 * signer app can display the inputs/outputs to the user for confirmation.
 */
class SignPsbtRequest {
    companion object {
        fun assemble(
            psbtHex: String,
            loggedInUser: HexKey,
            packageName: String,
        ): Intent {
            val intent = Intent(Intent.ACTION_VIEW, "nostrsigner:$psbtHex".toUri())
            intent.`package` = packageName
            intent.putExtra("type", CommandType.SIGN_PSBT.code)
            intent.putExtra("current_user", loggedInUser)
            return intent
        }
    }
}
