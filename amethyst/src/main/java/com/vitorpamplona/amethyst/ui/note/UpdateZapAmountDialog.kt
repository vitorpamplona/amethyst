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

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.keyBackup.getFragmentActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.ui.theme.SettingsCategorySpacingModifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
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
    accountViewModel: AccountViewModel,
    trailingContent: @Composable ColumnScope.() -> Unit = {},
) {
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
                ZapAmountPresetChip(
                    amountInSats = amountInSats,
                    onRemove = { postViewModel.removeAmount(amountInSats) },
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
                        text = "21, 50, 100",
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
                            symbol = MaterialSymbols.AddCircle,
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

        trailingContent()

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * A quick-zap preset chip that mirrors the live feed chip: the amount with the
 * rails it could pay on — the amount-tier default rail in colour on the left,
 * the alternatives in monochrome — plus an X to remove it. No recipient context
 * here, so the rails are the ones a preset of this size could use (cashu,
 * Lightning, and on-chain at/above the minimum).
 */
@Composable
private fun ZapAmountPresetChip(
    amountInSats: Long,
    onRemove: () -> Unit,
) {
    val rails = remember(amountInSats) { previewRailsFor(amountInSats) }
    val accent = zapRailAccent(rails.first(), MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary)
    Surface(
        shape = ButtonBorder,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZapRailIcon(rails.first(), colored = true)
            Spacer(Modifier.width(5.dp))
            Text(
                text = showAmount(amountInSats.toBigDecimal().setScale(1)),
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            rails.drop(1).forEach { rail ->
                Spacer(Modifier.width(4.dp))
                ZapRailIcon(rail, colored = false)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(R.string.remove),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
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
