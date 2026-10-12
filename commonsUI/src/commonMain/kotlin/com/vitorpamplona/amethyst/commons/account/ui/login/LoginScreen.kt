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
package com.vitorpamplona.amethyst.commons.account.ui.login

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.ui.LoggedOffScaffold
import com.vitorpamplona.amethyst.commons.account.ui.LoggedOffTitle
import com.vitorpamplona.amethyst.commons.account.ui.OrDivider
import com.vitorpamplona.amethyst.commons.account.ui.SwitchScreenFooter
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.don_t_have_an_account
import com.vitorpamplona.amethyst.commons.resources.hide_password
import com.vitorpamplona.amethyst.commons.resources.login
import com.vitorpamplona.amethyst.commons.resources.login_welcome_back
import com.vitorpamplona.amethyst.commons.resources.ncryptsec_password
import com.vitorpamplona.amethyst.commons.resources.show_password
import com.vitorpamplona.amethyst.commons.resources.sign_up
import com.vitorpamplona.amethyst.commons.resources.temporary_account
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.keystorage.KeyStorageLoginOptions
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import kotlinx.coroutines.delay

/** The fields and buttons of the logged-off screens: rounded, full width. */
val LoggedOffShape = RoundedCornerShape(16.dp)

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun LoginPagePreview() {
    val loginViewModel: LoginViewModel = rememberViewModel { LoginViewModel() }
    loginViewModel.init(TorSettingsFlow())

    ThemeComparisonRow(
        toPreview = {
            LoginPage(loginViewModel) {}
        },
    )
}

@Composable
fun LoginPage(
    accountSessionManager: AccountSessionManager,
    isFirstLogin: Boolean,
    newAccountKey: String? = null,
    onWantsToLogin: () -> Unit,
) {
    val requiresTerms = LocalAppPlatform.current.requiresTermsAcceptance
    val loginViewModel: LoginViewModel = rememberViewModel { LoginViewModel(requiresTerms) }
    loginViewModel.init(accountSessionManager)
    loginViewModel.init(LocalAppServices.current.torSettings)

    LaunchedEffect(loginViewModel, isFirstLogin, newAccountKey) {
        loginViewModel.load(isFirstLogin, newAccountKey)
    }

    LoginPage(loginViewModel, onWantsToLogin)
}

@Composable
fun LoginPage(
    loginViewModel: LoginViewModel,
    onWantsToSignUp: () -> Unit,
) {
    val platform = LocalAppPlatform.current
    val toaster = platform.rememberToaster()
    loginViewModel.remoteSignerLoginSupported = platform.supportsRemoteSignerLogin

    LoggedOffScaffold(
        torSettingsFlow = loginViewModel.torSettings,
        onTorError = { toaster.show(it, long = true) },
        footer = {
            SwitchScreenFooter(
                question = stringRes(Res.string.don_t_have_an_account),
                action = stringRes(Res.string.sign_up),
                onClick = onWantsToSignUp,
            )
        },
    ) {
        LoggedOffTitle(stringRes(Res.string.login_welcome_back))

        KeyTextField(
            value = loginViewModel.key,
            onValueChange = loginViewModel::updateKey,
            onLogin = loginViewModel::login,
            acceptsBunker = platform.supportsRemoteSignerLogin,
        )

        LoginErrorText(loginViewModel.errorManager)

        PasswordField(loginViewModel)

        if (loginViewModel.offerTemporaryLogin) {
            OfferTemporaryAccount(
                checked = loginViewModel.isTemporary,
                onCheckedChange = { loginViewModel.isTemporary = it },
            )
        }

        // A temporary login keeps nothing, so whether to keep the key only matters otherwise.
        if (!loginViewModel.isTemporary) {
            KeyStorageLoginOptions(
                rememberKey = loginViewModel.rememberKey,
                onRememberKeyChange = { loginViewModel.rememberKey = it },
            )
        }

        if (loginViewModel.isFirstLogin) {
            platform.TermsGate(
                checked = loginViewModel.acceptedTerms,
                onCheckedChange = loginViewModel::updateAcceptedTerms,
                showError = loginViewModel.termsAcceptanceIsRequiredError,
            )
        }

        LoggedOffPrimaryButton(
            text = stringRes(Res.string.login),
            // Stays enabled while it works: a disabled button would grey out under its spinner.
            // login() ignores taps until the attempt ends.
            enabled = loginViewModel.acceptedTerms,
            // A remote signer's login has its own dialog; the button only spins for its own.
            loading = loginViewModel.processingLogin && !loginViewModel.nostrConnectPending,
            onClick = loginViewModel::login,
        )

        val hasExternalSigner = platform.rememberHasExternalSignerLogin()
        if (platform.supportsRemoteSignerLogin || hasExternalSigner) {
            OrDivider()
        }

        if (hasExternalSigner) {
            platform.ExternalSignerLoginButton(loginViewModel)
        }

        if (platform.supportsRemoteSignerLogin) {
            RemoteSignerLoginButton(loginViewModel)
        }
    }

    platform.IncomingLoginKeys { key ->
        loginViewModel.updateKey(TextFieldValue(key), true)
        loginViewModel.updateOfferTemporaryLogin(true)
    }
}

/** The form's error, in the error color under the field it is about. */
@Composable
fun LoginErrorText(errorManager: LoginErrorManager) {
    val text =
        when (val error = errorManager.error) {
            is LoginErrorManager.SingleErrorMsg -> stringRes(error.errorResId)
            is LoginErrorManager.ParamsErrorMsg -> stringRes(error.errorResId, *error.params)
            else -> null
        } ?: return

    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The screen's main action: a full-width filled button, with a spinner while it works. */
@Composable
fun LoggedOffPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = LoggedOffShape,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** Another way in (a signer app, a remote signer): a full-width outlined button with an icon. */
@Composable
fun LoggedOffSecondaryButton(
    text: String,
    symbol: MaterialSymbol,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = LoggedOffSecondaryButton(
    text = text,
    icon = { Icon(symbol, contentDescription = null, modifier = Modifier.size(20.dp)) },
    enabled = enabled,
    onClick = onClick,
)

@Composable
fun LoggedOffSecondaryButton(
    text: String,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = LoggedOffShape,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun OfferTemporaryAccount(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )

        Text(stringRes(Res.string.temporary_account))
    }
}

@Composable
private fun PasswordField(loginViewModel: LoginViewModel) {
    if (loginViewModel.needsPassword) {
        val passwordFocusRequester = remember { FocusRequester() }

        PasswordField(
            value = loginViewModel.password,
            onValueChange = loginViewModel::updatePassword,
            passwordFocusRequester = passwordFocusRequester,
            onGo = loginViewModel::login,
        )

        // Only once the key is complete: a pasted ncryptsec jumps straight to the password,
        // one being typed keeps its focus until the last character.
        val keyComplete = loginViewModel.isCompleteNcryptsec
        LaunchedEffect(keyComplete) {
            if (keyComplete) {
                delay(300)
                passwordFocusRequester.requestFocus()
            }
        }
    }
}

@Composable
fun PasswordField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    passwordFocusRequester: FocusRequester,
    onGo: () -> Unit,
) {
    var showCharsPassword by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .semantics { contentType = ContentType.Password },
        value = value,
        onValueChange = onValueChange,
        shape = LoggedOffShape,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
        label = { Text(stringRes(Res.string.ncryptsec_password)) },
        leadingIcon = { Icon(MaterialSymbols.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { showCharsPassword = !showCharsPassword }) {
                Icon(
                    symbol = if (showCharsPassword) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                    contentDescription =
                        if (showCharsPassword) {
                            stringRes(Res.string.hide_password)
                        } else {
                            stringRes(Res.string.show_password)
                        },
                )
            }
        },
        visualTransformation =
            if (showCharsPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
    )
}
