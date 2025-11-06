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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.memberEdit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer

@Composable
fun PeopleListAndUserView(
    userToAddOrRemove: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val followSetsState by accountViewModel.account.peopleLists.uiListFlow
        .collectAsStateWithLifecycle()

    if (followSetsState.isEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = stringRes(R.string.follow_set_empty_dialog_msg))
            Spacer(modifier = StdVertSpacer)
        }
    } else {
        val userName by observeUserName(userToAddOrRemove, accountViewModel)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(followSetsState, key = { _, item -> item.identifierTag }) { _, list ->
                PeopleListAndUserItem(
                    modifier = Modifier.fillMaxWidth(),
                    listHeader = list.title,
                    userName = userName,
                    userIsPrivateMember = list.privateMembers.contains(userToAddOrRemove),
                    userIsPublicMember = list.publicMembers.contains(userToAddOrRemove),
                    onRemoveUser = {
                        accountViewModel.runIOCatching {
                            accountViewModel.account.peopleLists.removeUserFromSet(
                                userToAddOrRemove,
                                isPrivate = list.privateMembers.contains(userToAddOrRemove),
                                list.identifierTag,
                                accountViewModel.account,
                            )
                        }
                    },
                    privateMemberSize = list.privateMembers.size,
                    publicMemberSize = list.publicMembers.size,
                    onAddUserToList = { userShouldBePrivate ->
                        accountViewModel.runIOCatching {
                            accountViewModel.account.peopleLists.addUserToSet(
                                userToAddOrRemove,
                                list.identifierTag,
                                userShouldBePrivate,
                                accountViewModel.account,
                            )
                        }
                    },
                )
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}
