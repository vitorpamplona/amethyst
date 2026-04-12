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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.nip51Lists.peopleList.PeopleList
import kotlinx.coroutines.flow.StateFlow

/**
 * Callback interface for PeopleListListViewModel to interact with
 * platform-specific account operations.
 */
interface PeopleListOperations {
    fun listFlow(): StateFlow<List<PeopleList>>

    fun launchSigner(block: suspend () -> Unit)

    suspend fun cloneFollowSet(
        currentPeopleList: PeopleList,
        customCloneName: String?,
        customCloneDescription: String?,
    )

    suspend fun deleteFollowSet(identifierTag: String)
}

@Stable
class PeopleListListViewModel : ViewModel() {
    private lateinit var operations: PeopleListOperations

    fun init(operations: PeopleListOperations) {
        this.operations = operations
    }

    fun listFlow() = operations.listFlow()

    fun cloneItem(
        followSet: PeopleList,
        customName: String?,
        customDescription: String?,
    ) {
        operations.launchSigner {
            operations.cloneFollowSet(
                currentPeopleList = followSet,
                customCloneName = customName,
                customCloneDescription = customDescription,
            )
        }
    }

    fun deleteItem(followSet: PeopleList) {
        operations.launchSigner {
            operations.deleteFollowSet(identifierTag = followSet.identifierTag)
        }
    }
}
