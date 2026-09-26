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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * An embedded page's `alert` / `confirm` / `prompt` / `beforeunload`, drawn by the main process over the tab
 * (the provider's WebView has no window to draw one). Titled with the page's host ("example.com says") so a
 * page can't pass its dialog off as Amethyst's own, and offering "Block dialogs from this page" from the
 * page's second dialog on — the same rules as the full-screen browser.
 */
@Composable
fun EmbeddedJsDialogView(
    dialog: EmbeddedJsDialog,
    onAnswer: (confirmed: Boolean, text: String?, block: Boolean) -> Unit,
) {
    var text by remember(dialog.id) { mutableStateOf(dialog.defaultValue) }
    val leave = dialog.type == EmbeddedJsDialog.Type.BEFORE_UNLOAD
    val host = dialog.url?.let { BrowserChrome.originOf(it) }?.let(BrowserChrome::displayHost)
    AlertDialog(
        onDismissRequest = { onAnswer(false, null, false) },
        title = {
            Text(
                when {
                    leave -> stringRes(CommonsR.string.browser_js_leave_title)
                    host != null -> stringRes(CommonsR.string.browser_js_dialog_title, host)
                    else -> stringRes(CommonsR.string.browser_js_dialog_title_generic)
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // The page's own beforeunload text is ignored, as in every current browser (it was abused).
                Text(if (leave) stringRes(CommonsR.string.browser_js_leave_message) else dialog.message)
                if (dialog.type == EmbeddedJsDialog.Type.PROMPT) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(true, if (dialog.type == EmbeddedJsDialog.Type.PROMPT) text else null, false) }) {
                Text(if (leave) stringRes(CommonsR.string.browser_js_leave) else stringRes(android.R.string.ok))
            }
        },
        dismissButton = {
            Row {
                if (dialog.offerBlock) {
                    // A blocked page may not keep the user on it, so blocking a beforeunload means leaving.
                    TextButton(onClick = { onAnswer(leave, null, true) }) { Text(stringRes(CommonsR.string.browser_js_dialog_block)) }
                }
                if (dialog.type != EmbeddedJsDialog.Type.ALERT) {
                    TextButton(onClick = { onAnswer(false, null, false) }) { Text(stringRes(android.R.string.cancel)) }
                }
            }
        },
    )
}

/**
 * Chrome's permission bubble for an embedded page: "<host> wants to — use your camera — Block / Allow".
 * Dismissing it denies this once without remembering anything.
 */
@Composable
fun EmbeddedPermissionPrompt(
    origin: String,
    permissions: Set<BrowserSitePermission>,
    onAnswer: (allow: Boolean?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        title = { Text(stringRes(CommonsR.string.browser_permission_title, BrowserChrome.displayHost(origin))) },
        text = {
            Column {
                permissions.forEach { permission ->
                    Text(
                        "• " +
                            stringRes(
                                when (permission) {
                                    BrowserSitePermission.CAMERA -> CommonsR.string.browser_permission_camera
                                    BrowserSitePermission.MICROPHONE -> CommonsR.string.browser_permission_microphone
                                    BrowserSitePermission.LOCATION -> CommonsR.string.browser_permission_location
                                },
                            ),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringRes(CommonsR.string.browser_permission_allow)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringRes(CommonsR.string.browser_permission_block)) } },
    )
}

/** Chrome's page-info sheet for an embedded page: connection, Tor, certificate; site settings; clear data. */
@Composable
fun EmbeddedPageInfoDialog(
    host: String,
    info: String?,
    onPermissions: (() -> Unit)?,
    onClearData: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(host) },
        text = { Text(info ?: "…") },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringRes(android.R.string.ok)) } },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onClearData()
                    onDismiss()
                }) { Text(stringRes(CommonsR.string.browser_page_info_clear_data)) }
                if (onPermissions != null) {
                    TextButton(onClick = {
                        onPermissions()
                        onDismiss()
                    }) { Text(stringRes(CommonsR.string.browser_page_info_permissions)) }
                }
            }
        },
    )
}
