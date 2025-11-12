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

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.nip51Lists.peopleList.PeopleList
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Stable
class FollowPackViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
    }

    fun listFlow() = accountViewModel.account.followLists.uiListFlow

    fun addItem(
        title: String,
        description: String?,
    ) {
        accountViewModel.launchSigner {
            accountViewModel.account.followLists.addFollowList(
                name = title,
                desc = description,
                account = accountViewModel.account,
            )
        }
    }

    fun openItem(dTag: String) = Route.MyFollowPackView(dTag)

    fun renameItem(
        followSet: PeopleList,
        newValue: String,
    ) {
        accountViewModel.launchSigner {
            accountViewModel.account.followLists.renameFollowList(
                newName = newValue,
                followPack = followSet,
                account = accountViewModel.account,
            )
        }
    }

    fun changeItemDescription(
        followSet: PeopleList,
        newDescription: String,
    ) {
        accountViewModel.launchSigner {
            accountViewModel.account.followLists.modifyFollowSetDescription(
                newDescription = newDescription,
                followPack = followSet,
                account = accountViewModel.account,
            )
        }
    }

    fun cloneItem(
        followSet: PeopleList,
        customName: String?,
        customDescription: String?,
    ) {
        accountViewModel.launchSigner {
            accountViewModel.account.followLists.cloneFollowSet(
                currentFollowPack = followSet,
                customCloneName = customName,
                customCloneDescription = customDescription,
                account = accountViewModel.account,
            )
        }
    }

    fun deleteItem(followSet: PeopleList) {
        accountViewModel.launchSigner {
            accountViewModel.account.followLists.deleteFollowSet(
                identifierTag = followSet.identifierTag,
                account = accountViewModel.account,
            )
        }
    }
}
