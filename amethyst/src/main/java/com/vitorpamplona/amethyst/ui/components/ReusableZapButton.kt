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
package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ObserveZapIcon
import com.vitorpamplona.amethyst.ui.note.PayViaIntentDialog
import com.vitorpamplona.amethyst.ui.note.ZapAmountChoicePopup
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ModifierWidth3dp
import com.vitorpamplona.amethyst.ui.theme.Size14Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Configuration for zap button behavior and appearance
 */
data class ZapButtonConfig(
    val grayTint: Color = Color.Gray,
    val iconSize: Dp = Size35dp,
    val iconSizeModifier: Modifier = Size20Modifier,
    val animationModifier: Modifier = Size14Modifier,
    val showUserFinderSubscription: Boolean = false,
    val zapAmountChoices: List<Long>? = null,
    val thankYouText: String? = null,
    val buttonText: String? = null,
)

/**
 * Callbacks for zap button events
 */
data class ZapButtonCallbacks(
    val onZapComplete: ((Boolean) -> Unit)? = null,
)

@Composable
fun ReusableZapButton(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    config: ZapButtonConfig = ZapButtonConfig(),
    callbacks: ZapButtonCallbacks = ZapButtonCallbacks(),
) {
    var wantsToZap by remember { mutableStateOf<ImmutableList<Long>?>(null) }
    var wantsToPay by remember(baseNote) {
        mutableStateOf<ImmutableList<ZapPaymentHandler.Payable>>(persistentListOf())
    }

    // Makes sure the user is loaded to get his ln address ahead of time (for DVM buttons)
    if (config.showUserFinderSubscription) {
        baseNote.author?.let { author ->
            UserFinderFilterAssemblerSubscription(author, accountViewModel)
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var zappingProgress by remember { mutableFloatStateOf(0f) }
    var zapStartingTime by remember { mutableLongStateOf(0L) }
    var hasZapped by remember { mutableStateOf(false) }

    Button(
        onClick = {
            handleZapClick(
                baseNote = baseNote,
                accountViewModel = accountViewModel,
                context = context,
                zapAmountChoices = config.zapAmountChoices,
                onZapStarts = { zapStartingTime = TimeUtils.now() },
                onZappingProgress = { progress ->
                    scope.launch { zappingProgress = progress }
                },
                onMultipleChoices = { options ->
                    wantsToZap = options.toImmutableList()
                },
                onError = { _, message, toUser ->
                    scope.launch {
                        zappingProgress = 0f
                        accountViewModel.toastManager.toast(R.string.error_dialog_zap_error, message, toUser)
                    }
                },
                onPayViaIntent = { wantsToPay = it },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        wantsToZap?.let { zapAmountChoices ->
            ZapAmountChoicePopup(
                baseNote = baseNote,
                zapAmountChoices = zapAmountChoices,
                popupYOffset = config.iconSize,
                accountViewModel = accountViewModel,
                onZapStarts = { zapStartingTime = TimeUtils.now() },
                onDismiss = {
                    wantsToZap = null
                    zappingProgress = 0f
                },
                onChangeAmount = {
                    wantsToZap = null
                },
                onError = { _, message, user ->
                    scope.launch {
                        zappingProgress = 0f
                        accountViewModel.toastManager.toast(R.string.error_dialog_zap_error, message, user)
                    }
                },
                onProgress = {
                    scope.launch(Dispatchers.Main) { zappingProgress = it }
                },
                onPayViaIntent = { wantsToPay = it },
            )
        }

        if (wantsToPay.isNotEmpty()) {
            PayViaIntentDialog(
                payingInvoices = wantsToPay,
                accountViewModel = accountViewModel,
                onClose = { wantsToPay = persistentListOf() },
                onError = {
                    wantsToPay = persistentListOf()
                    scope.launch {
                        zappingProgress = 0f
                        accountViewModel.toastManager.toast(R.string.error_dialog_zap_error, it)
                    }
                },
                justShowError = {
                    scope.launch {
                        accountViewModel.toastManager.toast(R.string.error_dialog_zap_error, it)
                    }
                },
            )
        }

        // Zap Icon and Progress
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = config.iconSizeModifier,
        ) {
            if (zappingProgress > 0.00001 && zappingProgress < 0.99999) {
                Spacer(ModifierWidth3dp)

                val animatedProgress by animateFloatAsState(
                    targetValue = zappingProgress,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "ZapIconIndicator",
                )

                ObserveZapIcon(
                    baseNote,
                    accountViewModel,
                    zapStartingTime,
                ) { wasZappedByLoggedInUser ->
                    CrossfadeIfEnabled(
                        targetState = wasZappedByLoggedInUser.value,
                        label = "ZapIcon",
                        accountViewModel = accountViewModel,
                    ) {
                        if (it) {
                            ZappedIcon(config.iconSizeModifier)
                        } else {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = config.animationModifier,
                                strokeWidth = 2.dp,
                                color = config.grayTint,
                            )
                        }
                    }
                }
            } else {
                ObserveZapIcon(
                    baseNote,
                    accountViewModel,
                ) { wasZappedByLoggedInUser ->
                    LaunchedEffect(wasZappedByLoggedInUser.value) {
                        hasZapped = wasZappedByLoggedInUser.value
                        callbacks.onZapComplete?.invoke(wasZappedByLoggedInUser.value)

                        if (wasZappedByLoggedInUser.value && !accountViewModel.account.hasDonatedInThisVersion()) {
                            delay(1000)
                            accountViewModel.markDonatedInThisVersion()
                        }
                    }

                    CrossfadeIfEnabled(
                        targetState = wasZappedByLoggedInUser.value,
                        label = "ZapIcon",
                        accountViewModel = accountViewModel,
                    ) {
                        if (it) {
                            ZappedIcon(config.iconSizeModifier)
                        } else {
                            ZapIcon(config.iconSizeModifier, config.grayTint)
                        }
                    }
                }
            }
        }

        val displayText =
            when {
                hasZapped -> config.thankYouText ?: stringRes(id = R.string.thank_you)
                config.buttonText != null -> config.buttonText
                else -> stringRes(id = R.string.donate_now)
            }

        Text(text = displayText)
    }
}

private fun handleZapClick(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    context: Context,
    zapAmountChoices: List<Long>?,
    onZapStarts: () -> Unit,
    onZappingProgress: (Float) -> Unit,
    onMultipleChoices: (List<Long>) -> Unit,
    onError: (String, String, User?) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
) {
    if (baseNote.isDraft()) {
        accountViewModel.toastManager.toast(
            R.string.draft_note,
            R.string.it_s_not_possible_to_zap_to_a_draft_note,
        )
        return
    }

    val choices = zapAmountChoices ?: accountViewModel.zapAmountChoices()

    if (choices.isEmpty()) {
        accountViewModel.toastManager.toast(
            stringRes(context, R.string.error_dialog_zap_error),
            stringRes(context, R.string.no_zap_amount_setup_long_press_to_change),
        )
    } else if (!accountViewModel.isWriteable()) {
        accountViewModel.toastManager.toast(
            stringRes(context, R.string.error_dialog_zap_error),
            stringRes(context, R.string.login_with_a_private_key_to_be_able_to_send_zaps),
        )
    } else if (choices.size == 1) {
        val amount = choices.first()

        if (amount > 1100 || zapAmountChoices != null) {
            onZapStarts()
            accountViewModel.zap(
                baseNote,
                amount * 1000,
                null,
                "",
                context,
                showErrorIfNoLnAddress = false,
                onError = onError,
                onProgress = { onZappingProgress(it) },
                onPayViaIntent = onPayViaIntent,
            )
        } else {
            onMultipleChoices(listOf(1000L, 5_000L, 10_000L))
        }
    } else {
        if (choices.any { it > 1100 } || zapAmountChoices != null) {
            onMultipleChoices(choices)
        } else {
            onMultipleChoices(listOf(1000L, 5_000L, 10_000L))
        }
    }
}
