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
package com.vitorpamplona.amethyst.ui.note

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.SaveButton
import com.vitorpamplona.amethyst.ui.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TitleExplainer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.getFragmentActivity
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.encoders.Nip47WalletConnect
import com.vitorpamplona.quartz.encoders.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.encoders.decodePublicKey
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.LnZapEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException

class UpdateZapAmountViewModel(val account: Account) : ViewModel() {
    var nextAmount by mutableStateOf(TextFieldValue(""))
    var amountSet by mutableStateOf(listOf<Long>())
    var walletConnectRelay by mutableStateOf(TextFieldValue(""))
    var walletConnectPubkey by mutableStateOf(TextFieldValue(""))
    var walletConnectSecret by mutableStateOf(TextFieldValue(""))
    var selectedZapType by mutableStateOf(LnZapEvent.ZapType.PRIVATE)

    fun load() {
        this.amountSet = account.zapAmountChoices
        this.walletConnectPubkey =
            account.zapPaymentRequest?.pubKeyHex?.let { TextFieldValue(it) } ?: TextFieldValue("")
        this.walletConnectRelay =
            account.zapPaymentRequest?.relayUri?.let { TextFieldValue(it) } ?: TextFieldValue("")
        this.walletConnectSecret =
            account.zapPaymentRequest?.secret?.let { TextFieldValue(it) } ?: TextFieldValue("")
        this.selectedZapType = account.defaultZapType
    }

    fun toListOfAmounts(commaSeparatedAmounts: String): List<Long> {
        return commaSeparatedAmounts.split(",").map { it.trim().toLongOrNull() ?: 0 }
    }

    fun addAmount() {
        val newValue = nextAmount.text.trim().toLongOrNull()
        if (newValue != null) {
            amountSet = amountSet + newValue
        }

        nextAmount = TextFieldValue("")
    }

    fun removeAmount(amount: Long) {
        amountSet = amountSet - amount
    }

    fun sendPost() {
        account?.changeZapAmounts(amountSet)
        account?.changeDefaultZapType(selectedZapType)

        if (walletConnectRelay.text.isNotBlank() && walletConnectPubkey.text.isNotBlank()) {
            val pubkeyHex =
                try {
                    decodePublicKey(walletConnectPubkey.text.trim()).toHexKey()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }

            val relayUrl =
                walletConnectRelay.text
                    .ifBlank { null }
                    ?.let {
                        var addedWSS =
                            if (!it.startsWith("wss://") && !it.startsWith("ws://")) "wss://$it" else it
                        if (addedWSS.endsWith("/")) addedWSS = addedWSS.dropLast(1)

                        addedWSS
                    }

            val privKeyHex = walletConnectSecret.text.ifBlank { null }?.let { decodePrivateKeyAsHexOrNull(it) }

            if (pubkeyHex != null) {
                account?.changeZapPaymentRequest(
                    Nip47WalletConnect.Nip47URI(
                        pubkeyHex,
                        relayUrl,
                        privKeyHex,
                    ),
                )
            } else {
                account?.changeZapPaymentRequest(null)
            }
        } else {
            account?.changeZapPaymentRequest(null)
        }

        nextAmount = TextFieldValue("")
    }

    fun cancel() {
        nextAmount = TextFieldValue("")
    }

    fun hasChanged(): Boolean {
        return (
            selectedZapType != account?.defaultZapType ||
                amountSet != account?.zapAmountChoices ||
                walletConnectPubkey.text != (account?.zapPaymentRequest?.pubKeyHex ?: "") ||
                walletConnectRelay.text != (account?.zapPaymentRequest?.relayUri ?: "") ||
                walletConnectSecret.text != (account?.zapPaymentRequest?.secret ?: "")
        )
    }

    fun updateNIP47(uri: String) {
        val contact = Nip47WalletConnect.parse(uri)
        if (contact != null) {
            walletConnectPubkey = TextFieldValue(contact.pubKeyHex)
            walletConnectRelay = TextFieldValue(contact.relayUri ?: "")
            walletConnectSecret = TextFieldValue(contact.secret ?: "")
        }
    }

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <UpdateZapAmountViewModel : ViewModel> create(modelClass: Class<UpdateZapAmountViewModel>): UpdateZapAmountViewModel {
            return UpdateZapAmountViewModel(account) as UpdateZapAmountViewModel
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateZapAmountDialog(
    onClose: () -> Unit,
    nip47uri: String? = null,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val postViewModel: UpdateZapAmountViewModel =
        viewModel(
            key = "UpdateZapAmountViewModel",
            factory = UpdateZapAmountViewModel.Factory(accountViewModel.account),
        )

    val uri = LocalUriHandler.current

    val zapTypes =
        listOf(
            Triple(
                LnZapEvent.ZapType.PUBLIC,
                stringResource(id = R.string.zap_type_public),
                stringResource(id = R.string.zap_type_public_explainer),
            ),
            Triple(
                LnZapEvent.ZapType.PRIVATE,
                stringResource(id = R.string.zap_type_private),
                stringResource(id = R.string.zap_type_private_explainer),
            ),
            Triple(
                LnZapEvent.ZapType.ANONYMOUS,
                stringResource(id = R.string.zap_type_anonymous),
                stringResource(id = R.string.zap_type_anonymous_explainer),
            ),
            Triple(
                LnZapEvent.ZapType.NONZAP,
                stringResource(id = R.string.zap_type_nonzap),
                stringResource(id = R.string.zap_type_nonzap_explainer),
            ),
        )

    val zapOptions =
        remember {
            zapTypes.map { TitleExplainer(it.second, it.third) }.toImmutableList()
        }

    LaunchedEffect(accountViewModel, nip47uri) {
        postViewModel.load()
        if (nip47uri != null) {
            try {
                postViewModel.updateNIP47(nip47uri)
            } catch (e: IllegalArgumentException) {
                if (e.message != null) {
                    accountViewModel.toast(
                        context.getString(R.string.error_parsing_nip47_title),
                        context.getString(R.string.error_parsing_nip47, nip47uri, e.message!!),
                    )
                } else {
                    accountViewModel.toast(
                        context.getString(R.string.error_parsing_nip47_title),
                        context.getString(R.string.error_parsing_nip47_no_error, nip47uri),
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(10.dp).imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(
                        onPress = {
                            postViewModel.cancel()
                            onClose()
                        },
                    )

                    SaveButton(
                        onPost = {
                            postViewModel.sendPost()
                            onClose()
                        },
                        isActive = postViewModel.hasChanged(),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.animateContentSize()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    postViewModel.amountSet.forEach { amountInSats ->
                                        Button(
                                            modifier = Modifier.padding(horizontal = 3.dp),
                                            shape = ButtonBorder,
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                ),
                                            onClick = { postViewModel.removeAmount(amountInSats) },
                                        ) {
                                            Text(
                                                "⚡ ${
                                                    showAmount(
                                                        amountInSats.toBigDecimal().setScale(1),
                                                    )
                                                } ✖",
                                                color = Color.White,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                label = { Text(text = stringResource(R.string.new_amount_in_sats)) },
                                value = postViewModel.nextAmount,
                                onValueChange = { postViewModel.nextAmount = it },
                                keyboardOptions =
                                    KeyboardOptions.Default.copy(
                                        capitalization = KeyboardCapitalization.None,
                                        keyboardType = KeyboardType.Number,
                                    ),
                                placeholder = {
                                    Text(
                                        text = "100, 1000, 5000",
                                        color = MaterialTheme.colorScheme.placeholderText,
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.padding(end = 10.dp).weight(1f),
                            )

                            Button(
                                onClick = { postViewModel.addAmount() },
                                shape = ButtonBorder,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                            ) {
                                Text(text = stringResource(R.string.add), color = Color.White)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextSpinner(
                                label = stringResource(id = R.string.zap_type_explainer),
                                placeholder =
                                    zapTypes.filter { it.first == accountViewModel.defaultZapType() }.first().second,
                                options = zapOptions,
                                onSelect = { postViewModel.selectedZapType = zapTypes[it].first },
                                modifier = Modifier.weight(1f).padding(end = 5.dp),
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            thickness = DividerThickness,
                        )

                        var qrScanning by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(id = R.string.wallet_connect_service),
                                Modifier.weight(1f),
                            )

                            IconButton(onClick = {
                                onClose()
                                runCatching { uri.openUri("https://app.mutinywallet.com/settings/connections?name=Amethyst") }
                            }) {
                                Icon(
                                    painter = painterResource(R.mipmap.mutiny),
                                    null,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified,
                                )
                            }

                            IconButton(
                                onClick = {
                                    onClose()
                                    runCatching { uri.openUri("https://nwc.getalby.com/apps/new?c=Amethyst") }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.alby),
                                    contentDescription = stringResource(id = R.string.accessibility_navigate_to_alby),
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified,
                                )
                            }

                            IconButton(onClick = { qrScanning = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_qrcode),
                                    contentDescription = stringResource(id = R.string.accessibility_scan_qr_code),
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(id = R.string.wallet_connect_service_explainer),
                                Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.placeholderText,
                                fontSize = Font14SP,
                            )
                        }

                        if (qrScanning) {
                            SimpleQrCodeScanner {
                                qrScanning = false
                                if (!it.isNullOrEmpty()) {
                                    try {
                                        postViewModel.updateNIP47(it)
                                    } catch (e: IllegalArgumentException) {
                                        if (e.message != null) {
                                            accountViewModel.toast(
                                                context.getString(R.string.error_parsing_nip47_title),
                                                context.getString(R.string.error_parsing_nip47, it, e.message!!),
                                            )
                                        } else {
                                            accountViewModel.toast(
                                                context.getString(R.string.error_parsing_nip47_title),
                                                context.getString(R.string.error_parsing_nip47_no_error, it),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                label = { Text(text = stringResource(R.string.wallet_connect_service_pubkey)) },
                                value = postViewModel.walletConnectPubkey,
                                onValueChange = { postViewModel.walletConnectPubkey = it },
                                keyboardOptions =
                                    KeyboardOptions.Default.copy(
                                        capitalization = KeyboardCapitalization.None,
                                    ),
                                placeholder = {
                                    Text(
                                        text = "npub, hex",
                                        color = MaterialTheme.colorScheme.placeholderText,
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                label = { Text(text = stringResource(R.string.wallet_connect_service_relay)) },
                                modifier = Modifier.weight(1f),
                                value = postViewModel.walletConnectRelay,
                                onValueChange = { postViewModel.walletConnectRelay = it },
                                placeholder = {
                                    Text(
                                        text = "wss://relay.server.com",
                                        color = MaterialTheme.colorScheme.placeholderText,
                                        maxLines = 1,
                                    )
                                },
                                singleLine = true,
                            )
                        }

                        var showPassword by remember { mutableStateOf(false) }

                        val context = LocalContext.current

                        val keyguardLauncher =
                            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                                    result: ActivityResult ->
                                if (result.resultCode == Activity.RESULT_OK) {
                                    showPassword = true
                                }
                            }

                        val authTitle = stringResource(id = R.string.wallet_connect_service_show_secret)

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                label = { Text(text = stringResource(R.string.wallet_connect_service_secret)) },
                                modifier = Modifier.weight(1f),
                                value = postViewModel.walletConnectSecret,
                                onValueChange = { postViewModel.walletConnectSecret = it },
                                keyboardOptions =
                                    KeyboardOptions(
                                        autoCorrect = false,
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Go,
                                    ),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.wallet_connect_service_secret_placeholder),
                                        color = MaterialTheme.colorScheme.placeholderText,
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (!showPassword) {
                                                authenticate(
                                                    title = authTitle,
                                                    context = context,
                                                    keyguardLauncher = keyguardLauncher,
                                                    onApproved = { showPassword = true },
                                                    onError = { title, message -> accountViewModel.toast(title, message) },
                                                )
                                            } else {
                                                showPassword = false
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector =
                                                if (showPassword) {
                                                    Icons.Outlined.VisibilityOff
                                                } else {
                                                    Icons.Outlined.Visibility
                                                },
                                            contentDescription =
                                                if (showPassword) {
                                                    stringResource(R.string.show_password)
                                                } else {
                                                    stringResource(
                                                        R.string.hide_password,
                                                    )
                                                },
                                        )
                                    }
                                },
                                visualTransformation =
                                    if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun authenticate(
    title: String,
    context: Context,
    keyguardLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    onApproved: () -> Unit,
    onError: (String, String) -> Unit,
) {
    val fragmentContext = context.getFragmentActivity()!!
    val keyguardManager =
        fragmentContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    if (!keyguardManager.isDeviceSecure) {
        onApproved()
        return
    }

    @Suppress("DEPRECATION")
    fun keyguardPrompt() {
        val intent =
            keyguardManager.createConfirmDeviceCredentialIntent(
                context.getString(R.string.app_name_release),
                title,
            )

        keyguardLauncher.launch(intent)
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        keyguardPrompt()
        return
    }

    val biometricManager = BiometricManager.from(context)
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    val promptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.app_name_release))
            .setSubtitle(title)
            .setAllowedAuthenticators(authenticators)
            .build()

    val biometricPrompt =
        BiometricPrompt(
            fragmentContext,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(errorCode, errString)

                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> keyguardPrompt()
                        BiometricPrompt.ERROR_LOCKOUT -> keyguardPrompt()
                        else ->
                            onError(
                                context.getString(R.string.biometric_authentication_failed),
                                context.getString(
                                    R.string.biometric_authentication_failed_explainer_with_error,
                                    errString,
                                ),
                            )
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError(
                        context.getString(R.string.biometric_authentication_failed),
                        context.getString(R.string.biometric_authentication_failed_explainer),
                    )
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onApproved()
                }
            },
        )

    when (biometricManager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> biometricPrompt.authenticate(promptInfo)
        else -> keyguardPrompt()
    }
}
