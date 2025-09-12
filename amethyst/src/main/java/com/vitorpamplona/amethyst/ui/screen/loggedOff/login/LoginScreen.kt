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

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.Amethyst
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedOff.AcceptTerms
import com.vitorpamplona.amethyst.ui.screen.loggedOff.TorSettingsSetup
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip55AndroidSigner.client.isExternalSignerInstalled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun LoginPagePreview() {
    val accountViewModel: AccountStateViewModel = viewModel()

    ThemeComparisonRow(
        toPreview = {
            LoginPage(accountViewModel, true) {}
        },
    )
}

@Composable
fun LoginPage(
    accountStateViewModel: AccountStateViewModel,
    isFirstLogin: Boolean,
    newAccountKey: String? = null,
    onWantsToLogin: () -> Unit,
) {
    val loginViewModel: LoginViewModel = viewModel()
    loginViewModel.init(accountStateViewModel)

    LaunchedEffect(loginViewModel, isFirstLogin, newAccountKey) {
        loginViewModel.load(isFirstLogin, newAccountKey)
    }

    LoginPage(loginViewModel, onWantsToLogin)
}

@Composable
fun LoginPage(
    loginViewModel: LoginViewModel,
    onWantsToLogin: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Size20dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            imageVector = CustomHashTagIcons.Amethyst,
            contentDescription = stringRes(R.string.app_logo),
            modifier = Modifier.size(150.dp),
            contentScale = ContentScale.Inside,
        )

        Spacer(modifier = Modifier.height(Size40dp))

        KeyTextField(
            value = loginViewModel.key,
            onValueChange = loginViewModel::updateKey,
            onLogin = loginViewModel::login,
        )

        loginViewModel.errorManager.error?.let { error ->
            when (error) {
                is LoginErrorManager.SingleErrorMsg ->
                    Text(
                        text = stringRes(error.errorResId),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                is LoginErrorManager.ParamsErrorMsg ->
                    Text(
                        text = stringRes(error.errorResId, *error.params),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                else -> {}
            }
        }

        PasswordField(loginViewModel)

        Spacer(modifier = Modifier.height(10.dp))

        TorSettingsSetup(
            torSettingsFlow = Amethyst.instance.torPrefs.value,
            onError = {
                scope.launch {
                    Toast
                        .makeText(
                            context,
                            it,
                            Toast.LENGTH_LONG,
                        ).show()
                }
            },
        )

        if (loginViewModel.offerTemporaryLogin) {
            OfferTemporaryAccount(
                checked = loginViewModel.isTemporary,
                onCheckedChange = { loginViewModel.isTemporary = it },
            )
        }

        if (loginViewModel.isFirstLogin) {
            AcceptTerms(
                checked = loginViewModel.acceptedTerms,
                onCheckedChange = loginViewModel::updateAcceptedTerms,
            )

            if (loginViewModel.termsAcceptanceIsRequiredError) {
                Text(
                    text = stringRes(R.string.acceptance_of_terms_is_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(Size10dp))

        Box(modifier = Modifier.padding(Size40dp, 0.dp, Size40dp, 0.dp)) {
            LoginButton(
                enabled = loginViewModel.acceptedTerms,
                processingLogin = loginViewModel.processingLogin,
                onClick = loginViewModel::login,
            )
        }

        if (isExternalSignerInstalled(context)) {
            ExternalSignerButton(loginViewModel)
        }

        Spacer(modifier = Modifier.height(Size40dp))

        Text(text = stringRes(R.string.don_t_have_an_account))

        Spacer(modifier = Modifier.height(Size20dp))

        Box(modifier = Modifier.padding(Size40dp, 0.dp, Size40dp, 0.dp)) {
            SignUpButton(onWantsToLogin)
        }
    }

    OpenURIIfNotLoggedIn { key ->
        loginViewModel.updateKey(TextFieldValue(key), true)
        loginViewModel.updateOfferTemporaryLogin(true)
    }
}

@Composable
fun OfferTemporaryAccount(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )

        Text(stringRes(R.string.temporary_account))
    }
}

@Composable
private fun PasswordField(loginViewModel: LoginViewModel) {
    if (loginViewModel.needsPassword) {
        Spacer(modifier = Modifier.height(10.dp))

        val passwordFocusRequester = remember { FocusRequester() }

        PasswordField(
            value = loginViewModel.password,
            onValueChange = loginViewModel::updatePassword,
            passwordFocusRequester = passwordFocusRequester,
            onGo = loginViewModel::login,
        )

        LaunchedEffect(Unit) {
            delay(300)
            passwordFocusRequester.requestFocus()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PasswordField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    passwordFocusRequester: FocusRequester,
    onGo: () -> Unit,
) {
    val autofillNodeKey =
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { onValueChange(TextFieldValue(it)) },
        )

    val autofillNodePassword =
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { onValueChange(TextFieldValue(it)) },
        )

    val autofill = LocalAutofill.current
    LocalAutofillTree.current += autofillNodeKey
    LocalAutofillTree.current += autofillNodePassword

    var showCharsPassword by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier =
            Modifier
                .focusRequester(passwordFocusRequester)
                .onGloballyPositioned { coordinates ->
                    autofillNodePassword.boundingBox = coordinates.boundsInWindow()
                }.onFocusChanged { focusState ->
                    autofill?.run {
                        if (focusState.isFocused) {
                            requestAutofillForNode(autofillNodePassword)
                        } else {
                            cancelAutofillForNode(autofillNodePassword)
                        }
                    }
                },
        value = value,
        onValueChange = onValueChange,
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
        placeholder = {
            Text(
                text = stringRes(R.string.ncryptsec_password),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        trailingIcon = {
            Row {
                IconButton(onClick = { showCharsPassword = !showCharsPassword }) {
                    Icon(
                        imageVector =
                            if (showCharsPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription =
                            if (showCharsPassword) {
                                stringRes(R.string.show_password)
                            } else {
                                stringRes(
                                    R.string.hide_password,
                                )
                            },
                    )
                }
            }
        },
        visualTransformation =
            if (showCharsPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardActions =
            KeyboardActions(
                onGo = {
                    onGo()
                },
            ),
    )
}
