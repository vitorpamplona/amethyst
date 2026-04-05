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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AddMemberScreen(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var memberInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isAdding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = com.vitorpamplona.amethyst.R.string.add,
                onCancel = { nav.popBack() },
                onPost = {
                    isAdding = true
                    val pubkey = resolvePubkey(memberInput)
                    if (pubkey == null) {
                        statusMessage = "Error: Invalid public key format"
                        return@ActionTopBar
                    }
                    isAdding = true
                    statusMessage = "Fetching KeyPackage for ${pubkey.take(8)}..."
                    scope.launch(Dispatchers.IO) {
                        try {
                            val result =
                                accountViewModel.addMarmotGroupMember(nostrGroupId, pubkey)
                            statusMessage = result
                            if (result.startsWith("Success")) {
                                nav.popBack()
                            } else {
                                isAdding = false
                            }
                        } catch (e: Exception) {
                            statusMessage = "Error: ${e.message}"
                            isAdding = false
                        }
                    }
                },
                isActive = { !isAdding && memberInput.isNotBlank() },
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
                text = "Add Member",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )

            OutlinedTextField(
                value = memberInput,
                onValueChange = {
                    memberInput = it
                    statusMessage = null
                },
                label = { Text("npub or hex pubkey") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(
                "Enter the member's npub or hex public key. " +
                    "They must have published a KeyPackage (kind:30443) to be added.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (statusMessage != null) {
                Text(
                    text = statusMessage!!,
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (statusMessage!!.startsWith("Error")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }
        }
    }
}

private fun resolvePubkey(input: String): HexKey? {
    val trimmed = input.trim()

    if (trimmed.length == 64 && trimmed.all { it in '0'..'9' || it in 'a'..'f' }) {
        return trimmed
    }

    val entity =
        Nip19Parser.uriToRoute("nostr:$trimmed")?.entity
            ?: Nip19Parser.uriToRoute(trimmed)?.entity
    return when (entity) {
        is NPub -> entity.hex
        is NProfile -> entity.hex
        else -> null
    }
}
