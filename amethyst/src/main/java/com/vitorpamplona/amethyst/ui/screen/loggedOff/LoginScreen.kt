/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedOff

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.Amethyst
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.service.PackageUtils
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.navigation.getActivity
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Size0dp
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.amethyst.ui.theme.Size50dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.tor.TorSettings
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip55AndroidSigner.ExternalSignerLauncher
import com.vitorpamplona.quartz.nip55AndroidSigner.SignerType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun LoginPage() {
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
    val key = remember { mutableStateOf(TextFieldValue(newAccountKey ?: "")) }
    var errorMessage by remember { mutableStateOf("") }
    val acceptedTerms = remember { mutableStateOf(!isFirstLogin) }
    var termsAcceptanceIsRequired by remember { mutableStateOf("") }

    val context = LocalContext.current
    val torSettings = remember { mutableStateOf(TorSettings()) }
    val isNFCOrQR = remember { mutableStateOf(false) }
    val isTemporary = remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var loginWithExternalSigner by remember { mutableStateOf(false) }

    var processingLogin by remember { mutableStateOf(false) }

    val password = remember { mutableStateOf(TextFieldValue("")) }
    val needsPassword =
        remember {
            derivedStateOf {
                key.value.text.startsWith("ncryptsec1")
            }
        }

    val passwordFocusRequester = remember { FocusRequester() }

    if (loginWithExternalSigner) {
        PrepareExternalSignerReceiver { pubkey, packageName ->
            key.value = TextFieldValue(pubkey)
            if (!acceptedTerms.value) {
                termsAcceptanceIsRequired = stringRes(context, R.string.acceptance_of_terms_is_required)
            }

            if (key.value.text.isBlank()) {
                errorMessage = stringRes(context, R.string.key_is_required)
            }

            if (acceptedTerms.value && key.value.text.isNotBlank()) {
                accountStateViewModel.login(
                    key = key.value.text,
                    torSettings = torSettings.value,
                    transientAccount = isTemporary.value,
                    loginWithExternalSigner = true,
                    packageName = packageName,
                ) {
                    errorMessage = stringRes(context, R.string.invalid_key)
                }
            }
        }
    }

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
            value = key.value,
            onValueChange = { value, isQr ->
                key.value = value
                if (isQr) {
                    isNFCOrQR.value = true
                    isTemporary.value = true
                }
                if (errorMessage.isNotEmpty()) {
                    errorMessage = ""
                }
            },
        ) {
            if (!acceptedTerms.value) {
                termsAcceptanceIsRequired = stringRes(context, R.string.acceptance_of_terms_is_required)
            }

            if (key.value.text.isBlank()) {
                errorMessage = stringRes(context, R.string.key_is_required)
            }

            if (needsPassword.value && password.value.text.isBlank()) {
                errorMessage = stringRes(context, R.string.password_is_required)
            }

            if (acceptedTerms.value && key.value.text.isNotBlank() && !(needsPassword.value && password.value.text.isBlank())) {
                processingLogin = true
                accountStateViewModel.login(
                    key = key.value.text,
                    password = password.value.text,
                    torSettings = torSettings.value,
                    transientAccount = isTemporary.value,
                ) {
                    processingLogin = false
                    errorMessage =
                        if (it != null) {
                            stringRes(context, R.string.invalid_key_with_message, it)
                        } else {
                            stringRes(context, R.string.invalid_key)
                        }
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (needsPassword.value) {
            PasswordField(
                value = password.value,
                onValueChange = {
                    password.value = it
                    if (errorMessage.isNotEmpty()) {
                        errorMessage = ""
                    }
                },
                passwordFocusRequester,
            ) {
                if (!acceptedTerms.value) {
                    termsAcceptanceIsRequired = stringRes(context, R.string.acceptance_of_terms_is_required)
                }

                if (key.value.text.isBlank()) {
                    errorMessage = stringRes(context, R.string.key_is_required)
                }

                if (needsPassword.value && password.value.text.isBlank()) {
                    errorMessage = stringRes(context, R.string.password_is_required)
                }

                if (acceptedTerms.value && key.value.text.isNotBlank() && !(needsPassword.value && password.value.text.isBlank())) {
                    processingLogin = true
                    accountStateViewModel.login(key.value.text, password.value.text, torSettings.value, isTemporary.value) {
                        processingLogin = false
                        errorMessage =
                            if (it != null) {
                                stringRes(context, R.string.invalid_key_with_message, it)
                            } else {
                                stringRes(context, R.string.invalid_key)
                            }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        TorSettingsSetup(
            torSettings = torSettings.value,
            onCheckedChange = {
                torSettings.value = it
            },
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

        if (isNFCOrQR.value) {
            OfferTemporaryAccount(
                checked = isTemporary.value,
                onCheckedChange = { isTemporary.value = it },
            )
        }

        if (isFirstLogin) {
            AcceptTerms(
                checked = acceptedTerms.value,
                onCheckedChange = { acceptedTerms.value = it },
            )

            if (termsAcceptanceIsRequired.isNotBlank()) {
                Text(
                    text = termsAcceptanceIsRequired,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(Size10dp))

        Box(modifier = Modifier.padding(Size40dp, 0.dp, Size40dp, 0.dp)) {
            LoginButton(
                enabled = acceptedTerms.value,
                processingLogin = processingLogin,
                onClick = {
                    if (!acceptedTerms.value) {
                        termsAcceptanceIsRequired =
                            stringRes(context, R.string.acceptance_of_terms_is_required)
                    }

                    if (key.value.text.isBlank()) {
                        errorMessage = stringRes(context, R.string.key_is_required)
                    }

                    if (needsPassword.value && password.value.text.isBlank()) {
                        errorMessage = stringRes(context, R.string.password_is_required)
                    }

                    if (acceptedTerms.value && key.value.text.isNotBlank() && !(needsPassword.value && password.value.text.isBlank())) {
                        processingLogin = true
                        accountStateViewModel.login(key.value.text, password.value.text, torSettings.value, isTemporary.value) {
                            processingLogin = false
                            errorMessage =
                                if (it != null) {
                                    stringRes(context, R.string.invalid_key_with_message, it)
                                } else {
                                    stringRes(context, R.string.invalid_key)
                                }
                        }
                    }
                },
            )
        }

        if (PackageUtils.isExternalSignerInstalled(context)) {
            Box(modifier = Modifier.padding(Size40dp, Size20dp, Size40dp, Size0dp)) {
                LoginWithAmberButton(
                    enabled = acceptedTerms.value,
                    onClick = {
                        if (!acceptedTerms.value) {
                            termsAcceptanceIsRequired = stringRes(context, R.string.acceptance_of_terms_is_required)
                        } else {
                            loginWithExternalSigner = true
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(Size40dp))

        Text(text = stringRes(R.string.don_t_have_an_account))

        Spacer(modifier = Modifier.height(Size20dp))

        Box(modifier = Modifier.padding(Size40dp, 0.dp, Size40dp, 0.dp)) {
            SignUpButton(onWantsToLogin)
        }
    }

    OpenURIIfNotLoggedIn {
        key.value = TextFieldValue(it)
        acceptedTerms.value = true
        isNFCOrQR.value = true
        isTemporary.value = true
        if (it.startsWith("ncryptsec1")) {
            delay(300)
            passwordFocusRequester.requestFocus()
        }
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KeyTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue, throughQR: Boolean) -> Unit,
    onLogin: () -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    var showCharsKey by remember { mutableStateOf(false) }

    val autofillNodeKey =
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { onValueChange(TextFieldValue(it), false) },
        )

    val autofillNodePassword =
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { onValueChange(TextFieldValue(it), false) },
        )

    val autofill = LocalAutofill.current
    LocalAutofillTree.current += autofillNodeKey
    LocalAutofillTree.current += autofillNodePassword

    OutlinedTextField(
        modifier =
            Modifier
                .onGloballyPositioned { coordinates ->
                    autofillNodeKey.boundingBox = coordinates.boundsInWindow()
                }.onFocusChanged { focusState ->
                    autofill?.run {
                        if (focusState.isFocused) {
                            requestAutofillForNode(autofillNodeKey)
                        } else {
                            cancelAutofillForNode(autofillNodeKey)
                        }
                    }
                },
        value = value,
        onValueChange = { onValueChange(it, false) },
        keyboardOptions =
            KeyboardOptions(
                autoCorrect = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
        placeholder = {
            Text(
                text = stringRes(R.string.nsec_npub_hex_private_key),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        trailingIcon = {
            Row {
                IconButton(onClick = { showCharsKey = !showCharsKey }) {
                    Icon(
                        imageVector =
                            if (showCharsKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription =
                            if (showCharsKey) {
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
        leadingIcon = {
            if (dialogOpen) {
                SimpleQrCodeScanner {
                    dialogOpen = false
                    if (!it.isNullOrEmpty()) {
                        onValueChange(TextFieldValue(it), true)
                    }
                }
            }
            IconButton(onClick = { dialogOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_qrcode),
                    contentDescription =
                        stringRes(
                            R.string.login_with_qr_code,
                        ),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        visualTransformation =
            if (showCharsKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardActions =
            KeyboardActions(
                onGo = {
                    onLogin()
                },
            ),
    )
}

@Composable
private fun PrepareExternalSignerReceiver(onLogin: (pubkey: String, packageName: String) -> Unit) {
    val scope = rememberCoroutineScope()
    val externalSignerLauncher = remember { ExternalSignerLauncher("", signerPackageName = "") }
    val id = remember { UUID.randomUUID().toString() }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = { result ->
                if (result.resultCode != Activity.RESULT_OK) {
                    scope.launch(Dispatchers.Main) {
                        Toast
                            .makeText(
                                Amethyst.instance,
                                "Sign request rejected",
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                } else {
                    result.data?.let { externalSignerLauncher.newResult(it) }
                }
            },
        )

    val activity = getActivity() as MainActivity

    DisposableEffect(launcher, activity, externalSignerLauncher) {
        externalSignerLauncher.registerLauncher(
            launcher = {
                try {
                    activity.prepareToLaunchSigner()
                    launcher.launch(it)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("Signer", "Error opening Signer app", e)
                    scope.launch(Dispatchers.Main) {
                        Toast
                            .makeText(
                                Amethyst.instance,
                                R.string.error_opening_external_signer,
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            },
            contentResolver = Amethyst.instance::contentResolverFn,
        )
        onDispose { externalSignerLauncher.clearLauncher() }
    }

    LaunchedEffect(externalSignerLauncher) {
        externalSignerLauncher.openSignerApp(
            "",
            SignerType.GET_PUBLIC_KEY,
            "",
            id,
        ) { result ->
            val split = result.split("-")
            val pubkey = split.first()
            val packageName = if (split.size > 1) split[1] else ""

            onLogin(pubkey, packageName)
        }
    }
}

@Composable
private fun OpenURIIfNotLoggedIn(onNewNIP19: suspend (String) -> Unit) {
    val context = LocalContext.current
    val activity = context.getActivity()
    val scope = rememberCoroutineScope()

    var currentIntentNextPage by remember {
        val uri =
            activity.intent
                ?.data
                ?.toString()
                ?.ifBlank { null }

        activity.intent.data = null

        mutableStateOf(uri)
    }

    currentIntentNextPage?.let { intentNextPage ->
        var nip19 by remember {
            mutableStateOf(
                Nip19Parser.tryParseAndClean(currentIntentNextPage),
            )
        }

        LaunchedEffect(intentNextPage) {
            if (nip19 != null) {
                nip19?.let {
                    scope.launch {
                        onNewNIP19(it)
                    }
                    nip19 = null
                }
            } else {
                scope.launch {
                    Toast
                        .makeText(
                            context,
                            stringRes(context, R.string.invalid_nip19_uri_description, intentNextPage),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }

            currentIntentNextPage = null
        }
    }

    DisposableEffect(activity) {
        val consumer =
            Consumer<Intent> { intent ->
                val uri = intent.data?.toString()
                if (!uri.isNullOrBlank()) {
                    val newNip19 = Nip19Parser.tryParseAndClean(uri)
                    if (newNip19 != null) {
                        scope.launch {
                            onNewNIP19(newNip19)
                        }
                    } else {
                        scope.launch {
                            delay(1000)
                            Toast
                                .makeText(
                                    context,
                                    stringRes(context, R.string.invalid_nip19_uri_description, uri),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    }
                }
            }
        activity.addOnNewIntentListener(consumer)
        onDispose { activity.removeOnNewIntentListener(consumer) }
    }
}

@Composable
fun SignUpButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(Size35dp),
        modifier = Modifier.height(50.dp),
    ) {
        Text(
            text = stringRes(R.string.sign_up),
            modifier = Modifier.padding(horizontal = Size40dp),
        )
    }
}

@Composable
fun LoginWithAmberButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(Size35dp),
        modifier = Modifier.height(Size50dp),
    ) {
        Text(
            text = stringRes(R.string.login_with_external_signer),
            modifier = Modifier.padding(horizontal = Size40dp),
        )
    }
}

@Composable
fun LoginButton(
    enabled: Boolean,
    processingLogin: Boolean,
    onClick: () -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(Size35dp),
        modifier = Modifier.height(Size50dp),
    ) {
        Row(modifier = Modifier.padding(horizontal = Size40dp)) {
            if (processingLogin) {
                LoadingAnimation()
                Spacer(modifier = DoubleHorzSpacer)
            }
            Text(stringRes(R.string.login))
        }
    }
}
