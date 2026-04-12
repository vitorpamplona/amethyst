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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list

import com.vitorpamplona.amethyst.commons.model.nip51Lists.peopleList.PeopleList
import com.vitorpamplona.amethyst.commons.viewmodels.FollowPackOperations
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.flow.StateFlow

typealias FollowPackViewModel = com.vitorpamplona.amethyst.commons.viewmodels.FollowPackListViewModel

fun FollowPackViewModel.init(accountViewModel: AccountViewModel) {
    init(
        object : FollowPackOperations {
            override fun listFlow(): StateFlow<List<PeopleList>> = accountViewModel.account.followLists.uiListFlow

            override fun launchSigner(block: suspend () -> Unit) {
                accountViewModel.launchSigner { block() }
            }

            override suspend fun cloneFollowSet(
                currentFollowPack: PeopleList,
                customCloneName: String?,
                customCloneDescription: String?,
            ) {
                accountViewModel.account.followLists.cloneFollowSet(
                    currentFollowPack = currentFollowPack,
                    customCloneName = customCloneName,
                    customCloneDescription = customCloneDescription,
                    account = accountViewModel.account,
                )
            }

            override suspend fun deleteFollowSet(identifierTag: String) {
                accountViewModel.account.followLists.deleteFollowSet(
                    identifierTag = identifierTag,
                    account = accountViewModel.account,
                )
            }
        },
    )
}
