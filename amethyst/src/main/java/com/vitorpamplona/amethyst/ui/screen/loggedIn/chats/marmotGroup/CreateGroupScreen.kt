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

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun CreateGroupScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var groupName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var showKeyPackageRelayDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun proceedWithCreate() {
        isCreating = true
        scope.launch(Dispatchers.IO) {
            try {
                val nostrGroupId = RandomInstance.bytes(32).toHexKey()
                accountViewModel.createMarmotGroup(nostrGroupId)
                // Always commit an initial metadata extension so that
                // (a) the name (if any) is persisted in MLS extensions
                //     and survives app restarts,
                // (b) the inviter's outbox relays land in the group
                //     metadata so every member ends up with the same
                //     canonical relay set for kind:445 — without this,
                //     invitees would never receive the group's messages.
                // Both are handled inside `updateMarmotGroupMetadata`.
                accountViewModel.updateMarmotGroupMetadata(
                    nostrGroupId = nostrGroupId,
                    name = groupName.trim(),
                    description = "",
                )
                nav.nav(Route.MarmotGroupChat(nostrGroupId))
            } catch (e: Exception) {
                isCreating = false
                launch(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            "Failed to create group: ${e.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CreatingTopBar(
                onCancel = { nav.popBack() },
                onPost = {
                    if (!accountViewModel.hasKeyPackageRelayList()) {
                        showKeyPackageRelayDialog = true
                    } else {
                        proceedWithCreate()
                    }
                },
                isActive = { !isCreating },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Create Marmot Group",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )

            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(
                "A new MLS group will be created. You can add members after.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showKeyPackageRelayDialog) {
        MissingKeyPackageRelayListDialog(
            onConfirm = {
                showKeyPackageRelayDialog = false
                scope.launch(Dispatchers.IO) {
                    accountViewModel.saveKeyPackageRelayListFromOutbox()
                }
                proceedWithCreate()
            },
            onDismiss = {
                showKeyPackageRelayDialog = false
                proceedWithCreate()
            },
            onCancel = {
                showKeyPackageRelayDialog = false
            },
        )
    }
}

@Composable
private fun MissingKeyPackageRelayListDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("KeyPackage Relays not set") },
        text = {
            Text(
                "You don't have a KeyPackage Relay List yet (MIP-00). " +
                    "This list tells other people where your KeyPackage is " +
                    "published so they can invite you to group chats.\n\n" +
                    "Use your current outbox relays for this?",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Use outbox relays")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip for now")
            }
        },
    )
}
