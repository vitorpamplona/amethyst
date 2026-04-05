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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AddMemberDialog(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    var memberInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isAdding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Member") },
        text = {
            Column {
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
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        modifier = Modifier.padding(top = 8.dp),
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isAdding = true
                    statusMessage = "Looking up KeyPackage..."
                    scope.launch(Dispatchers.IO) {
                        try {
                            val pubkey = resolvePubkey(memberInput)
                            if (pubkey == null) {
                                statusMessage = "Error: Invalid public key format"
                                isAdding = false
                                return@launch
                            }
                            statusMessage = "Fetching KeyPackage for ${pubkey.take(8)}..."
                            // TODO: Fetch KeyPackage from relays and call addMarmotGroupMember
                            // For now, show that the flow would proceed here
                            statusMessage = "Error: KeyPackage fetch not yet implemented. " +
                                "The member's KeyPackage (kind:30443) must be fetched from relays."
                            isAdding = false
                        } catch (e: Exception) {
                            statusMessage = "Error: ${e.message}"
                            isAdding = false
                        }
                    }
                },
                enabled = !isAdding && memberInput.isNotBlank(),
            ) {
                Text(if (isAdding) "Adding..." else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun resolvePubkey(input: String): HexKey? {
    val trimmed = input.trim()

    // Try hex pubkey
    if (trimmed.length == 64 && trimmed.all { it in '0'..'9' || it in 'a'..'f' }) {
        return trimmed
    }

    // Try bech32 (npub / nprofile)
    val entity =
        Nip19Parser.uriToRoute("nostr:$trimmed")?.entity
            ?: Nip19Parser.uriToRoute(trimmed)?.entity
    return when (entity) {
        is NPub -> entity.hex
        is NProfile -> entity.hex
        else -> null
    }
}
