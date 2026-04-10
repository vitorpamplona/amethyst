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
package com.vitorpamplona.amethyst.ios.ui.reactions

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

/** Standard unicode emoji reactions. */
val STANDARD_REACTIONS =
    listOf(
        "❤️",
        "🤙",
        "👍",
        "👎",
        "😂",
        "🔥",
        "🫡",
        "🤔",
        "😢",
        "💜",
        "🚀",
        "👀",
        "🫂",
        "💯",
        "⚡",
    )

/**
 * Data class representing a reaction with its count for display.
 */
data class ReactionDisplay(
    val content: String,
    val customEmoji: EmojiUrlTag? = null,
    val count: Int = 1,
)

/**
 * Displays reaction counts under a note, including custom emoji.
 * Custom emoji reactions show the emoji image instead of text.
 */
@Composable
fun ReactionCountsRow(
    reactions: List<ReactionDisplay>,
    modifier: Modifier = Modifier,
    onReactionClick: ((ReactionDisplay) -> Unit)? = null,
) {
    if (reactions.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(reactions) { reaction ->
            ReactionChip(
                reaction = reaction,
                onClick =
                    if (onReactionClick != null) {
                        { onReactionClick(reaction) }
                    } else {
                        null
                    },
            )
        }
    }
}

/**
 * Individual reaction chip showing emoji + count.
 */
@Composable
fun ReactionChip(
    reaction: ReactionDisplay,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (reaction.customEmoji != null) {
            // Custom emoji: show the image
            AsyncImage(
                model = reaction.customEmoji.url,
                contentDescription = ":${reaction.customEmoji.code}:",
                modifier = Modifier.size(18.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            // Standard unicode emoji or "+" for likes
            val displayText =
                when (reaction.content) {
                    "+", "" -> "❤️"
                    "-" -> "👎"
                    else -> reaction.content
                }
            Text(
                text = displayText,
                fontSize = 14.sp,
            )
        }

        if (reaction.count > 1) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = reaction.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Emoji picker dialog for reactions.
 * Shows standard unicode emoji and custom emoji from followed packs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiReactionPickerDialog(
    customEmojis: List<EmojiUrlTag> = emptyList(),
    onSelect: (String, EmojiUrlTag?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = standard, 1 = custom

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "React",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab selector if custom emoji available
                if (customEmojis.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TabChip(
                            label = "Standard",
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                        )
                        TabChip(
                            label = "Custom",
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                when (selectedTab) {
                    0 -> {
                        // Standard emoji grid
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            STANDARD_REACTIONS.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 28.sp,
                                    textAlign = TextAlign.Center,
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onSelect(emoji, null) }
                                            .padding(6.dp),
                                )
                            }
                        }
                    }

                    1 -> {
                        // Custom emoji grid
                        if (customEmojis.isEmpty()) {
                            Text(
                                "No custom emoji packs found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                customEmojis.forEach { emoji ->
                                    AsyncImage(
                                        model = emoji.url,
                                        contentDescription = ":${emoji.code}:",
                                        modifier =
                                            Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { onSelect(emoji.toContentEncode(), emoji) }
                                                .padding(4.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color =
            if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
