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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.vitorpamplona.amethyst.ui.actions.EditPostView
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ChangeReactionIcon
import com.vitorpamplona.amethyst.ui.note.QuickActionAlertDialog
import com.vitorpamplona.amethyst.ui.note.RenderReaction
import com.vitorpamplona.amethyst.ui.note.ZapAmountChoiceGrid
import com.vitorpamplona.amethyst.ui.note.elements.AddHashtagLabelDialog
import com.vitorpamplona.amethyst.ui.note.elements.ConcordBanConfirmationDialog
import com.vitorpamplona.amethyst.ui.note.elements.DropDownParams
import com.vitorpamplona.amethyst.ui.note.elements.NoteActionHandlers
import com.vitorpamplona.amethyst.ui.note.elements.ShareOptionsBottomSheet
import com.vitorpamplona.amethyst.ui.note.elements.noteActionSections
import com.vitorpamplona.amethyst.ui.note.elements.observeBookmarksFollowsAndAccount
import com.vitorpamplona.amethyst.ui.note.observeZapRailCapability
import com.vitorpamplona.amethyst.ui.note.payViaIntentOrManualSplit
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.report.ReportNoteDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.OnchainZapSendDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.navigateToReloadMint
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size28Modifier
import com.vitorpamplona.amethyst.ui.theme.SmallishBorder
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.reactionBox
import com.vitorpamplona.amethyst.ui.theme.selectedReactionBoxModifier
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlin.uuid.ExperimentalUuidApi

// null amount = open the on-chain dialog with no prefill.
@Immutable
private data class OnchainZapRequest(
    val amountSats: Long?,
)

/**
 * Long-press surface for a chat message: a quick-reaction row and the unpacked
 * zap amount presets on top, chat-only tiles (reply, edit draft), then the shared
 * note-action inventory (noteActionSections — the same one behind the 3-dot menu,
 * so the two surfaces can never drift) rendered as rows of icon tiles.
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
    var showShareSheet by remember { mutableStateOf(false) }
    var wantsToEditPost by remember { mutableStateOf(false) }
    var reportDialogShowing by remember { mutableStateOf(false) }
    var addLabelDialogShowing by remember { mutableStateOf(false) }
    var deleteConfirmationShowing by remember { mutableStateOf(false) }
    var concordBanConfirming by remember { mutableStateOf(false) }
    var showDeliveryDialog by remember { mutableStateOf(false) }

    // Own private rumors (NIP-17 DMs) must be retracted with a gift-wrapped
    // deletion — a public NIP-09 would e-tag the rumor id onto public relays.
    val performDelete = {
        if (note.isPrivateRumor()) {
            accountViewModel.deletePrivately(note)
        } else {
            accountViewModel.delete(note)
        }
    }

    if (deleteConfirmationShowing) {
        QuickActionAlertDialog(
            title = stringRes(R.string.quick_action_request_deletion_alert_title),
            textContent = stringRes(R.string.quick_action_request_deletion_alert_body),
            buttonIcon = MaterialSymbols.Delete,
            buttonText = stringRes(R.string.quick_action_delete_dialog_btn),
            onClickDoOnce = {
                performDelete()
                onDismiss()
            },
            onClickDontShowAgain = {
                performDelete()
                accountViewModel.account.settings.setHideDeleteRequestDialog()
                onDismiss()
            },
            onDismiss = { deleteConfirmationShowing = false },
        )
    }

    // On-chain zaps need a dialog that outlives the sheet, so the request swaps
    // the sheet for the dialog. Share works the same way (see NoteDropDownMenu).
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

    if (showShareSheet) {
        ShareOptionsBottomSheet(
            note = note,
            nav = nav,
            onDismiss = onDismiss,
        )
        return
    }

    if (showDeliveryDialog) {
        ChatMessageDeliveryDialog(
            baseNote = note,
            accountViewModel = accountViewModel,
            nav = nav,
            onDismiss = onDismiss,
        )
        return
    }

    if (wantsToEditPost) {
        EditPostView(
            onClose = onDismiss,
            edit = note,
            versionLookingAt = null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    if (reportDialogShowing) {
        ReportNoteDialog(note = note, accountViewModel = accountViewModel) {
            reportDialogShowing = false
            onDismiss()
        }
    }

    if (addLabelDialogShowing) {
        AddHashtagLabelDialog(
            note = note,
            accountViewModel = accountViewModel,
            onDismiss = {
                addLabelDialogShowing = false
                onDismiss()
            },
        )
    }

    if (concordBanConfirming) {
        ConcordBanConfirmationDialog(
            note = note,
            accountViewModel = accountViewModel,
            onClose = { concordBanConfirming = false },
            onBanned = onDismiss,
        )
    }

    val state by observeBookmarksFollowsAndAccount(note, accountViewModel).collectAsStateWithLifecycle(
        DropDownParams(
            isFollowingAuthor = false,
            isPrivateBookmarkNote = false,
            isPublicBookmarkNote = false,
            isPinnedNote = false,
            isLoggedUser = false,
            isSensitive = false,
            showSensitiveContent = null,
        ),
    )

    var showAllActions by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (!note.isDraft()) {
                QuickReactionRow(note, onDismiss, accountViewModel, nav)

                QuickZapAmountRow(
                    note = note,
                    onDismiss = onDismiss,
                    onOnchainRequest = { onchainZapRequest = OnchainZapRequest(it) },
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

                SectionDivider()
            }

            // Stage one: the primary chat action (reply / edit draft) is always shown.
            ChatOnlyRow(note, state, onWantsToReply, onWantsToEditDraft, { showDeliveryDialog = true }, onDismiss)

            val handlers =
                NoteActionHandlers(
                    onShare = { showShareSheet = true },
                    onEditPost = { wantsToEditPost = true },
                    onEditDraft = {
                        onWantsToEditDraft(note)
                        onDismiss()
                    },
                    onAddLabel = { addLabelDialogShowing = true },
                    onReport = { reportDialogShowing = true },
                    onDeleteRequest = {
                        if (accountViewModel.account.settings.hideDeleteRequestDialog) {
                            performDelete()
                            onDismiss()
                        } else {
                            deleteConfirmationShowing = true
                        }
                    },
                    onConcordBan = { concordBanConfirming = true },
                    onDismiss = onDismiss,
                )

            // Stage two: the full shared note-action inventory (the same one behind the
            // 3-dot menu, so the two surfaces never drift). Tucked behind a toggle so the
            // sheet opens compact — react, zap, reply — and only grows to full height
            // when the user asks for the rest.
            val sections =
                noteActionSections(
                    note = note,
                    noteVersionToCopy = note,
                    state = state,
                    handlers = handlers,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

            if (sections.isNotEmpty()) {
                SectionDivider()
                MoreActionsToggle(expanded = showAllActions, onToggle = { showAllActions = !showAllActions })

                if (showAllActions) {
                    sections.forEach { section ->
                        SectionDivider()
                        TileRow {
                            section.forEach { action ->
                                ActionTile(action.symbol, action.label, action.isDestructive, action.onClick)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The expand/collapse control between the always-visible quick actions and the full
 * action inventory. Keeps the sheet short on open (the common case is a reaction, a
 * zap, or a reply) and reveals everything else on demand.
 */
@Composable
private fun MoreActionsToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(SmallishBorder)
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(if (expanded) R.string.show_less else R.string.more_options),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Action tile sections ----------

/** Chat-specific tiles (reply, message delivery, edit draft) that have no 3-dot menu equivalent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatOnlyRow(
    note: Note,
    state: DropDownParams,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onShowDelivery: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (note.isDraft() && !state.isLoggedUser) return
    if (note.isDraft() && state.isLoggedUser) {
        TileRow {
            ActionTile(MaterialSymbols.Edit, stringRes(R.string.edit_draft)) {
                onWantsToEditDraft(note)
                onDismiss()
            }
        }
        return
    }

    TileRow {
        ActionTile(MaterialSymbols.AutoMirrored.Chat, stringRes(R.string.reply_description)) {
            onWantsToReply(note)
            onDismiss()
        }
        // Which relays carry this message (and, for our own, their acceptance) — the
        // reliable way to reach the relay list now that the detail row is gone.
        ActionTile(MaterialSymbols.DoneAll, stringRes(R.string.chat_delivery_details_title)) {
            onShowDelivery()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

@Composable
private fun ActionTile(
    symbol: MaterialSymbol,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier =
            Modifier
                .width(80.dp)
                .clip(SmallishBorder)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val SECTION_DIVIDER_ALPHA = 0.5f

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        thickness = DividerThickness,
        color = MaterialTheme.colorScheme.placeholderText.copy(alpha = SECTION_DIVIDER_ALPHA),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

// ---------- Quick reaction + zap rows ----------

@OptIn(ExperimentalLayoutApi::class)
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

    // Wrap onto as many rows as needed (like the zap grid) rather than a single
    // horizontally-scrolling strip, so long custom-reaction sets stay reachable.
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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
        // Payment failed — drop the optimistic "zapping" indicator on the bubble.
        accountViewModel.endZapInFlight(note.idHex)
        accountViewModel.toastManager.toast(R.string.error_dialog_zap_error, message, user)
    }

    val onPayViaIntent = { payables: ImmutableList<ZapPaymentHandler.Payable> ->
        // Handoff to an external wallet: we can't observe whether it completes, so clear
        // the optimistic indicator rather than leave it spinning forever.
        accountViewModel.endZapInFlight(note.idHex)
        payViaIntentOrManualSplit(payables, context, accountViewModel, nav)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        ZapAmountChoiceGrid(
            amountChoices = amountChoices,
            railCapability = railCapability,
            onLightningZap = { amountInSats ->
                // Show a pending zap chip on the bubble immediately; it settles into the
                // real sats chip once the receipt lands (or clears on error/timeout).
                accountViewModel.markZapInFlight(note.idHex)
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
                accountViewModel.markZapInFlight(note.idHex)
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
