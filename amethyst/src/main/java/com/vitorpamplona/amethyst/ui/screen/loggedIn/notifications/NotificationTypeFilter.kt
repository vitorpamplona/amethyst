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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.notifications.Card
import com.vitorpamplona.amethyst.ui.note.CommentIcon
import com.vitorpamplona.amethyst.ui.note.LikedIcon
import com.vitorpamplona.amethyst.ui.note.RepostedIcon
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.RoyalBlue

enum class NotificationTypeFilter(
    val labelRes: Int,
) {
    ALL(R.string.notification_type_all),
    REPLIES(R.string.notification_type_replies),
    REACTIONS(R.string.notification_type_reactions),
    ZAPS(R.string.notification_type_zaps),
    REPOSTS(R.string.notification_type_reposts),
}

fun Card.matchesNotificationTypeFilter(filter: NotificationTypeFilter): Boolean =
    when (filter) {
        NotificationTypeFilter.ALL -> true
        NotificationTypeFilter.ZAPS ->
            this is ZapUserSetCard ||
                (this is MultiSetCard && zapEvents.isNotEmpty())
        NotificationTypeFilter.REPLIES ->
            this is NoteCard || this is MessageSetCard
        NotificationTypeFilter.REACTIONS ->
            this is MultiSetCard && likeEvents.isNotEmpty()
        NotificationTypeFilter.REPOSTS ->
            this is MultiSetCard && boostEvents.isNotEmpty()
    }

private val iconSize = Modifier.size(18.dp)

@Composable
fun NotificationTypeFilterRow(
    selectedFilter: NotificationTypeFilter,
    onFilterSelected: (NotificationTypeFilter) -> Unit,
) {
    val filters = remember { NotificationTypeFilter.entries }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(stringRes(filter.labelRes)) },
                leadingIcon = { NotificationTypeIcon(filter) },
            )
        }
    }
}

@Composable
private fun NotificationTypeIcon(filter: NotificationTypeFilter) {
    when (filter) {
        NotificationTypeFilter.ALL -> {}
        NotificationTypeFilter.REPLIES -> CommentIcon(iconSize, RoyalBlue)
        NotificationTypeFilter.REACTIONS -> LikedIcon(iconSize)
        NotificationTypeFilter.ZAPS ->
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                modifier = iconSize,
                tint = BitcoinOrange,
            )
        NotificationTypeFilter.REPOSTS -> RepostedIcon(modifier = iconSize)
    }
}
