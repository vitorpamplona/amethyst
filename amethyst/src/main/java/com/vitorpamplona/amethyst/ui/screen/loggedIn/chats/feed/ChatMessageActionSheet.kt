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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
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
import com.vitorpamplona.amethyst.ui.components.toasts.multiline.UserBasedErrorMessage
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ChangeReactionIcon
import com.vitorpamplona.amethyst.ui.note.RenderReaction
import com.vitorpamplona.amethyst.ui.note.ZapAmountChoiceGrid
import com.vitorpamplona.amethyst.ui.note.elements.AddHashtagLabelDialog
import com.vitorpamplona.amethyst.ui.note.elements.DropDownParams
import com.vitorpamplona.amethyst.ui.note.elements.ShareOptionsBottomSheet
import com.vitorpamplona.amethyst.ui.note.elements.observeBookmarksFollowsAndAccount
import com.vitorpamplona.amethyst.ui.note.observeZapRailCapability
import com.vitorpamplona.amethyst.ui.note.payViaIntent
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
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// null amount = open the on-chain dialog with no prefill.
@Immutable
private data class OnchainZapRequest(
    val amountSats: Long?,
)

/**
 * Long-press surface for a chat message: a quick-reaction row and the unpacked
 * zap amount presets on top, then every note action the 3-dot menu offers,
 * grouped by similarity into rows of icon tiles (message, author, organize,
 * moderation).
 *
 * The music-playlist and emoji-pack curation specials from the 3-dot menu are
 * intentionally absent: those kinds never render in a chat feed.
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

            MessageSection(note, state, onWantsToReply, onWantsToEditDraft, onEditPost = { wantsToEditPost = true }, onShare = { showShareSheet = true }, onDismiss, accountViewModel)

            SectionDivider()

            AuthorSection(note, state, onDismiss, accountViewModel, nav)

            if (!note.isPrivateRumor()) {
                SectionDivider()
                OrganizeSection(note, state, onAddLabel = { addLabelDialogShowing = true }, onDismiss, accountViewModel, nav)
            }

            SectionDivider()

            ModerationSection(note, state, onReport = { reportDialogShowing = true }, onDismiss, accountViewModel)

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Action tile sections (the unpacked 3-dot menu) ----------

@OptIn(ExperimentalLayoutApi::class, ExperimentalUuidApi::class)
@Composable
private fun MessageSection(
    note: Note,
    state: DropDownParams,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onEditPost: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val isPrivateRumor = note.isPrivateRumor()

    TileRow {
        if (!note.isDraft()) {
            ActionTile(MaterialSymbols.AutoMirrored.Chat, stringRes(R.string.reply_description)) {
                onWantsToReply(note)
                onDismiss()
            }
        }

        if (state.isLoggedUser && note.isDraft()) {
            ActionTile(MaterialSymbols.Edit, stringRes(R.string.edit_draft)) {
                onWantsToEditDraft(note)
                onDismiss()
            }
        }

        if (!note.isDraft() && !isPrivateRumor && note.event is TextNoteEvent) {
            ActionTile(
                MaterialSymbols.Edit,
                stringRes(if (state.isLoggedUser) R.string.edit_post else R.string.propose_an_edit),
                onClick = onEditPost,
            )
        }

        ActionTile(MaterialSymbols.ContentCopy, stringRes(R.string.copy_text)) {
            accountViewModel.decrypt(note) {
                scope.launch { clipboardManager.setText(it) }
            }
            onDismiss()
        }

        ActionTile(MaterialSymbols.FormatQuote, stringRes(R.string.copy_note_id)) {
            scope.launch(Dispatchers.IO) {
                clipboardManager.setText(note.toNostrUri())
                onDismiss()
            }
        }

        ActionTile(MaterialSymbols.ContentCopy, stringRes(R.string.copy_raw_json)) {
            val event = note.event
            if (event != null) {
                scope.launch {
                    val json = withContext(Dispatchers.Default) { JacksonMapper.toJsonPretty(event) }
                    clipboardManager.setText(json)
                    onDismiss()
                }
            } else {
                onDismiss()
            }
        }

        if (!isPrivateRumor) {
            ActionTile(MaterialSymbols.Share, stringRes(R.string.quick_action_share), onClick = onShare)
        }

        // Rumors are rebroadcast as their delivering gift wrap; hidden when the
        // wrap is unknown (the unsigned rumor must never be published).
        if (accountViewModel.canBroadcast(note)) {
            ActionTile(MaterialSymbols.CellTower, stringRes(R.string.broadcast)) {
                accountViewModel.broadcast(note)
                onDismiss()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthorSection(
    note: Note,
    state: DropDownParams,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    TileRow {
        if (!state.isLoggedUser) {
            if (!state.isFollowingAuthor) {
                ActionTile(MaterialSymbols.PersonAdd, stringRes(R.string.follow)) {
                    note.author?.let { accountViewModel.follow(it) }
                    onDismiss()
                }
            } else {
                ActionTile(MaterialSymbols.PersonRemove, stringRes(R.string.unfollow)) {
                    note.author?.let { accountViewModel.unfollow(it) }
                    onDismiss()
                }
            }
        }

        ActionTile(MaterialSymbols.AutoMirrored.PlaylistAdd, stringRes(R.string.follow_set_add_author_from_note_action)) {
            note.author?.pubkeyHex?.let {
                nav.nav(Route.PeopleListManagement(it))
            }
            onDismiss()
        }

        ActionTile(MaterialSymbols.AlternateEmail, stringRes(R.string.copy_user_pubkey)) {
            note.author?.let {
                scope.launch(Dispatchers.IO) {
                    clipboardManager.setText("nostr:${it.pubkeyNpub()}")
                    onDismiss()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganizeSection(
    note: Note,
    state: DropDownParams,
    onAddLabel: () -> Unit,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TileRow {
        if (accountViewModel.account.otsState.hasPendingAttestations(note)) {
            ActionTile(MaterialSymbols.Schedule, stringRes(R.string.timestamp_pending)) { onDismiss() }
        } else {
            ActionTile(MaterialSymbols.Schedule, stringRes(R.string.timestamp_it)) {
                accountViewModel.timestamp(note)
                onDismiss()
            }
        }

        if (state.isLoggedUser) {
            ActionTile(
                MaterialSymbols.PushPin,
                stringRes(if (state.isPinnedNote) R.string.unpin_from_profile else R.string.pin_to_profile),
            ) {
                if (state.isPinnedNote) {
                    accountViewModel.removePin(note)
                } else {
                    accountViewModel.addPin(note)
                }
                onDismiss()
            }
        }

        ActionTile(MaterialSymbols.Tag, stringRes(R.string.add_hashtag_label), onClick = onAddLabel)

        ActionTile(MaterialSymbols.BookmarkAdd, stringRes(R.string.manage_bookmark_label, stringRes(R.string.post))) {
            nav.nav(Route.PostBookmarkManagement(note.idHex))
            onDismiss()
        }

        if (state.isPrivateBookmarkNote) {
            ActionTile(MaterialSymbols.LockOpen, stringRes(R.string.remove_from_private_bookmarks)) {
                accountViewModel.removePrivateBookmark(note)
                onDismiss()
            }
        } else {
            ActionTile(MaterialSymbols.Lock, stringRes(R.string.add_to_private_bookmarks)) {
                accountViewModel.addPrivateBookmark(note)
                onDismiss()
            }
        }

        if (state.isPublicBookmarkNote) {
            ActionTile(MaterialSymbols.BookmarkRemove, stringRes(R.string.remove_from_public_bookmarks)) {
                accountViewModel.removePublicBookmark(note)
                onDismiss()
            }
        } else {
            ActionTile(MaterialSymbols.Bookmark, stringRes(R.string.add_to_public_bookmarks)) {
                accountViewModel.addPublicBookmark(note)
                onDismiss()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModerationSection(
    note: Note,
    state: DropDownParams,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    TileRow {
        val isThreadMuted = accountViewModel.isThreadMutedFor(note)
        ActionTile(
            MaterialSymbols.AutoMirrored.VolumeOff,
            stringRes(if (isThreadMuted) R.string.quick_action_unmute_thread else R.string.quick_action_mute_thread),
        ) {
            if (isThreadMuted) {
                accountViewModel.unmuteThread(note)
            } else {
                accountViewModel.muteThread(note)
            }
            onDismiss()
        }

        if (state.isLoggedUser && !note.isPrivateRumor()) {
            ActionTile(MaterialSymbols.Delete, stringRes(R.string.request_deletion), isDestructive = true) {
                accountViewModel.delete(note)
                onDismiss()
            }
        } else {
            ActionTile(MaterialSymbols.Report, stringRes(R.string.block_report), isDestructive = true, onClick = onReport)
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
