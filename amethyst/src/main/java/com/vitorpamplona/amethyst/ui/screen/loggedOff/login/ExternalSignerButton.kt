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
package com.vitorpamplona.amethyst.ui.screen.loggedOff.login

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.DefaultSignerPermissions
import com.vitorpamplona.amethyst.ui.theme.Size0dp
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.quartz.nip55AndroidSigner.api.PubKeyResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.client.ExternalSignerLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExternalSignerButton(loginViewModel: LoginViewModel) {
    val scope = rememberCoroutineScope()

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            scope.launch {
                val resultData = result.data
                if (result.resultCode == Activity.RESULT_OK && resultData != null) {
                    val loginInfo = ExternalSignerLogin.parseResult(resultData)
                    if (loginInfo is SignerResult.RequestAddressed.Successful<PubKeyResult>) {
                        loginViewModel.updateKey(TextFieldValue(loginInfo.result.pubkey), false)
                        loginViewModel.loginWithExternalSigner(loginInfo.result.packageName)
                    }
                } else {
                    loginViewModel.errorManager.error(R.string.sign_request_rejected2)
                }
            }
        }

    Box(modifier = Modifier.padding(Size40dp, Size20dp, Size40dp, Size0dp)) {
        LoginWithAmberButton(
            enabled = loginViewModel.acceptedTerms,
            onClick = {
                if (!loginViewModel.acceptedTerms) {
                    loginViewModel.termsAcceptanceIsRequiredError = true
                } else {
                    try {
                        launcher.launch(ExternalSignerLogin.createIntent(DefaultSignerPermissions))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("ExternalSigner", "Error opening Signer app", e)
                        loginViewModel.errorManager.error(R.string.error_opening_external_signer)
                    }
                }
            },
        )
    }
}
