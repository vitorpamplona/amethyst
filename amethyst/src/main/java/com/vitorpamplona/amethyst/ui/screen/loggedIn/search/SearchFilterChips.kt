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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.search.DateUtils
import com.vitorpamplona.amethyst.commons.search.SearchQuery
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import kotlinx.collections.immutable.toImmutableList

data class SearchableKind(
    val kind: Int,
    val nameResId: Int,
)

val SEARCHABLE_KINDS =
    listOf(
        SearchableKind(TextNoteEvent.KIND, R.string.kind_notes),
        SearchableKind(LongTextNoteEvent.KIND, R.string.kind_blogs),
        SearchableKind(PictureEvent.KIND, R.string.kind_pictures),
        SearchableKind(VideoNormalEvent.KIND, R.string.kind_video),
        SearchableKind(VideoShortEvent.KIND, R.string.kind_shorts),
        SearchableKind(RepostEvent.KIND, R.string.kind_reposts),
        SearchableKind(ReactionEvent.KIND, R.string.kind_reactions),
        SearchableKind(LnZapEvent.KIND, R.string.kind_zaps),
        SearchableKind(CommentEvent.KIND, R.string.kind_comments),
        SearchableKind(HighlightEvent.KIND, R.string.kind_highlights),
        SearchableKind(WikiNoteEvent.KIND, R.string.kind_wiki),
        SearchableKind(ClassifiedsEvent.KIND, R.string.kind_classifieds),
        SearchableKind(LiveActivitiesEvent.KIND, R.string.kind_live_streams),
        SearchableKind(ChannelCreateEvent.KIND, R.string.kind_channel_definition),
        SearchableKind(CommunityDefinitionEvent.KIND, R.string.kind_community_def),
        SearchableKind(MetadataEvent.KIND, R.string.kind_profile),
    )

@Composable
fun SearchFilterChipsRow(
    searchBarViewModel: SearchBarViewModel,
    modifier: Modifier = Modifier,
) {
    val query by searchBarViewModel.parsedQuery.collectAsStateWithLifecycle()

    var showFromDialog by remember { mutableStateOf(false) }
    var showToDialog by remember { mutableStateOf(false) }
    var showSinceDialog by remember { mutableStateOf(false) }
    var showUntilDialog by remember { mutableStateOf(false) }
    var showKindDialog by remember { mutableStateOf(false) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // From user chip
        val fromLabel = buildFromLabel(query)
        FilterChip(
            selected = query.authors.isNotEmpty() || query.authorNames.isNotEmpty(),
            onClick = { showFromDialog = true },
            label = { Text(fromLabel) },
            trailingIcon =
                if (query.authors.isNotEmpty() || query.authorNames.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                searchBarViewModel.updateFromParsedQuery { q ->
                                    q.copy(
                                        authors = emptyList<String>().toImmutableList(),
                                        authorNames = emptyList<String>().toImmutableList(),
                                    )
                                }
                            },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringRes(R.string.clear), modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    null
                },
        )

        // To user chip
        val toLabel = buildToLabel(query)
        FilterChip(
            selected = query.recipients.isNotEmpty() || query.recipientNames.isNotEmpty(),
            onClick = { showToDialog = true },
            label = { Text(toLabel) },
            trailingIcon =
                if (query.recipients.isNotEmpty() || query.recipientNames.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                searchBarViewModel.updateFromParsedQuery { q ->
                                    q.copy(
                                        recipients = emptyList<String>().toImmutableList(),
                                        recipientNames = emptyList<String>().toImmutableList(),
                                    )
                                }
                            },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringRes(R.string.clear), modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    null
                },
        )

        // Since date chip
        val sinceLabel =
            if (query.since != null) {
                "${stringRes(R.string.search_filter_since)}: ${DateUtils.timestampToDate(query.since!!)}"
            } else {
                stringRes(R.string.search_filter_since)
            }
        FilterChip(
            selected = query.since != null,
            onClick = { showSinceDialog = true },
            label = { Text(sinceLabel) },
            trailingIcon =
                if (query.since != null) {
                    {
                        IconButton(
                            onClick = {
                                searchBarViewModel.updateFromParsedQuery { q -> q.copy(since = null) }
                            },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringRes(R.string.clear), modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    null
                },
        )

        // Until date chip
        val untilLabel =
            if (query.until != null) {
                "${stringRes(R.string.search_filter_until)}: ${DateUtils.timestampToDate(query.until!!)}"
            } else {
                stringRes(R.string.search_filter_until)
            }
        FilterChip(
            selected = query.until != null,
            onClick = { showUntilDialog = true },
            label = { Text(untilLabel) },
            trailingIcon =
                if (query.until != null) {
                    {
                        IconButton(
                            onClick = {
                                searchBarViewModel.updateFromParsedQuery { q -> q.copy(until = null) }
                            },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringRes(R.string.clear), modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    null
                },
        )

        // Kind chip
        val kindLabel = buildKindLabel(query)
        FilterChip(
            selected = query.kinds.isNotEmpty(),
            onClick = { showKindDialog = true },
            label = { Text(kindLabel) },
            trailingIcon =
                if (query.kinds.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                searchBarViewModel.updateFromParsedQuery { q ->
                                    q.copy(kinds = emptyList<Int>().toImmutableList())
                                }
                            },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringRes(R.string.clear), modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    null
                },
        )
    }

    // Dialogs
    if (showFromDialog) {
        UserPickerDialog(
            title = stringRes(R.string.search_filter_from),
            currentValues = query.authorNames.toList() + query.authors.toList(),
            onDismiss = { showFromDialog = false },
            onConfirm = { username ->
                searchBarViewModel.updateFromParsedQuery { q ->
                    q.copy(authorNames = (q.authorNames + username).distinct().toImmutableList())
                }
                showFromDialog = false
            },
        )
    }

    if (showToDialog) {
        UserPickerDialog(
            title = stringRes(R.string.search_filter_to),
            currentValues = query.recipientNames.toList() + query.recipients.toList(),
            onDismiss = { showToDialog = false },
            onConfirm = { username ->
                searchBarViewModel.updateFromParsedQuery { q ->
                    q.copy(recipientNames = (q.recipientNames + username).distinct().toImmutableList())
                }
                showToDialog = false
            },
        )
    }

    if (showSinceDialog) {
        SearchDatePickerDialog(
            title = stringRes(R.string.search_filter_since),
            initialTimestamp = query.since,
            onDismiss = { showSinceDialog = false },
            onConfirm = { timestamp ->
                searchBarViewModel.updateFromParsedQuery { q -> q.copy(since = timestamp) }
                showSinceDialog = false
            },
        )
    }

    if (showUntilDialog) {
        SearchDatePickerDialog(
            title = stringRes(R.string.search_filter_until),
            initialTimestamp = query.until,
            onDismiss = { showUntilDialog = false },
            onConfirm = { timestamp ->
                searchBarViewModel.updateFromParsedQuery { q -> q.copy(until = timestamp) }
                showUntilDialog = false
            },
        )
    }

    if (showKindDialog) {
        KindPickerDialog(
            selectedKinds = query.kinds.toList(),
            onDismiss = { showKindDialog = false },
            onConfirm = { kinds ->
                searchBarViewModel.updateFromParsedQuery { q ->
                    q.copy(kinds = kinds.toImmutableList())
                }
                showKindDialog = false
            },
        )
    }
}

@Composable
private fun buildFromLabel(query: SearchQuery): String {
    val base = stringRes(R.string.search_filter_from)
    val names = query.authorNames.toList() + query.authors.map { it.take(8) + "..." }
    return if (names.isNotEmpty()) {
        "$base: ${names.first()}"
    } else {
        base
    }
}

@Composable
private fun buildToLabel(query: SearchQuery): String {
    val base = stringRes(R.string.search_filter_to)
    val names = query.recipientNames.toList() + query.recipients.map { it.take(8) + "..." }
    return if (names.isNotEmpty()) {
        "$base: ${names.first()}"
    } else {
        base
    }
}

@Composable
private fun buildKindLabel(query: SearchQuery): String {
    val base = stringRes(R.string.search_filter_kind)
    if (query.kinds.isEmpty()) return base
    val firstName =
        SEARCHABLE_KINDS.find { it.kind == query.kinds.first() }?.let {
            stringResource(it.nameResId)
        } ?: "k${query.kinds.first()}"
    return if (query.kinds.size == 1) {
        "$base: $firstName"
    } else {
        "$base: $firstName +${query.kinds.size - 1}"
    }
}

@Composable
fun UserPickerDialog(
    title: String,
    currentValues: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var inputValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    placeholder = { Text(stringRes(R.string.search_filter_username_or_npub)) },
                    singleLine = true,
                    shape = RoundedCornerShape(25.dp),
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                if (inputValue.isNotBlank()) {
                                    onConfirm(inputValue.trim())
                                }
                            },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (currentValues.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringRes(R.string.search_filter_selected_count, currentValues.size.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (inputValue.isNotBlank()) {
                        onConfirm(inputValue.trim())
                    }
                },
                enabled = inputValue.isNotBlank(),
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDatePickerDialog(
    title: String,
    initialTimestamp: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialTimestamp?.let { it * 1000 },
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onConfirm(millis / 1000)
                    }
                },
                enabled = datePickerState.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    title,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

@Composable
fun KindPickerDialog(
    selectedKinds: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit,
) {
    val currentSelection = remember { mutableStateListOf<Int>().apply { addAll(selectedKinds) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.search_filter_pick_kinds)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
            ) {
                items(SEARCHABLE_KINDS) { searchableKind ->
                    val isSelected = searchableKind.kind in currentSelection
                    ListItem(
                        headlineContent = {
                            Text(stringResource(searchableKind.nameResId))
                        },
                        leadingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                if (isSelected) {
                                    currentSelection.remove(searchableKind.kind)
                                } else {
                                    currentSelection.add(searchableKind.kind)
                                }
                            },
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentSelection.toList()) },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
