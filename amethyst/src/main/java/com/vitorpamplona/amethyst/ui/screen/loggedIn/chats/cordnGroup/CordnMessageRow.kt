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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.chats.ui.ChatDivisor
import com.vitorpamplona.amethyst.commons.chats.ui.UserDisplayNameLayout
import com.vitorpamplona.amethyst.commons.cordn.CordnMentions
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.today
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.ui.note.elements.ToggleableTimeAgoText
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ActionTile
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ChatChipFlowRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ReactionChip
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ReactionChipView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.SectionDivider
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.TileRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.authorNameColorFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.jumboEmojiCount
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.jumboEmojiFontSize
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.CHAT_GROUP_WINDOW_SECONDS
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.ChatBubbleLayout
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.ChatGroupPosition
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.cordn.appEncryptedMedia.CordnMediaTag
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnAnnotationIndex
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnMessageReferences
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * A cordn message, drawn as an Amethyst chat bubble.
 *
 * Everything structural here is the app's shared chat furniture — [ChatBubbleLayout]
 * for the bubble, its grouping shapes and its whole gesture vocabulary (long-press for
 * the action sheet, double-tap to react, swipe toward the centre to reply),
 * [ReactionChipView] for the engagement strip, [ActionTile] for the sheet. A cordn room
 * used to draw its own flat rows, which meant the one chat in the app where a tap
 * opened a menu and nothing could be swiped.
 *
 * What cordn cannot share is the *content* of those slots. The shared fillings
 * ([com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ChatReactionChips],
 * `ChatMessageActionSheet`, `ChatMessageFooter`, `DrawAuthorInfo`) all take a `Note`,
 * and a `Note` comes from `LocalCache` — which nothing in a cordn room may enter, see
 * the screen's KDoc. So the slots are filled from the envelope and the annotation fold
 * instead, and the layout above them is the same one every other chat uses.
 */
@Composable
internal fun CordnMessageRow(
    message: CordnDeliveredMessage,
    room: CordnGroupChatroom,
    annotations: CordnAnnotationIndex,
    me: HexKey,
    groupPosition: ChatGroupPosition,
    text: String?,
    isEdited: Boolean,
    shouldHighlight: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onHighlightFinished: () -> Unit,
    onScrollToMessage: (HexKey) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onReact: (String) -> Unit,
) {
    val isMine = message.envelope.pubKey == me
    val isPinned = annotations.isPinned(message.envelope.id)
    val reactions = annotations.reactions[message.envelope.id].orEmpty()

    // A deleted message keeps its place in the conversation but stops accepting
    // annotations: replying to, editing or reacting to a withdrawal is meaningless,
    // and the manager would refuse most of it anyway.
    val isLive = text != null

    // A mention is the one reason to pick a message out of a wall of them. Read from
    // the content rather than from a `p` tag: a tag is a claim the sender makes about
    // who they addressed, while the text is what everyone in the room actually sees.
    val mentionsMe = remember(text, me) { text != null && me in CordnMentions.mentioned(text) }
    val jumboCount = remember(text) { if (text != null) jumboEmojiCount(text) else 0 }

    val mentionTint = MaterialTheme.colorScheme.primary.copy(alpha = MENTION_TINT_ALPHA)

    Box(if (mentionsMe) Modifier.fillMaxWidth().background(mentionTint) else Modifier.fillMaxWidth()) {
        ChatBubbleLayout(
            isLoggedInUser = isMine,
            isDraft = false,
            innerQuote = false,
            // Your own bubbles are right-aligned and tinted, so naming yourself over
            // every burst of them is noise. Everyone else is named once per burst.
            drawAuthorInfo = groupPosition.isFirstOfGroup && !isMine,
            groupPosition = groupPosition,
            transparentBubble = jumboCount > 0,
            shouldHighlight = shouldHighlight,
            onHighlightFinished = onHighlightFinished,
            // A plain tap is a no-op, exactly as in every other Amethyst chat.
            onClick = { false },
            onDoubleTap =
                if (isLive) {
                    { onReact(DEFAULT_REACTION) }
                } else {
                    null
                },
            onSwipeReply =
                if (isLive) {
                    onReply
                } else {
                    null
                },
            onAuthorClick = { nav.nav(Route.Profile(message.envelope.pubKey)) },
            actionMenu = { onDismiss ->
                CordnMessageActionSheet(
                    isMine = isMine,
                    isPinned = isPinned,
                    isLive = isLive,
                    accountViewModel = accountViewModel,
                    onDismiss = onDismiss,
                    onReply = onReply,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onTogglePin = onTogglePin,
                    onReact = onReact,
                )
            },
            reactionsRow =
                if (reactions.isEmpty()) {
                    null
                } else {
                    { CordnReactionChips(reactions, me, accountViewModel, nav, onReact) }
                },
            // Mirrors chatFooterHasMeta: the footer earns its row on the last message
            // of a burst (for the time) or on any message carrying a marker of its own.
            footerRow =
                if (groupPosition.isLastOfGroup || isEdited || isPinned) {
                    {
                        CordnMessageFooter(
                            createdAt = message.envelope.createdAt,
                            isEdited = isEdited,
                            isPinned = isPinned,
                            showTime = groupPosition.isLastOfGroup,
                        )
                    }
                } else {
                    null
                },
            drawAuthorLine = { CordnAuthorLine(message.envelope.pubKey, accountViewModel, nav) },
        ) { _ ->
            CordnBubbleContents(
                message = message,
                room = room,
                annotations = annotations,
                text = text,
                jumboCount = jumboCount,
                accountViewModel = accountViewModel,
                nav = nav,
                onScrollToMessage = onScrollToMessage,
            )
        }
    }
}

/** What a double-tap sends, and the fallback when the account lists no reaction choices. */
private const val DEFAULT_REACTION = "👍"

/** How strongly a message that mentions you tints its row. */
private const val MENTION_TINT_ALPHA = 0.10f

@Composable
private fun CordnBubbleContents(
    message: CordnDeliveredMessage,
    room: CordnGroupChatroom,
    annotations: CordnAnnotationIndex,
    text: String?,
    jumboCount: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
    onScrollToMessage: (HexKey) -> Unit,
) {
    val thread = remember(message.envelope.id) { CordnMessageReferences.thread(message.envelope.tags) }
    val parent = thread?.let { annotations.byId[it.parentId] }
    if (parent != null) {
        CordnQuotedMessage(
            parent = parent,
            annotations = annotations,
            accountViewModel = accountViewModel,
            onClick = { onScrollToMessage(parent.envelope.id) },
        )
    }

    when {
        text == null ->
            Text(
                text = stringRes(R.string.cordn_message_deleted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

        // Emoji-only messages render bare and large, over a transparent bubble.
        jumboCount > 0 -> Text(text = text.trim(), fontSize = jumboEmojiFontSize(jumboCount))

        else -> MessageBody(text, accountViewModel, nav)
    }

    // Only on a live message: a deleted one must not keep offering its attachment,
    // and the blob is still on the host either way.
    if (text != null) {
        CordnMediaTag.parseAll(message.envelope.tags).forEach { attachment ->
            CordnAttachment(attachment, room, accountViewModel)
        }
    }
}

/**
 * Name and face on the first bubble of a burst, in the shared chat author layout — so a
 * cordn sender is drawn exactly like a DM sender, colour included. [authorNameColorFor]
 * derives a stable hue from the pubkey, which is what makes authors scannable in a
 * fast-moving room.
 *
 * [observeUserNameByHex] falls back to a hex prefix until the profile arrives. Looking a
 * sender up for a display name is safe: a profile is public relay data the cache already
 * holds, and reading one puts no part of this conversation into it.
 */
@Composable
private fun CordnAuthorLine(
    pubKey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val name = observeUserNameByHex(pubKey, accountViewModel)
    val isLightTheme = MaterialTheme.colorScheme.isLight
    val nameColor = remember(pubKey, isLightTheme) { authorNameColorFor(pubKey, isLightTheme) }

    UserDisplayNameLayout(
        picture = {
            UserPicture(
                userHex = pubKey,
                size = Size20dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        name = {
            Text(
                text = name,
                color = nameColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/**
 * The engagement strip riding the bubble's bottom border.
 *
 * A reaction is a set of pubkeys per emoji in the fold, so the count is the set size — a
 * member who reacted twice with the same emoji counts once, which is what the index
 * already guarantees and what a naive message count would get wrong on a re-sync.
 */
@Composable
private fun CordnReactionChips(
    reactions: Map<String, Set<HexKey>>,
    me: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
    onReact: (String) -> Unit,
) {
    val chips =
        remember(reactions, me) {
            reactions
                .map { (emoji, who) -> ReactionChip(emoji, who.size, me in who) }
                .sortedByDescending { it.count }
        }

    var showWho by remember { mutableStateOf(false) }

    if (showWho) {
        CordnReactionDetailSheet(
            reactions = reactions,
            accountViewModel = accountViewModel,
            nav = nav,
            onDismiss = { showWho = false },
        )
    }

    ChatChipFlowRow {
        chips.forEach { chip ->
            ReactionChipView(
                chip = chip,
                onClick = { onReact(chip.type) },
                // Long press opens who reacted, as the DM strip does.
                onLongClick = { showWho = true },
            )
        }
    }
}

/**
 * Who reacted, and with what.
 *
 * The fold keys reactions by emoji to a *set* of senders, so this is the whole truth
 * the room holds about them — there is no separate receipt to open, and no count that
 * could disagree with the list under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CordnReactionDetailSheet(
    reactions: Map<String, Set<HexKey>>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    // Most-reacted first, matching the order of the chips that opened this.
    val groups = remember(reactions) { reactions.entries.sortedByDescending { it.value.size } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                text = stringRes(R.string.cordn_reactions_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            groups.forEach { (emoji, who) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = who.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                who.forEach { pubKey ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UserPicture(
                            userHex = pubKey,
                            size = Size20dp,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                        Text(
                            text = observeUserNameByHex(pubKey, accountViewModel),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The message a reply is answering, drawn above it.
 *
 * A one-line "Replying to: <first 60 characters>" told you a reply existed without ever
 * saying who it answered, which in a group is most of what you need. This names the
 * author in their own colour and is tappable, so a reply is a way back to what it
 * answers rather than a dead label.
 */
@Composable
internal fun CordnQuotedMessage(
    parent: CordnDeliveredMessage,
    annotations: CordnAnnotationIndex,
    accountViewModel: AccountViewModel,
    onClick: (() -> Unit)? = null,
) {
    val isLightTheme = MaterialTheme.colorScheme.isLight
    val authorColor =
        remember(parent.envelope.pubKey, isLightTheme) {
            authorNameColorFor(parent.envelope.pubKey, isLightTheme)
        }

    val body =
        if (annotations.isDeleted(parent.envelope.id)) {
            stringRes(R.string.cordn_message_deleted)
        } else {
            annotations.contentOf(parent.envelope.id).orEmpty()
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(QuoteShape)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = QUOTE_TINT_ALPHA))
                .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The accent bar reads as "this is quoted" at a glance, and takes the author's
        // own colour so a run of replies to different people stays distinguishable.
        Box(Modifier.width(3.dp).fillMaxHeight().background(authorColor))

        Column(Modifier.padding(top = 6.dp, bottom = 6.dp, end = 8.dp)) {
            Text(
                text = observeUserNameByHex(parent.envelope.pubKey, accountViewModel),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = authorColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val QuoteShape = RoundedCornerShape(6.dp)

/** How strongly a quoted message is lifted off the bubble it sits in. */
private const val QUOTE_TINT_ALPHA = 0.06f

/**
 * The line where the messages you have already read end.
 *
 * Drawn from a cursor snapshotted when the room opened: `markRead()` runs on open, so a
 * divider read from the live cursor would vanish the moment it became useful.
 */
@Composable
internal fun UnreadDivider() {
    ChatDivisor(stringRes(R.string.cordn_chat_unread_divider), MaterialTheme.colorScheme.primary)
}

/**
 * The bubble's bottom-corner footer: the markers this message carries, then the time on
 * the last bubble of a burst. Until this existed a cordn room showed no per-message
 * time at all — only the day separator — so nothing said when anything was said.
 */
@Composable
private fun CordnMessageFooter(
    createdAt: Long,
    isEdited: Boolean,
    isPinned: Boolean,
    showTime: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isPinned) {
            Icon(
                symbol = MaterialSymbols.PushPin,
                contentDescription = stringRes(R.string.cordn_action_pin),
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(StdHorzSpacer)
        }

        if (isEdited) {
            Text(
                text = stringRes(R.string.cordn_message_edited),
                fontSize = Font12SP,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
            )
            Spacer(StdHorzSpacer)
        }

        if (showTime) {
            ToggleableTimeAgoText(
                timestamp = createdAt,
                style = TimeAgoStyle.Short,
                color = MaterialTheme.colorScheme.placeholderText,
                fontSize = Font12SP,
            )
        }
    }
}

/**
 * The long-press surface, built from the same [ActionTile]s as the DM sheet.
 *
 * It opens with the account's own reaction palette, because the strip below a bubble now
 * appears only once a message *has* a reaction — without the palette, double-tap would
 * be the only way to leave the first one, and an undiscoverable gesture is not an
 * affordance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CordnMessageActionSheet(
    isMine: Boolean,
    isPinned: Boolean,
    isLive: Boolean,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onReact: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isLive) {
                val choices by accountViewModel.reactionChoicesFlow().collectAsStateWithLifecycle()
                val palette = choices.ifEmpty { listOf(DEFAULT_REACTION) }

                // Never drawn as "mine": cordn has no un-react, so this row adds a
                // reaction rather than toggling one, and highlighting a choice you
                // already used would promise a second tap that takes it back.
                ChatChipFlowRow {
                    palette.forEach { emoji ->
                        ReactionChipView(
                            chip = ReactionChip(emoji, 1, false),
                            onClick = {
                                onReact(emoji)
                                onDismiss()
                            },
                            onLongClick = {},
                        )
                    }
                }

                SectionDivider()
            }

            TileRow {
                if (isLive) {
                    ActionTile(MaterialSymbols.AutoMirrored.Chat, stringRes(R.string.cordn_action_reply)) {
                        onReply()
                        onDismiss()
                    }
                }

                // Pinning is any member's (spec/01.md §5.1), so it is offered on every
                // message rather than only on your own.
                ActionTile(
                    MaterialSymbols.PushPin,
                    stringRes(if (isPinned) R.string.cordn_action_unpin else R.string.cordn_action_pin),
                ) {
                    onTogglePin()
                    onDismiss()
                }

                // Edit and delete are author-only, and the manager refuses them for
                // anyone else. Hiding them here is the same rule, stated where it stops
                // being a surprise.
                if (isMine && isLive) {
                    ActionTile(MaterialSymbols.Edit, stringRes(R.string.cordn_action_edit)) {
                        onEdit()
                        onDismiss()
                    }
                    ActionTile(MaterialSymbols.Delete, stringRes(R.string.cordn_action_delete), isDestructive = true) {
                        onDelete()
                        onDismiss()
                    }
                }
            }
        }
    }
}

/**
 * Where [message] sits inside a run of consecutive bubbles by the same sender, in the
 * shared [ChatGroupPosition] vocabulary — so a cordn burst gets the same squared-off
 * corners and tightened spacing as a DM burst, not just a hidden author line.
 *
 * The feed is reverse-laid-out: [newer] is the message rendered below, [older] above.
 */
internal fun cordnGroupPositionFor(
    newer: CordnDeliveredMessage?,
    message: CordnDeliveredMessage,
    older: CordnDeliveredMessage?,
): ChatGroupPosition {
    val connectedAbove = older != null && groupsWith(message, older)
    val connectedBelow = newer != null && groupsWith(newer, message)

    return when {
        connectedAbove && connectedBelow -> ChatGroupPosition.MIDDLE
        connectedAbove -> ChatGroupPosition.BOTTOM
        connectedBelow -> ChatGroupPosition.TOP
        else -> ChatGroupPosition.SINGLE
    }
}

/**
 * Whether [newer] continues the run [older] started — same sender, close in time, same
 * day. Time as well as sender, because a reply hours later to your own last message is a
 * new thought, and joining it to the run reads as though the conversation never paused.
 *
 * The window is the shared [CHAT_GROUP_WINDOW_SECONDS], so a cordn burst and a DM burst
 * break in the same place.
 */
private fun groupsWith(
    newer: CordnDeliveredMessage,
    older: CordnDeliveredMessage,
): Boolean {
    if (newer.envelope.pubKey != older.envelope.pubKey) return false
    if (abs(newer.envelope.createdAt - older.envelope.createdAt) > CHAT_GROUP_WINDOW_SECONDS) return false
    // A day separator between the two breaks the run, exactly as a date divisor does in
    // the DM feed.
    return newer.sameDayAs(older)
}

/** Whether both fall on the same local calendar day. A null [older] is a new day. */
internal fun CordnDeliveredMessage.sameDayAs(older: CordnDeliveredMessage?): Boolean {
    if (older == null) return false
    return localDayOf(envelope.createdAt) == localDayOf(older.envelope.createdAt)
}

private fun localDayOf(epochSeconds: Long): LocalDate = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * The day a run of messages belongs to.
 *
 * Without one, a conversation is an undivided column and "yesterday evening" and "this
 * morning" sit flush against each other.
 */
@Composable
internal fun DaySeparator(
    createdAt: Long,
    today: LocalDate,
) {
    val day = remember(createdAt) { localDayOf(createdAt) }

    val label =
        when (day) {
            today -> stringRes(Res.string.today)
            today.minusDays(1) -> stringRes(R.string.cordn_chat_yesterday)
            // Year included only when it is not this one: printing 2026 on every
            // divider all year is noise.
            else ->
                day.format(
                    DateTimeFormatter.ofPattern(
                        if (day.year == today.year) "d MMM" else "d MMM yyyy",
                    ),
                )
        }

    ChatDivisor(label)
}

/** Upper bound on how long a stale "Today" can survive a clock correction. */
private const val TODAY_POLL_MS = 60_000L

/**
 * Today, as a value that stops being today when it stops being today.
 *
 * [DaySeparator] used to hold `remember { LocalDate.now() }` of its own. That is a
 * snapshot of the wall clock with nothing to invalidate it, and each separator keeps a
 * separate one, so they can disagree: a separator composed before midnight goes on
 * saying "Today" while the one for the new day says it too. Seen on the tablet — one
 * room, two "Today" dividers.
 *
 * Polling rather than a single sleep to the next midnight, because a device clock does
 * not only advance: it is corrected, and the tablet this was found on jumped nine hours
 * in one step. Re-assigning an equal [LocalDate] is not a change, so a quiet minute
 * costs no recomposition.
 */
@Composable
internal fun rememberToday(): LocalDate {
    val zone = remember { ZoneId.systemDefault() }
    return produceState(LocalDate.now(zone), zone) {
        while (true) {
            val now = ZonedDateTime.now(zone)
            val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)).toMillis()
            delay(untilMidnight.coerceIn(1_000L, TODAY_POLL_MS))
            value = LocalDate.now(zone)
        }
    }.value
}
