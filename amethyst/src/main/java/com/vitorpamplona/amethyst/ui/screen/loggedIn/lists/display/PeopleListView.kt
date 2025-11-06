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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.display

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserComposeNoAction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.HalfHalfHorzModifier
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import kotlinx.collections.immutable.ImmutableList

@Composable
fun PeopleListView(
    memberList: ImmutableList<User>,
    modifier: Modifier = Modifier,
    onDeleteUser: (User) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier,
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(memberList, key = { _, item -> "u" + item.pubkeyHex }) { _, item ->
            PeopleListItem(
                modifier = Modifier.animateContentSize(),
                user = item,
                accountViewModel = accountViewModel,
                nav = nav,
                onDeleteUser = onDeleteUser,
            )
        }
    }
}

@Composable
fun PeopleListItem(
    modifier: Modifier = Modifier,
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDeleteUser: (User) -> Unit,
) {
    Column(
        modifier = modifier,
    ) {
        Row(HalfHalfHorzModifier) {
            UserComposeNoAction(
                user,
                modifier = HalfPadding.weight(1f, fill = false),
                accountViewModel = accountViewModel,
                nav = nav,
            )
            IconButton(
                onClick = {
                    onDeleteUser(user)
                },
                modifier =
                    HalfPadding
                        .align(Alignment.CenterVertically)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(percent = 80),
                        ),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider(thickness = DividerThickness)
    }
}
