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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.components.toasts.multiline.UserBasedErrorMessage
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ChangeReactionIcon
import com.vitorpamplona.amethyst.ui.note.RenderReaction
import com.vitorpamplona.amethyst.ui.note.ZapAmountChoiceGrid
import com.vitorpamplona.amethyst.ui.note.elements.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.note.observeZapRailCapability
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.OnchainZapSendDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.navigateToReloadMint
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size28Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.reactionBox
import com.vitorpamplona.amethyst.ui.theme.selectedReactionBoxModifier
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// null amount = open the on-chain dialog with no prefill.
@Immutable
private data class OnchainZapRequest(
    val amountSats: Long?,
)

/**
 * Long-press surface for a chat message: a quick-reaction row and the unpacked
 * zap amount presets on top, the common chat actions (reply, copy) as rows, and a
 * handoff to the full note options menu (the same 3-dot menu posts get) for
 * everything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMessageActionSheet(
    note: Note,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var showMoreOptions by remember { mutableStateOf(false) }

    // On-chain zaps need a dialog that outlives the sheet, so the request swaps
    // the sheet for the dialog (same pattern as "More options" below).
    var onchainZapRequest by remember { mutableStateOf<OnchainZapRequest?>(null) }

    onchainZapRequest?.let { request ->
        val zappedEventHint = remember(note) { note.toEventHint<Event>() }
        OnchainZapSendDialog(
            accountViewModel = accountViewModel,
            onDismiss = onDismiss,
            recipientPubKey = note.author?.pubkeyHex,
            zappedEvent = zappedEventHint,
            prefillAmountSats = request.amountSats,
        )
        return
    }

    // "More options" swaps this sheet for the full note menu instead of stacking
    // the two surfaces; dismissing the menu closes the whole interaction.
    if (showMoreOptions) {
        NoteDropDownMenu(
            note = note,
            onDismiss = onDismiss,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (!note.isDraft()) {
            QuickReactionRow(note, onDismiss, accountViewModel, nav)

            QuickZapAmountRow(
                note = note,
                onDismiss = onDismiss,
                onOnchainRequest = { onchainZapRequest = OnchainZapRequest(it) },
                accountViewModel = accountViewModel,
                nav = nav,
            )

            HorizontalDivider(
                thickness = DividerThickness,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (!note.isDraft()) {
            SheetActionRow(
                symbol = MaterialSymbols.AutoMirrored.Chat,
                label = stringRes(R.string.reply_description),
            ) {
                onWantsToReply(note)
                onDismiss()
            }
        }

        if (note.isDraft() && accountViewModel.isLoggedUser(note.author)) {
            SheetActionRow(
                symbol = MaterialSymbols.Edit,
                label = stringRes(R.string.edit_draft),
            ) {
                onWantsToEditDraft(note)
                onDismiss()
            }
        }

        CopyTextActionRow(note, onDismiss, accountViewModel)

        SheetActionRow(
            symbol = MaterialSymbols.MoreVert,
            label = stringRes(R.string.more_options),
        ) {
            showMoreOptions = true
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuickReactionRow(
    note: Note,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val reactions by accountViewModel.reactionChoicesFlow().collectAsStateWithLifecycle()
    val myReactions =
        remember(note) {
            note.allReactionsByAuthor(accountViewModel.userProfile()).toImmutableSet()
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactions.forEach { reactionType ->
            ClickableBox(
                modifier = if (reactionType in myReactions) MaterialTheme.colorScheme.selectedReactionBoxModifier else reactionBox,
                onClick = {
                    accountViewModel.reactToOrDelete(note, reactionType)
                    onDismiss()
                },
            ) {
                RenderReaction(reactionType)
            }
        }

        ClickableBox(
            modifier = reactionBox,
            onClick = {
                nav.nav(Route.UpdateReactionType)
                onDismiss()
            },
        ) {
            ChangeReactionIcon(Size28Modifier, MaterialTheme.colorScheme.placeholderText)
        }
    }
}

/**
 * The user's zap presets unpacked as rail-aware amount chips (same grid the zap
 * amount popup shows), firing directly from the sheet. Lightning/cashu zaps
 * dismiss immediately — errors surface as toasts and the receipt lands on the
 * bubble's sats chip; on-chain amounts hand off to the dialog hosted by the sheet.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun QuickZapAmountRow(
    note: Note,
    onDismiss: () -> Unit,
    onOnchainRequest: (Long?) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val zapAmountChoices by
        accountViewModel.account.settings.syncedSettings.zaps.zapAmountChoices
            .collectAsStateWithLifecycle()

    val amountChoices = remember(zapAmountChoices) { zapAmountChoices.distinct().toImmutableList() }
    if (amountChoices.isEmpty()) return

    val railCapability =
        observeZapRailCapability(
            baseNote = note,
            accountViewModel = accountViewModel,
            // Onchain zap events are public and would e-tag the rumor id.
            onchainSupported = !note.isPrivateRumor(),
        )

    val context = LocalContext.current

    val onError = { _: String, message: String, user: User? ->
        accountViewModel.toastManager.toast(R.string.error_dialog_zap_error, message, user)
    }

    val onPayViaIntent = { payables: ImmutableList<ZapPaymentHandler.Payable> ->
        if (payables.size == 1) {
            val payable = payables.first()
            payViaIntent(payable.invoice, context, { }) { error ->
                accountViewModel.toastManager.toast(R.string.error_dialog_zap_error, UserBasedErrorMessage(error, payable.info.user))
            }
        } else {
            val uid = Uuid.random().toString()
            accountViewModel.tempManualPaymentCache.put(uid, payables)
            nav.nav(Route.ManualZapSplitPayment(uid))
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        ZapAmountChoiceGrid(
            amountChoices = amountChoices,
            railCapability = railCapability,
            onLightningZap = { amountInSats ->
                accountViewModel.zap(
                    note,
                    amountInSats * 1000,
                    null,
                    "",
                    context,
                    true,
                    onError,
                    { },
                    onPayViaIntent,
                )
                onDismiss()
            },
            onNutzap = { amountInSats ->
                accountViewModel.sendNutzap(
                    baseNote = note,
                    amountSats = amountInSats,
                    message = "",
                    onError = onError,
                    onProgress = { },
                )
                onDismiss()
            },
            onOnchainAmount = onOnchainRequest,
            onReloadNutzap = { amount ->
                navigateToReloadMint(accountViewModel, nav, note, amount)
                onDismiss()
            },
            onChangeAmount = {
                nav.nav(Route.UpdateZapAmount())
                onDismiss()
            },
        )
    }
}

@Composable
private fun CopyTextActionRow(
    note: Note,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    SheetActionRow(
        symbol = MaterialSymbols.ContentCopy,
        label = stringRes(R.string.copy_text),
    ) {
        accountViewModel.decrypt(note) {
            scope.launch {
                clipboardManager.setText(it)
            }
        }
        onDismiss()
    }
}

@Composable
private fun SheetActionRow(
    symbol: MaterialSymbol,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.placeholderText,
        )
        Text(text = label, fontSize = 16.sp)
    }
}
