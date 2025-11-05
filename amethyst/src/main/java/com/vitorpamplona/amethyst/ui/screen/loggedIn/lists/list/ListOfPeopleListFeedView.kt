/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.nip51Lists.peopleList.PeopleList
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size30dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AllPeopleListFeedView(
    listFlow: StateFlow<List<PeopleList>>,
    onOpenItem: (String) -> Unit = {},
    onRenameItem: (targetSet: PeopleList, newName: String) -> Unit,
    onItemDescriptionChange: (peopleList: PeopleList, newDescription: String?) -> Unit,
    onItemClone: (peopleList: PeopleList, customName: String?, customDesc: String?) -> Unit,
    onDeleteItem: (peopleList: PeopleList) -> Unit,
) {
    val followSetFeedState by listFlow.collectAsStateWithLifecycle()

    if (followSetFeedState.isEmpty()) {
        AllPeopleListFeedEmpty(
            message = stringRes(R.string.follow_set_empty_feed_msg),
        )
    } else {
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = FeedPadding,
        ) {
            itemsIndexed(followSetFeedState, key = { _, item -> item.identifierTag }) { _, list ->
                PeopleListItem(
                    modifier = Modifier.fillMaxSize().animateItem(),
                    peopleList = list,
                    onClick = { onOpenItem(list.identifierTag) },
                    onRename = { onRenameItem(list, it) },
                    onDescriptionChange = { newDescription -> onItemDescriptionChange(list, newDescription) },
                    onClone = { cloneName, cloneDescription -> onItemClone(list, cloneName, cloneDescription) },
                    onDelete = { onDeleteItem(list) },
                )
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}

@Composable
fun AllPeopleListFeedEmpty(message: String = stringRes(R.string.feed_is_empty)) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Size30dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message)
        Spacer(modifier = StdVertSpacer)
    }
}
