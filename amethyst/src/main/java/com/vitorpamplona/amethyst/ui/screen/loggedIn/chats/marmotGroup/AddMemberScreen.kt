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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05State
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AddMemberScreen(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var searchInput by remember { mutableStateOf("") }
    val selectedUsers = remember { mutableStateListOf<User>() }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val userSuggestions =
        remember {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }

    DisposableEffect(Unit) {
        onDispose { userSuggestions.reset() }
    }

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = com.vitorpamplona.amethyst.R.string.add,
                onCancel = { nav.popBack() },
                onPost = {
                    if (selectedUsers.isEmpty()) {
                        statusMessage = "No users selected"
                        isError = true
                        return@ActionTopBar
                    }
                    isAdding = true
                    isError = false
                    val usersToAdd = selectedUsers.toList()
                    scope.launch(Dispatchers.IO) {
                        var successCount = 0
                        val failures = mutableListOf<Pair<User, String>>()
                        for ((index, user) in usersToAdd.withIndex()) {
                            statusMessage =
                                "Adding ${user.toBestDisplayName()} (${index + 1}/${usersToAdd.size})..."
                            isError = false
                            try {
                                val result =
                                    accountViewModel.addMarmotGroupMember(
                                        nostrGroupId,
                                        user.pubkeyHex,
                                    )
                                if (result.startsWith("Success")) {
                                    successCount++
                                } else {
                                    failures.add(user to result.removePrefix("Error: "))
                                }
                            } catch (e: Exception) {
                                failures.add(user to (e.message ?: "unknown error"))
                            }
                        }

                        if (failures.isEmpty()) {
                            nav.popBack()
                        } else {
                            statusMessage =
                                buildString {
                                    if (successCount > 0) {
                                        append("Added $successCount. ")
                                    }
                                    append("Failed: ")
                                    append(
                                        failures.joinToString("; ") { (user, reason) ->
                                            "${user.toBestDisplayName()} ($reason)"
                                        },
                                    )
                                }
                            isError = true
                            // Keep failed users in the list for retry, drop successful ones
                            val failedUsers = failures.map { it.first }.toSet()
                            selectedUsers.clear()
                            selectedUsers.addAll(failedUsers)
                            isAdding = false
                        }
                    }
                },
                isActive = { !isAdding && selectedUsers.isNotEmpty() },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
        ) {
            // Selected users list
            if (selectedUsers.isNotEmpty()) {
                selectedUsers.toList().forEachIndexed { index, user ->
                    SelectedUserRow(
                        user = user,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        enabled = !isAdding,
                        onClear = {
                            selectedUsers.remove(user)
                            statusMessage = null
                            isError = false
                        },
                    )
                    if (index < selectedUsers.lastIndex) {
                        HorizontalDivider()
                    }
                }
                HorizontalDivider()
            }

            // Search field
            OutlinedTextField(
                value = searchInput,
                onValueChange = { newValue ->
                    searchInput = newValue
                    if (!isError) statusMessage = null
                    if (newValue.length > 2) {
                        userSuggestions.processCurrentWord(newValue)
                    } else {
                        userSuggestions.reset()
                    }
                },
                label = { Text("Search users") },
                placeholder = { Text("Name, npub, or NIP-05") },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                singleLine = true,
                enabled = !isAdding,
            )

            if (statusMessage != null) {
                Text(
                    text = statusMessage!!,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // User suggestion list
            if (!isAdding && searchInput.length > 2) {
                ShowUserSuggestionList(
                    userSuggestions = userSuggestions,
                    onSelect = { user ->
                        if (selectedUsers.none { it.pubkeyHex == user.pubkeyHex }) {
                            selectedUsers.add(user)
                        }
                        searchInput = ""
                        userSuggestions.reset()
                    },
                    accountViewModel = accountViewModel,
                    modifier = SuggestionListDefaultHeightPage,
                    onEmpty = {
                        Text(
                            "They must have published a KeyPackage (kind:30443) to be added.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectedUserRow(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserPicture(
            userHex = user.pubkeyHex,
            size = 40.dp,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        ) {
            Text(
                text = user.toBestDisplayName(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            UserSecondaryLine(user)
        }
        TextButton(onClick = onClear, enabled = enabled) {
            Text("Remove")
        }
    }
}

@Composable
private fun UserSecondaryLine(user: User) {
    val nip05StateMetadata by user.nip05State().flow.collectAsStateWithLifecycle()

    val text =
        when (val state = nip05StateMetadata) {
            is Nip05State.Exists -> {
                val name = state.nip05.name
                if (name == "_") state.nip05.domain else "$name@${state.nip05.domain}"
            }

            else -> {
                user.pubkeyDisplayHex()
            }
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
