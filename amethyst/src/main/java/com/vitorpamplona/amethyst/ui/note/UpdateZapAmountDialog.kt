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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.components.util.getText
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.keyBackup.getFragmentActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.ui.theme.SettingsCategorySpacingModifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Preview(device = "spec:width=1900px,height=2340px,dpi=440")
fun UpdateZapAmountContentPreview() {
    val accountViewModel = mockAccountViewModel()
    val vm: UpdateZapAmountViewModel = viewModel()
    vm.init(accountViewModel)

    ThemeComparisonRow {
        UpdateZapAmountContent(
            postViewModel = vm,
            onClose = {},
            accountViewModel = accountViewModel,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateZapAmountContent(
    postViewModel: UpdateZapAmountViewModel,
    onClose: () -> Unit,
    nip47uri: String? = null,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val zapTypes =
        listOf(
            Triple(
                LnZapEvent.ZapType.PUBLIC,
                stringRes(id = R.string.zap_type_public),
                stringRes(id = R.string.zap_type_public_explainer),
            ),
            Triple(
                LnZapEvent.ZapType.PRIVATE,
                stringRes(id = R.string.zap_type_private),
                stringRes(id = R.string.zap_type_private_explainer),
            ),
            Triple(
                LnZapEvent.ZapType.ANONYMOUS,
                stringRes(id = R.string.zap_type_anonymous),
                stringRes(id = R.string.zap_type_anonymous_explainer),
            ),
            Triple(
                LnZapEvent.ZapType.NONZAP,
                stringRes(id = R.string.zap_type_nonzap),
                stringRes(id = R.string.zap_type_nonzap_explainer),
            ),
        )

    val zapOptions =
        remember {
            zapTypes.map { TitleExplainer(it.second, it.third) }.toImmutableList()
        }

    LaunchedEffect(accountViewModel, nip47uri) {
        if (nip47uri != null) {
            try {
                postViewModel.updateNIP47(nip47uri)
            } catch (e: IllegalArgumentException) {
                if (e.message != null) {
                    accountViewModel.toastManager.toast(
                        stringRes(context, R.string.error_parsing_nip47_title),
                        stringRes(context, R.string.error_parsing_nip47, nip47uri, e.message!!),
                    )
                } else {
                    accountViewModel.toastManager.toast(
                        stringRes(context, R.string.error_parsing_nip47_title),
                        stringRes(context, R.string.error_parsing_nip47_no_error, nip47uri),
                    )
                }
            }
        }
    }

    var qrScanning by remember { mutableStateOf(false) }

    // Expand manual config automatically when a wallet connection exists
    var showManualConfig by remember { mutableStateOf(postViewModel.walletConnectPubkey.text.isNotBlank()) }
    LaunchedEffect(postViewModel.walletConnectPubkey.text) {
        if (postViewModel.walletConnectPubkey.text.isNotBlank()) {
            showManualConfig = true
        }
    }

    Column(
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState()),
    ) {
        // ── Section 1: Quick Zap Amounts ──────────────────────────────────────

        Text(
            text = stringRes(R.string.quick_zap_amounts),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            modifier = SettingsCategoryFirstModifier,
        )
        Text(
            text = stringRes(R.string.quick_zap_amounts_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // Amount chips — animateContentSize gives a smooth expand/collapse when
        // chips are added or removed
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            postViewModel.amountSet.forEach { amountInSats ->
                InputChip(
                    selected = false,
                    onClick = { postViewModel.removeAmount(amountInSats) },
                    label = {
                        Text(
                            text = "⚡ ${showAmount(amountInSats.toBigDecimal().setScale(1))}",
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringRes(R.string.remove),
                            modifier = Modifier.size(InputChipDefaults.AvatarSize),
                        )
                    },
                    colors =
                        InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            labelColor = MaterialTheme.colorScheme.primary,
                            trailingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Add new amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                label = { Text(text = stringRes(R.string.new_amount_in_sats)) },
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
                trailingIcon = {
                    IconButton(
                        onClick = postViewModel::addAmount,
                        shape = ButtonBorder,
                        enabled = postViewModel.nextAmount.text.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddCircle,
                            contentDescription = stringRes(R.string.add),
                            modifier = Size20Modifier,
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Section 2: Zap Privacy ────────────────────────────────────────────

        Text(
            text = stringRes(R.string.zap_privacy_section),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            modifier = SettingsCategorySpacingModifier,
        )
        Text(
            text = stringRes(R.string.zap_type_section_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextSpinner(
                label = stringRes(id = R.string.zap_type_explainer),
                placeholder =
                    zapTypes
                        .firstOrNull { it.first == accountViewModel.defaultZapType() }
                        ?.second
                        ?: zapTypes.firstOrNull()?.second
                        ?: "",
                options = zapOptions,
                onSelect = { postViewModel.selectedZapType = zapTypes[it].first },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Section 3: Nostr Wallet Connect ───────────────────────────────────

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            thickness = DividerThickness,
        )

        // Section header + connection status indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringRes(R.string.wallet_connect_service),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringRes(R.string.wallet_connect_service_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // Animated connection status badge
        val isConnected = postViewModel.walletConnectPubkey.text.isNotBlank()
        val statusColor by animateColorAsState(
            targetValue = if (isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.placeholderText,
            animationSpec = tween(durationMillis = 400),
            label = "nwc_status_color",
        )

        AnimatedContent(
            targetState = isConnected,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "nwc_status_badge",
        ) { connected ->
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (connected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text =
                        if (connected) {
                            stringRes(R.string.wallet_connect_status_connected)
                        } else {
                            stringRes(R.string.wallet_connect_status_not_connected)
                        },
                    color = statusColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Connect action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Primary "Connect Wallet" button — opens the NWC app deep link
            OutlinedButton(
                modifier = Modifier.weight(1f),
                shape = ButtonBorder,
                onClick = {
                    try {
                        uri.openUri(
                            "nostrnwc://connect?appname=Amethyst&appicon=https%3A%2F%2Fraw.githubusercontent.com%2Fvitorpamplona%2Famethyst%2Frefs%2Fheads%2Fmain%2Ficon.png&callback=amethyst%2Bwalletconnect%3A%2F%2Fdlnwc",
                        )
                        onClose()
                    } catch (_: IllegalArgumentException) {
                        accountViewModel.toastManager.toast(
                            R.string.couldnt_find_nwc_wallets,
                            R.string.couldnt_find_nwc_wallets_description,
                        )
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringRes(R.string.wallet_connect_connect_app))
            }

            Spacer(DoubleHorzSpacer)

            // Paste from clipboard
            IconButton(
                onClick = {
                    scope.launch {
                        val clipText = clipboardManager.getText()
                        try {
                            clipText?.let { postViewModel.copyFromClipboard(it) }
                        } catch (e: IllegalArgumentException) {
                            accountViewModel.toastManager.toast(
                                R.string.invalid_nip47_uri_title,
                                R.string.invalid_nip47_uri_description,
                                clipText ?: "",
                            )
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = stringRes(id = R.string.paste_from_clipboard),
                    modifier = Size24Modifier,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // QR code scanner
            IconButton(onClick = { qrScanning = true }) {
                Icon(
                    painter = painterRes(R.drawable.ic_qrcode, 3),
                    contentDescription = stringRes(id = R.string.accessibility_scan_qr_code),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (qrScanning) {
            SimpleQrCodeScanner {
                qrScanning = false
                if (!it.isNullOrEmpty()) {
                    try {
                        postViewModel.updateNIP47(it)
                    } catch (e: IllegalArgumentException) {
                        if (e.message != null) {
                            accountViewModel.toastManager.toast(
                                stringRes(context, R.string.error_parsing_nip47_title),
                                stringRes(context, R.string.error_parsing_nip47, it, e.message!!),
                            )
                        } else {
                            accountViewModel.toastManager.toast(
                                stringRes(context, R.string.error_parsing_nip47_title),
                                stringRes(context, R.string.error_parsing_nip47_no_error, it),
                            )
                        }
                    }
                }
            }
        }

        // Expandable manual configuration section
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { showManualConfig = !showManualConfig }
                    .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringRes(R.string.wallet_connect_manual_config),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.weight(1f),
                fontSize = Font14SP,
            )
            Icon(
                imageVector = if (showManualConfig) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(
            visible = showManualConfig,
            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.wallet_connect_service_pubkey)) },
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.wallet_connect_service_relay)) },
                        modifier = Modifier.fillMaxWidth(),
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

                val secretContext = LocalContext.current

                val keyguardLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            showPassword = true
                        }
                    }

                val authTitle = stringRes(id = R.string.wallet_connect_service_show_secret)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.wallet_connect_service_secret)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.walletConnectSecret,
                        onValueChange = { postViewModel.walletConnectSecret = it },
                        keyboardOptions =
                            KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Go,
                            ),
                        placeholder = {
                            Text(
                                text = stringRes(R.string.wallet_connect_service_secret_placeholder),
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (!showPassword) {
                                        authenticate(
                                            title = authTitle,
                                            context = secretContext,
                                            keyguardLauncher = keyguardLauncher,
                                            onApproved = { showPassword = true },
                                            onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
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
                                            stringRes(R.string.show_password)
                                        } else {
                                            stringRes(R.string.hide_password)
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

        Spacer(modifier = Modifier.height(16.dp))
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
                stringRes(context, R.string.app_name),
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
        BiometricPrompt.PromptInfo
            .Builder()
            .setTitle(stringRes(context, R.string.app_name))
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
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            keyguardPrompt()
                        }

                        BiometricPrompt.ERROR_LOCKOUT -> {
                            keyguardPrompt()
                        }

                        else -> {
                            onError(
                                stringRes(context, R.string.biometric_authentication_failed),
                                stringRes(
                                    context,
                                    R.string.biometric_authentication_failed_explainer_with_error,
                                    errString.toString(),
                                ),
                            )
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError(
                        stringRes(context, R.string.biometric_authentication_failed),
                        stringRes(context, R.string.biometric_authentication_failed_explainer),
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
