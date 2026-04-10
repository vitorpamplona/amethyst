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
package com.vitorpamplona.amethyst.ios.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.nip05.Nip05VerificationDisplay
import com.vitorpamplona.amethyst.ios.translation.TranslateButton
import com.vitorpamplona.amethyst.ios.ui.NoteDisplayData
import com.vitorpamplona.amethyst.ios.ui.media.RichNoteContent
import com.vitorpamplona.amethyst.ios.ui.reactions.ReactionCountsRow
import com.vitorpamplona.amethyst.ios.ui.reactions.ReactionDisplay
import com.vitorpamplona.amethyst.ios.util.toTimeAgo

/**
 * Reusable note card composable for the iOS app.
 */
@Composable
fun NoteCard(
    note: NoteDisplayData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAuthorClick: ((String) -> Unit)? = null,
    onReply: ((String) -> Unit)? = null,
    onBoost: ((String) -> Unit)? = null,
    onLike: ((String) -> Unit)? = null,
    onZap: ((String) -> Unit)? = null,
    onBookmark: ((String) -> Unit)? = null,
    isBookmarked: Boolean = false,
    user: User? = null,
    localCache: IosLocalCache? = null,
    reactions: List<ReactionDisplay> = emptyList(),
    onCopyNoteId: ((String) -> Unit)? = null,
    onCopyNoteText: ((String) -> Unit)? = null,
    onCopyAuthorNpub: ((String) -> Unit)? = null,
    onShare: ((String) -> Unit)? = null,
    onMuteUser: ((String) -> Unit)? = null,
    onReport: ((String, String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(12.dp)
                    .then(
                        if (onClick != null) Modifier.clickable { onClick() } else Modifier,
                    ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Author with avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        if (onAuthorClick != null) {
                            Modifier.clickable { onAuthorClick(note.pubKeyHex) }
                        } else {
                            Modifier
                        },
                ) {
                    UserAvatar(
                        userHex = note.pubKeyHex,
                        pictureUrl = note.profilePictureUrl,
                        size = 32.dp,
                        contentDescription = "Profile picture",
                    )

                    Spacer(Modifier.width(8.dp))

                    Column {
                        Text(
                            text = note.pubKeyDisplay.take(20) + if (note.pubKeyDisplay.length > 20) "..." else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        // NIP-05 verification badge
                        if (localCache != null) {
                            Nip05VerificationDisplay(
                                pubKeyHex = note.pubKeyHex,
                                localCache = localCache,
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Timestamp
                    Text(
                        text = note.createdAt.toTimeAgo(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Options overflow menu
                    NoteOptionsMenu(
                        note = note,
                        onCopyNoteId = onCopyNoteId,
                        onCopyNoteText = onCopyNoteText,
                        onCopyAuthorNpub = onCopyAuthorNpub,
                        onShare = onShare,
                        onMuteUser = onMuteUser,
                        onReport = onReport,
                        onDelete = onDelete,
                        onEdit = onEdit,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Content (rich: inline images, video thumbnails, URL previews)
            if (note.content.isNotBlank()) {
                RichNoteContent(content = note.content)

                // Translation button for foreign-language notes
                TranslateButton(noteContent = note.content)
            }

            Spacer(Modifier.height(8.dp))

            // Custom emoji reaction counts
            if (reactions.isNotEmpty()) {
                ReactionCountsRow(reactions = reactions)
                Spacer(Modifier.height(4.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            Spacer(Modifier.height(4.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Reply
                NoteActionButton(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    count = note.replyCount,
                    onClick =
                        if (onReply != null) {
                            { onReply(note.id) }
                        } else {
                            null
                        },
                )

                // Repost / Boost
                NoteActionButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Repost",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    count = note.boostCount,
                    onClick =
                        if (onBoost != null) {
                            { onBoost(note.id) }
                        } else {
                            null
                        },
                )

                // Like / React
                NoteActionButton(
                    icon = {
                        Icon(
                            imageVector = if (note.reactionCount > 0) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint =
                                if (note.reactionCount > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    count = note.reactionCount,
                    onClick =
                        if (onLike != null) {
                            { onLike(note.id) }
                        } else {
                            null
                        },
                )

                // Zap (with amount display)
                NoteActionButton(
                    icon = {
                        Text(
                            text = "⚡",
                            fontSize = 16.sp,
                        )
                    },
                    count = note.zapCount,
                    label = if (note.zapAmount.signum() > 0) formatZapAmount(note.zapAmount) else null,
                    onClick =
                        if (onZap != null) {
                            { onZap(note.id) }
                        } else {
                            null
                        },
                )

                // Bookmark
                NoteActionButton(
                    icon = {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint =
                                if (isBookmarked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    count = 0,
                    onClick =
                        if (onBookmark != null) {
                            { onBookmark(note.id) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
private fun NoteActionButton(
    icon: @Composable () -> Unit,
    count: Int,
    label: String? = null,
    onClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onClick?.invoke() },
            enabled = onClick != null,
            modifier = Modifier.size(32.dp),
        ) {
            icon()
        }
        if (label != null) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (count > 0) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Formats a zap amount in sats for display (e.g. 1000 -> "1k", 1500000 -> "1.5M").
 */
private fun formatZapAmount(amount: com.vitorpamplona.quartz.utils.BigDecimal): String {
    val sats = amount.toString().substringBefore(".").toLongOrNull() ?: 0L
    return when {
        sats >= 1_000_000 -> {
            val whole = sats / 1_000_000
            val frac = (sats % 1_000_000) / 100_000
            if (frac == 0L) "${whole}M" else "$whole.${frac}M"
        }

        sats >= 1_000 -> {
            val whole = sats / 1_000
            val frac = (sats % 1_000) / 100
            if (frac == 0L) "${whole}k" else "$whole.${frac}k"
        }

        else -> {
            sats.toString()
        }
    }
}
