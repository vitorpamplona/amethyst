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

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.tor.TorSettings

class LoginViewModel : ViewModel() {
    lateinit var accountStateViewModel: AccountStateViewModel

    val errorManager = LoginErrorManager()

    var key by mutableStateOf(TextFieldValue(""))
    var acceptedTerms by mutableStateOf(false)
    var termsAcceptanceIsRequiredError by mutableStateOf(false)

    var torSettings by mutableStateOf(TorSettings())
    var offerTemporaryLogin by mutableStateOf(false)
    var isTemporary by mutableStateOf(false)

    var processingLogin by mutableStateOf(false)

    var password by mutableStateOf(TextFieldValue(""))
    val needsPassword by derivedStateOf {
        key.text.startsWith("ncryptsec1")
    }

    var isFirstLogin by mutableStateOf(false)

    fun init(accountStateViewModel: AccountStateViewModel) {
        this.accountStateViewModel = accountStateViewModel
    }

    fun load(
        isFirstLogin: Boolean,
        newAccountKey: String?,
    ) {
        clear()
        this.isFirstLogin = isFirstLogin
        acceptedTerms = !isFirstLogin
        if (newAccountKey != null) {
            key = TextFieldValue(newAccountKey)
        }
    }

    fun clear() {
        key = TextFieldValue("")
        password = TextFieldValue("")

        errorManager.clearErrors()
        acceptedTerms = false
        processingLogin = false
        isTemporary = false
        offerTemporaryLogin = false
        torSettings = TorSettings()
        isFirstLogin = false
    }

    fun updateKey(
        value: TextFieldValue,
        throughQR: Boolean,
    ) {
        key = value
        if (throughQR) {
            offerTemporaryLogin = true
            isTemporary = true
        }
        errorManager.clearErrors()
    }

    fun updatePassword(newPassword: TextFieldValue) {
        password = newPassword
        errorManager.clearErrors()
    }

    fun updateTorSettings(newTorSettings: TorSettings) {
        torSettings = newTorSettings
    }

    fun updateAcceptedTerms(newAcceptedTerms: Boolean) {
        acceptedTerms = newAcceptedTerms
        if (newAcceptedTerms) {
            termsAcceptanceIsRequiredError = false
        }

        errorManager.clearErrors()
    }

    fun updateOfferTemporaryLogin(tempLogin: Boolean) {
        offerTemporaryLogin = tempLogin
    }

    fun checkCanLogin(): Boolean {
        if (!acceptedTerms) {
            termsAcceptanceIsRequiredError = true
            return false
        }

        if (key.text.isBlank()) {
            errorManager.error(R.string.key_is_required)
            return false
        }

        if (needsPassword && password.text.isBlank()) {
            errorManager.error(R.string.password_is_required)
            return false
        }

        return true
    }

    fun login() {
        if (checkCanLogin()) {
            processingLogin = true
            accountStateViewModel.login(
                key = key.text,
                password = password.text,
                torSettings = torSettings,
                transientAccount = isTemporary,
            ) {
                processingLogin = false
                if (it != null) {
                    errorManager.error(R.string.invalid_key_with_message, it)
                } else {
                    errorManager.error(R.string.invalid_key)
                }
            }
        }
    }

    fun loginWithExternalSigner(packageName: String) {
        if (checkCanLogin()) {
            processingLogin = true
            accountStateViewModel.login(
                key = key.text,
                torSettings = torSettings,
                transientAccount = isTemporary,
                loginWithExternalSigner = true,
                packageName = packageName,
            ) {
                processingLogin = false
                errorManager.error(R.string.sign_request_rejected_description)
            }
        }
    }
}
