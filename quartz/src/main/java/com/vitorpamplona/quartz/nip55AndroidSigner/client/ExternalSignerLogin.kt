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
package com.vitorpamplona.quartz.nip55AndroidSigner.client

import android.content.Intent
import com.vitorpamplona.quartz.nip55AndroidSigner.api.PubKeyResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.LoginRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.responses.LoginResponse
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission

object ExternalSignerLogin {
    fun createIntent(permissions: List<Permission> = LoginRequest.DefaultPermissions): Intent {
        val intent = LoginRequest.assemble(permissions)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return intent
    }

    fun parseResult(data: Intent): SignerResult<PubKeyResult> {
        val result = IntentResult.fromIntent(data)
        return LoginResponse.parse(result)
    }
}
