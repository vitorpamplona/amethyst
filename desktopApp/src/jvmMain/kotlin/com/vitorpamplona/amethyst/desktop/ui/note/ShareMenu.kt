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
package com.vitorpamplona.amethyst.desktop.ui.note

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.desktop.model.LocalDesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.ui.LocalSnackbarHost
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class ShareMenuState {
    var expanded by mutableStateOf(false)
        private set

    fun open() {
        expanded = true
    }

    fun dismiss() {
        expanded = false
    }
}

@Composable
fun rememberShareMenuState(): ShareMenuState = remember { ShareMenuState() }

/** A single note-menu entry — one source of truth shared by the ⋮ overflow and the right-click menu. */
class NoteMenuAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The canonical note action list. Rendered identically by [ShareMenu] (⋮ dropdown)
 * and the feed's right-click context menu, so both offer the same items.
 * Moderation actions (mute/report) are added only for other authors on a
 * writeable account; each action shows a snackbar via [LocalSnackbarHost].
 */
@Composable
fun rememberNoteMenuActions(
    event: Event,
    relayManager: DesktopRelayConnectionManager,
    onReportClick: () -> Unit,
): List<NoteMenuAction> {
    val account = LocalDesktopIAccount.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val canModerate = account != null && account.isWriteable() && event.pubKey != account.pubKey
    return remember(event, canModerate) {
        buildList {
            add(NoteMenuAction("Copy Text") { copyToClipboard(event.content) })
            add(NoteMenuAction("Copy Note ID") { copyToClipboard("nostr:${NNote.create(event.id)}") })
            add(
                NoteMenuAction("Copy Event Link") {
                    val relays = relayManager.connectedRelays.value.take(3)
                    copyToClipboard("nostr:${NEvent.create(event.id, event.pubKey, event.kind, relays)}")
                },
            )
            add(NoteMenuAction("Copy Raw JSON") { copyToClipboard(event.toJson()) })
            add(
                NoteMenuAction("Copy Web Link") {
                    copyToClipboard("https://njump.me/${NEvent.create(event.id, event.pubKey, event.kind, emptyList())}")
                },
            )
            add(
                NoteMenuAction("Broadcast") {
                    relayManager.broadcastToAll(event)
                    scope.launch { snackbar?.showSnackbar("Broadcast to relays") }
                },
            )
            if (canModerate) {
                add(
                    NoteMenuAction("Mute user") {
                        scope.launch {
                            try {
                                account.hideUser(event.pubKey)
                                snackbar?.showSnackbar("Muted user")
                            } catch (e: Exception) {
                                snackbar?.showSnackbar("Mute failed: ${e.message}")
                            }
                        }
                    },
                )
                add(NoteMenuAction("Report…", onReportClick))
            }
        }
    }
}

/** The ⋮ overflow dropdown. Renders [rememberNoteMenuActions] + the report dialog. */
@Composable
fun ShareMenu(
    state: ShareMenuState,
    event: Event,
    relayManager: DesktopRelayConnectionManager,
) {
    var showReportDialog by remember { mutableStateOf(false) }
    val actions = rememberNoteMenuActions(event, relayManager) { showReportDialog = true }

    DropdownMenu(
        expanded = state.expanded,
        onDismissRequest = { state.dismiss() },
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                onClick = {
                    action.onClick()
                    state.dismiss()
                },
            )
        }
    }

    if (showReportDialog) {
        NoteReportDialog(event) { showReportDialog = false }
    }
}

/** Report dialog wired to the account + snackbar, reused by every note surface. */
@Composable
fun NoteReportDialog(
    event: Event,
    onDismiss: () -> Unit,
) {
    val account = LocalDesktopIAccount.current ?: return
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    ReportNoteDialog(
        onDismiss = onDismiss,
        onReport = { type, comment ->
            scope.launch {
                try {
                    account.reportEvent(event, type, comment)
                    snackbar?.showSnackbar("Report sent")
                } catch (e: Exception) {
                    snackbar?.showSnackbar("Report failed: ${e.message}")
                }
            }
        },
        onBlockAndReport = { type, comment ->
            scope.launch {
                try {
                    account.reportEvent(event, type, comment)
                    account.hideUser(event.pubKey)
                    snackbar?.showSnackbar("Reported & muted")
                } catch (e: Exception) {
                    snackbar?.showSnackbar("Report failed: ${e.message}")
                }
            }
        },
    )
}

private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}
