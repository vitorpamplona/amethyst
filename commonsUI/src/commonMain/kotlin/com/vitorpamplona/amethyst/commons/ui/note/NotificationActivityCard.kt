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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.model.CombinedZap
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip71Video.selectVideoTrack
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatPreviewLabels
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatPreviewText
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.notifications.MultiSetCard
import com.vitorpamplona.amethyst.commons.notifications.NotificationContent
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserPicture
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_preview_note
import com.vitorpamplona.amethyst.commons.resources.chat_preview_photo
import com.vitorpamplona.amethyst.commons.resources.chat_preview_video
import com.vitorpamplona.amethyst.commons.resources.notification_your_profile
import com.vitorpamplona.amethyst.commons.resources.nutzap
import com.vitorpamplona.amethyst.commons.resources.profile_image
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.customEmojiUrlOrNull
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.fastFirstOrNull
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.claimedSatsTotal
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.plus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/*
 * The notification card for activity on something the user made: reactions, boosts, zaps and
 * nutzaps on a note, or zaps on their profile. The user already knows what they wrote, so the card
 * leads with who acted and shrinks the target to one line:
 *
 *   [faces, each with a badge for what it did] +N • 2h        42.1k   [thumb]
 *   the zap comment, when there is one
 *   one muted line of the note
 *
 * One card serves a single reaction and a group of fifty: the face pile fits as many faces as the
 * width allows and counts the rest.
 */

/** What one person did, drawn as the badge on their face. */
enum class ActivityKind {
    ZAP,
    NUTZAP,
    BOOST,
    REACTION,
}

/** One face in the pile: who, what, and for a reaction which one ([reaction] is its content key). */
@Immutable
class ActivityActor(
    val user: User?,
    val kind: ActivityKind,
    val reaction: String? = null,
)

/**
 * A comment that came with a zap or nutzap, who sent it, and the event it came in ([id] keys its
 * translation and expanded state; [tags] resolve its custom emoji and mentions).
 */
@Immutable
class ActivityComment(
    val user: User?,
    val text: String,
    val id: String,
    val tags: ImmutableListOfLists<String>,
)

private val FaceSize = 28.dp
private val FaceGap = 6.dp
private val BadgeSize = 16.dp
private val BadgeGlyphSize = 11.dp
private val ThumbnailSize = 44.dp
private val ThumbnailShape = RoundedCornerShape(6.dp)
private val ColumnWidth = 55.dp
private val KindIconSize = 20.dp
private val ZapTotalIconSize = 15.dp

// The width of a note header's ⋮ button, so a card without one keeps its time aligned.
private val MenuWidth = 24.dp

/**
 * The faces of [card], one per person: zappers first, then boosters, then reactions, newest first
 * within each. A person who zapped and liked shows once, with the zap.
 */
fun MultiSetCard.activityActors(zaps: List<ZapAmountCommentNotification>): ImmutableList<ActivityActor> {
    val seen = HashSet<String>()
    val out = ArrayList<ActivityActor>()

    fun add(
        user: User?,
        kind: ActivityKind,
        reaction: String? = null,
    ) {
        if (user == null || seen.add(user.pubkeyHex)) out.add(ActivityActor(user, kind, reaction))
    }

    zaps
        .sortedByDescending { it.zapNote?.createdAt() ?: 0L }
        .forEach { add(it.user, ActivityKind.ZAP) }
    nutzapEvents.forEach { add(it.author, ActivityKind.NUTZAP) }
    boostEvents.forEach { add(it.author, ActivityKind.BOOST) }

    val reactionOf = HashMap<Note, String>()
    likeEventsByType.forEach { (reaction, notes) -> notes.forEach { reactionOf[it] = reaction } }
    likeEvents
        .sortedByDescending { it.createdAt() ?: 0L }
        .forEach { add(it.author, ActivityKind.REACTION, reactionOf[it] ?: "+") }

    return out.toImmutableList()
}

/** The sats in [zaps] plus [nutzaps], or null when the card has none. */
fun zapTotal(
    zaps: List<CombinedZap>,
    nutzaps: List<Note>,
): String? {
    if (zaps.isEmpty() && nutzaps.isEmpty()) return null
    var total = BigDecimal(0)
    zaps.forEach { zap -> (zap.response.event as? ZapReceiptEvent)?.amount?.let { total += it } }
    nutzaps.forEach { note -> (note.event as? NutzapEvent)?.claimedSatsTotal()?.let { total += BigDecimal(it) } }
    return showAmount(total).ifEmpty { null }
}

/** The newest non-blank comment among [zaps] and [nutzaps]. */
fun latestZapComment(
    zaps: List<ZapAmountCommentNotification>,
    nutzaps: List<Note>,
): ActivityComment? {
    val zap =
        zaps
            .filter { !it.comment.isNullOrBlank() }
            .maxByOrNull { it.zapNote?.createdAt() ?: 0L }
    val nutzap =
        nutzaps
            .filter { !it.event?.content.isNullOrBlank() }
            .maxByOrNull { it.createdAt() ?: 0L }

    val zapText = zap?.comment?.trim()
    val nutzapText = nutzap?.event?.content?.trim()
    val zapTime = zap?.zapNote?.createdAt() ?: -1L
    val nutzapTime = nutzap?.createdAt() ?: -1L

    return when {
        // A zap's comment sits in its (possibly private, decrypted) request, whose tags the
        // receipt doesn't carry: like the zap galleries before, it renders without them.
        zapText != null && (nutzapText == null || zapTime >= nutzapTime) -> {
            ActivityComment(zap.user, zapText, zap.zapNote?.idHex ?: zapText, EmptyTagList)
        }

        nutzapText != null -> {
            ActivityComment(nutzap.author, nutzapText, nutzap.idHex, nutzap.event?.tags?.toImmutableListOfLists() ?: EmptyTagList)
        }
        else -> null
    }
}

/**
 * The card. [target] is the last line (usually [NotificationNoteLine]); [thumbnail] the picture
 * beside the text, when the target has one. [kindIcon] fills the author column of the Indented
 * layout; the full-width layouts have no column, and the badges on the faces carry the same
 * information.
 *
 * The first row ends like a note's header, with the time and then [moreOptions] (the ⋮ menu), so
 * the times of activity cards and of the replies between them line up on the right edge. A card
 * without a note to act on passes nothing and keeps the menu's width empty for the same alignment.
 */
@Composable
fun NotificationActivityCard(
    actors: ImmutableList<ActivityActor>,
    createdAt: Long,
    zapTotal: String?,
    comment: ActivityComment?,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
    kindIcon: @Composable (Modifier) -> Unit,
    moreOptions: @Composable () -> Unit = { Spacer(Modifier.width(MenuWidth)) },
    thumbnail: (@Composable () -> Unit)? = null,
    target: @Composable () -> Unit,
) {
    val fullWidth = LocalDisplaySettings.current.fullWidthNotes

    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (!fullWidth) {
            Box(
                modifier = Modifier.width(ColumnWidth).padding(top = 4.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                kindIcon(Modifier.size(KindIconSize))
            }
            Spacer(Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    NotificationFacePile(
                        actors = actors,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (zapTotal != null) {
                        ZappedIcon(Modifier.padding(start = 10.dp).size(ZapTotalIconSize))
                        Text(
                            text = zapTotal,
                            color = MaterialTheme.colorScheme.bitcoinColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                }
                Box(Modifier.padding(start = 8.dp)) { TimeAgo(createdAt, TimeAgoStyle.DottedTight) }
                moreOptions()
            }

            Row {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (comment != null) {
                        ActivityCommentLine(comment, showName = actors.size > 1, backgroundColor, accountViewModel, nav)
                    }
                    target()
                }

                if (thumbnail != null) {
                    Spacer(Modifier.width(12.dp))
                    thumbnail()
                }
            }
        }
    }
}

/**
 * The zap comment is someone else's words, so it renders like a note's text: translated when it is
 * in another language, rich (emoji, mentions, links) and expandable when long.
 */
@Composable
private fun ActivityCommentLine(
    comment: ActivityComment,
    showName: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row {
        if (showName && comment.user != null) {
            val name by observeUserName(comment.user, accountViewModel)
            Text(
                text = name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 6.dp).weight(1f, fill = false),
            )
        }
        TranslatableRichTextViewer(
            content = comment.text,
            canPreview = false,
            quotesLeft = 1,
            modifier = Modifier.weight(1f),
            tags = comment.tags,
            backgroundColor = backgroundColor,
            id = comment.id,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

/**
 * As many faces of [actors] as fit the width, then "+N" for the rest. The count is worked out and
 * drawn by the layout itself, so the pile adapts to a phone, a deck column or a wide window without
 * subcomposing.
 */
@Composable
fun NotificationFacePile(
    actors: ImmutableList<ActivityActor>,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val composed = minOf(actors.size, MAX_COMPOSED_FACES)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.placeholderText)
    val badgeBase = MaterialTheme.colorScheme.background
    // Read once for the whole pile rather than once per face; animated avatars follow the setting.
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()

    // What the draw pass puts after the faces. Int and Float states skip equal writes, so a
    // re-measure that changes nothing doesn't invalidate the drawing.
    val hiddenCount = remember { mutableIntStateOf(0) }
    val labelX = remember { mutableFloatStateOf(0f) }

    Layout(
        content = {
            for (i in 0 until composed) {
                NotificationFace(actors[i], backgroundColor, badgeBase, autoPlayGif, accountViewModel)
            }
        },
        modifier =
            modifier.drawBehind {
                val hidden = hiddenCount.intValue
                if (hidden > 0) {
                    // The measurer caches the layout, so this is a lookup on every frame but the first.
                    val label = measurer.measure("+$hidden", labelStyle)
                    drawText(label, topLeft = Offset(labelX.floatValue, (size.height - label.size.height) / 2f))
                }
            },
    ) { measurables, constraints ->
        val face = FaceSize.roundToPx()
        val gap = FaceGap.roundToPx()
        val total = actors.size

        fun facesWidth(count: Int) = count * face + (count - 1).coerceAtLeast(0) * gap

        // Faces that fit with no label, else as many as fit beside the widest label the count can
        // need ("+total"): one text measurement per pass instead of one per face dropped.
        var shown = composed
        var labelWidth = 0
        if (composed < total || facesWidth(composed) > constraints.maxWidth) {
            labelWidth = measurer.measure("+$total", labelStyle).size.width
            shown = ((constraints.maxWidth - labelWidth) / (face + gap)).coerceIn(minOf(1, composed), composed)
        }

        val hidden = total - shown
        val labelFits = hidden > 0 && facesWidth(shown) + gap + labelWidth <= constraints.maxWidth
        val width = (facesWidth(shown) + if (labelFits) gap + labelWidth else 0).coerceAtMost(constraints.maxWidth)

        val placeables = arrayOfNulls<Placeable>(shown)
        for (i in 0 until shown) placeables[i] = measurables[i].measure(Constraints.fixed(face, face))

        // Too narrow for even one face and a count: show the face alone rather than overlap the time.
        hiddenCount.intValue = if (labelFits) hidden else 0
        labelX.floatValue =
            if (layoutDirection == LayoutDirection.Rtl) {
                (width - facesWidth(shown) - gap - measurer.measure("+$hidden", labelStyle).size.width).toFloat()
            } else {
                (facesWidth(shown) + gap).toFloat()
            }

        layout(width, face) {
            for (i in 0 until shown) placeables[i]?.placeRelative(i * (face + gap), 0)
        }
    }
}

// Enough to fill a wide window; the rest is only ever counted.
private const val MAX_COMPOSED_FACES = 16

@Composable
private fun NotificationFace(
    actor: ActivityActor,
    backgroundColor: MutableState<Color>,
    badgeBase: Color,
    autoPlayGif: Boolean,
    accountViewModel: AccountViewModel,
) {
    Box(Modifier.size(FaceSize)) {
        val user = actor.user
        if (user != null) {
            val picture by observeUserPicture(user, accountViewModel)
            RobohashFallbackAsyncImage(
                robot = user.pubkeyHex,
                model = picture,
                contentDescription = stringRes(Res.string.profile_image),
                modifier = Modifier.size(FaceSize).clip(CircleShape),
                contentScale = ContentScale.Crop,
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
                autoPlayGif = autoPlayGif,
            )
        } else {
            DisplayBlankAuthor(FaceSize, accountViewModel = accountViewModel)
        }

        // The badge sits on the corner, ringed in the card's own color so it reads as cut out of
        // the face. Read at draw time: the unread tint fades without recomposing the pile.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(4.dp, 4.dp)
                    .size(BadgeSize)
                    .drawBehind { drawCircle(backgroundColor.value.compositeOver(badgeBase)) },
            contentAlignment = Alignment.Center,
        ) {
            ActivityGlyph(actor.kind, actor.reaction, BadgeGlyphSize)
        }
    }
}

/** The mark for one kind of activity, at [size]: a bolt, the cashu nut, the boost arrows, a reaction. */
@Composable
fun ActivityGlyph(
    kind: ActivityKind,
    reaction: String?,
    size: Dp,
) {
    when (kind) {
        ActivityKind.ZAP -> {
            ZappedIcon(Modifier.size(size))
        }

        ActivityKind.NUTZAP -> {
            Icon(
                imageVector = CustomHashTagIcons.Cashu,
                contentDescription = stringRes(Res.string.nutzap),
                modifier = Modifier.size(size),
                tint = BitcoinOrange,
            )
        }

        ActivityKind.BOOST -> {
            RepostedIcon(Modifier.size(size))
        }

        ActivityKind.REACTION -> {
            ReactionGlyph(reaction ?: "+", size)
        }
    }
}

/** A reaction's content as a glyph: the heart for "+", an image for a custom emoji, else the emoji. */
@Composable
fun ReactionGlyph(
    reaction: String,
    size: Dp,
) {
    val emojiUrl = remember(reaction) { reaction.customEmojiUrlOrNull() }
    when {
        emojiUrl != null -> {
            AsyncImage(
                model = emojiUrl,
                contentDescription = reaction,
                modifier = Modifier.size(size),
            )
        }

        reaction == "+" -> {
            LikedIcon(Modifier.size(size))
        }

        else -> {
            // A coded emoji keeps its secret in invisible selectors after the face, so it draws as-is.
            val text = if (reaction == "-") "\uD83D\uDC4E" else reaction
            val fontSize = with(LocalDensity.current) { (size * 0.9f).toSp() }
            Text(
                text = text,
                fontSize = fontSize,
                lineHeight = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** The Indented column's icon for [card]: its most valuable kind, or its one reaction if that is all. */
@Composable
fun MultiSetKindIcon(
    card: MultiSetCard,
    modifier: Modifier,
) {
    when {
        card.zapEvents.isNotEmpty() -> {
            ZappedIcon(modifier)
        }

        card.nutzapEvents.isNotEmpty() -> {
            Icon(
                imageVector = CustomHashTagIcons.Cashu,
                contentDescription = stringRes(Res.string.nutzap),
                modifier = modifier,
                tint = BitcoinOrange,
            )
        }

        card.boostEvents.isNotEmpty() -> {
            RepostedIcon(modifier)
        }

        card.likeEventsByType.size == 1 -> {
            ReactionGlyph(card.likeEventsByType.keys.first(), KindIconSize)
        }

        else -> {
            LikedIcon(modifier)
        }
    }
}

/**
 * One muted line of [note]: its title or subject when it has one, else its text as a preview line
 * says it (links and mentions tidied, media named). [leadingIcon] marks why it is there (a reply
 * arrow, a quote mark).
 */
@Composable
fun NotificationNoteLine(
    note: Note,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val event by observeNoteEvent<Event>(note, accountViewModel)
    val labels =
        ChatPreviewLabels(
            photo = stringRes(Res.string.chat_preview_photo),
            video = stringRes(Res.string.chat_preview_video),
            note = stringRes(Res.string.chat_preview_note),
        )
    // The decrypted body, so a reaction to one of the user's DMs names the message, not its ciphertext.
    LoadDecryptedContentOrNull(note, accountViewModel) { body ->
        val text = remember(event, body, labels) { event?.let { notificationLineText(it, body, labels) } ?: "" }
        NotificationMutedLine(text, modifier, leadingIcon)
    }
}

/** The second line of a profile-zap card. */
@Composable
fun NotificationProfileLine(modifier: Modifier = Modifier) {
    NotificationMutedLine(stringRes(Res.string.notification_your_profile), modifier)
}

@Composable
private fun NotificationMutedLine(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.placeholderText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun notificationLineText(
    event: Event,
    decrypted: String?,
    labels: ChatPreviewLabels,
): String {
    val title =
        event.tags
            .fastFirstOrNull { it.size > 1 && (it[0] == "title" || it[0] == "subject") && it[1].isNotBlank() }
            ?.get(1)
    // Until an encrypted body decrypts (or when it can't), show nothing rather than the ciphertext.
    val body = title ?: decrypted ?: ""
    val nameOf = { hex: HexKey -> LocalCache.getUserIfExists(hex)?.toBestDisplayName() }
    // Media links drop out when there are words to show: "Nostr is dead they say", not "... Photo".
    // A post that is only a picture or a video still gets named.
    val wordsOnly = ChatPreviewText.tidy(body, labels.copy(photo = "", video = ""), nameOf)
    return wordsOnly.ifBlank { ChatPreviewText.tidy(body, labels, nameOf).ifBlank { labels.photo } }
}

/**
 * A small square of [note]'s first picture, or nothing when it has none, the user turned image
 * loading off, or the note is marked sensitive.
 */
@Composable
fun rememberNotificationThumbnail(
    note: Note,
    accountViewModel: AccountViewModel,
): (@Composable () -> Unit)? {
    val event by observeNoteEvent<Event>(note, accountViewModel)
    val url =
        remember(event) {
            event?.let {
                if (it.isSensitiveOrNSFW()) {
                    null
                } else {
                    // A video only has a picture when it declares a poster frame; mediaImageUrl
                    // would fall back to the video file itself, which an image loader can't draw.
                    val picture = if (it is VideoEvent) it.selectVideoTrack()?.image?.firstOrNull() else NotificationContent.mediaImageUrl(it)
                    picture ?: NotificationContent.firstImageUrl(it.content)
                }
            }
        }
    if (url == null || !accountViewModel.settings.showImages()) return null

    return {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(ThumbnailSize)
                    .clip(ThumbnailShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
