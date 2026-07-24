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
package com.vitorpamplona.amethyst.desktop.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.model.LocalDesktopIAccount
import com.vitorpamplona.amethyst.desktop.ui.deck.LocalDesktopCache
import kotlinx.coroutines.launch

/**
 * Content-Filters entry for NIP-36 sensitive content + NIP-51 mute-list
 * management (blocked users / hidden words / muted threads). Reads/writes the
 * logged-in [com.vitorpamplona.amethyst.desktop.model.DesktopIAccount] via
 * [LocalDesktopIAccount]; the lists are driven by its live hidden-users flow, so
 * removing an entry publishes the updated kind-10000 and un-hides immediately.
 */
@Composable
fun ModerationSettingsSection(modifier: Modifier = Modifier) {
    val account = LocalDesktopIAccount.current ?: return
    val cache = LocalDesktopCache.current
    val scope = rememberCoroutineScope()

    val hidden by account.hiddenUsers.collectAsState()
    val showSensitive by account.showSensitiveContentSetting.collectAsState()
    val writeable = account.isWriteable()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Moderation & Safety",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))

        // NIP-36 sensitive content
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = showSensitive == true, onCheckedChange = { account.setAlwaysShowSensitive(it) })
            Spacer(Modifier.width(8.dp))
            Text("Always show sensitive content", style = MaterialTheme.typography.bodyMedium)
        }

        // Muted users
        if (hidden.hiddenUsers.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Muted users (${hidden.hiddenUsers.size})", style = MaterialTheme.typography.labelLarge)
            hidden.hiddenUsers.sorted().forEach { hex ->
                val name = remember(hex) { cache?.getUserIfExists(hex)?.toBestDisplayName() ?: hex.take(12) }
                EntryRow(label = name, actionLabel = "Unmute", enabled = writeable) {
                    scope.launch { account.showUser(hex) }
                }
            }
        }

        // Hidden words
        Spacer(Modifier.height(12.dp))
        Text("Hidden words", style = MaterialTheme.typography.labelLarge)
        var newWord by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newWord,
                onValueChange = { newWord = it },
                label = { Text("Add word to hide") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = writeable && newWord.isNotBlank(),
                onClick = {
                    val w = newWord.trim()
                    if (w.isNotEmpty()) {
                        scope.launch { account.hideWord(w) }
                        newWord = ""
                    }
                },
            ) { Text("Add") }
        }
        hidden.hiddenWords.sorted().forEach { word ->
            EntryRow(label = word, actionLabel = "Remove", enabled = writeable) {
                scope.launch { account.showWord(word) }
            }
        }

        // Muted threads
        if (hidden.mutedThreads.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Muted threads (${hidden.mutedThreads.size})", style = MaterialTheme.typography.labelLarge)
            hidden.mutedThreads.sorted().forEach { id ->
                EntryRow(label = "${id.take(12)}…", actionLabel = "Unmute", enabled = writeable) {
                    scope.launch { account.showThread(id) }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    label: String,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (enabled) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
