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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.note.DisplayDraft
import com.vitorpamplona.amethyst.ui.note.DisplayExpiration
import com.vitorpamplona.amethyst.ui.note.PinnedMark
import com.vitorpamplona.amethyst.ui.note.PrivateRumorMark
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Design-system preview for the note-header first row: how the two marker
 * tiers ([HeaderPill] and [QuietMark]) compose next to the username and
 * timestamp as the line fills up. Uses the same layout contract as
 * `FirstUserInfoRow` (weight-1 username, `Arrangement.spacedBy(5.dp)`),
 * with static stand-ins only for the data-loading markers (location, OTS,
 * hashtag).
 */
@Preview(widthDp = 400)
@Composable
fun NoteHeaderFirstRowDensityPreview() {
    ThemeComparisonColumn {
        Column(Modifier.padding(vertical = 6.dp)) {
            // Bare minimum: username + time + options
            PreviewHeaderRow {}

            // One quiet mark: repost
            PreviewHeaderRow { BoostedMark() }

            // Quiet text marks: edited + draft
            PreviewHeaderRow {
                QuietMark(text = stringRes(R.string.edited), onClick = {})
                DisplayDraft()
            }

            // Quiet icon marks: private rumor + pinned
            PreviewHeaderRow {
                PrivateRumorMark()
                PinnedMark()
            }

            // Soft link + one pill
            PreviewHeaderRow {
                PreviewHashtag()
                DisplayPoW(24)
            }

            // Pill cluster: location + PoW + OTS
            PreviewHeaderRow {
                PreviewLocationPill()
                DisplayPoW(28)
                PreviewOtsPill()
            }

            // Expiring note with a parent to jump to
            PreviewHeaderRow {
                DisplayExpiration(TimeUtils.now() + 7200)
                PreviewJumpToParent()
            }

            // Kitchen sink under a long username: everything competes for space
            PreviewHeaderRow(username = "A user with a very long display name") {
                PreviewHashtag()
                QuietMark(text = stringRes(R.string.edited), onClick = {})
                DisplayDraft()
                PrivateRumorMark()
                PinnedMark()
                PreviewLocationPill()
                DisplayPoW(32)
                PreviewOtsPill()
                DisplayExpiration(TimeUtils.now() + 7200)
                PreviewJumpToParent()
            }
        }
    }
}

/** Same layout contract as `FirstUserInfoRow`: weight-1 bold username, 5dp gaps, time + options at the end. */
@Composable
private fun PreviewHeaderRow(
    username: String = "Vitor",
    markers: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = username,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        markers()

        TimeAgo(TimeUtils.now() - 300, style = TimeAgoStyle.Short, fontSize = Font12SP)

        VerticalDotsIcon()
    }
}

/** Static stand-in for `DisplayFollowingHashtagsInPost` (which needs account state). */
@Composable
private fun PreviewHashtag() {
    Text(
        text = "#nostr",
        color = MaterialTheme.colorScheme.lessImportantLink,
        fontSize = Font12SP,
        maxLines = 1,
    )
}

/** Static stand-in for `DisplayLocation` (which resolves the geohash to a city name). */
@Composable
private fun PreviewLocationPill() {
    HeaderPill(
        symbol = MaterialSymbols.LocationOn,
        text = "Belo Horizonte",
        onClick = {},
    )
}

/** Static stand-in for `DisplayOts` (which loads and verifies the attestation). */
@Composable
private fun PreviewOtsPill() {
    HeaderPill(
        symbol = MaterialSymbols.CheckCircle,
        text = stringRes(R.string.existed_since, "2y"),
        onClick = {},
    )
}

/** Static stand-in for `JumpToParentReplyButton` (which needs the parent note). */
@Composable
private fun PreviewJumpToParent() {
    Icon(
        symbol = MaterialSymbols.KeyboardArrowUp,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.placeholderText,
    )
}
