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
package com.vitorpamplona.amethyst.desktop.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.crash_window_body
import com.vitorpamplona.amethyst.commons.resources.crash_window_send
import com.vitorpamplona.amethyst.commons.resources.crash_window_title
import com.vitorpamplona.amethyst.commons.ui.components.ErrorDetails
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.amethystDarkColors
import com.vitorpamplona.amethyst.commons.ui.theme.amethystLightColors
import com.vitorpamplona.quartz.utils.Log
import java.awt.Dialog
import java.awt.Window
import java.awt.event.WindowEvent

/**
 * What a window shows when its Compose UI throws (while composing, drawing or handling input), in
 * place of Compose Desktop's default: a bare system "Error" box holding only the exception's
 * message, often just a class name. Ours says what happened in plain words, keeps the stack behind
 * "Details" with a copy button, and offers what the phone offers after a crash: sending the report
 * to the developers in a DM.
 *
 * Close does what the default does: closes the window and rethrows, so the crash reporter keeps the
 * report for the next start. Send hands the report to [onSendReport], which rebuilds the window
 * (the crashed composition can't be reused) with the report's DM draft open; the report goes into
 * the draft, so it is not saved for the next start too.
 */
@OptIn(ExperimentalComposeUiApi::class)
class AmethystWindowExceptionHandlerFactory(
    private val onSendReport: (report: String) -> Unit,
) : WindowExceptionHandlerFactory {
    override fun exceptionHandler(window: Window): WindowExceptionHandler =
        WindowExceptionHandler { throwable ->
            Log.e("CrashWindow", "Uncaught exception in the window's UI", throwable)
            val report = throwable.stackTraceToString()
            val choice =
                try {
                    showCrashWindow(window.takeIf { it.isDisplayable }, throwable, report)
                } catch (e: Throwable) {
                    // The report matters more than the window: never let showing it lose the crash.
                    Log.e("CrashWindow", "Could not show the crash window", e)
                    CrashChoice.CLOSE
                }
            if (choice == CrashChoice.SEND) {
                onSendReport(report)
            } else {
                window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
                throw throwable
            }
        }
}

private enum class CrashChoice { SEND, CLOSE }

/** A fresh window (the crashed one can't be trusted to draw), modal until the user picks. */
@OptIn(ExperimentalComposeUiApi::class)
private fun showCrashWindow(
    owner: Window?,
    throwable: Throwable,
    report: String,
): CrashChoice {
    val dark = runCatching { PlatformAppearance.isSystemDark() }.getOrDefault(false)
    val colors = if (dark) amethystDarkColors(AccentColorType.PURPLE) else amethystLightColors(AccentColorType.PURPLE)
    var choice = CrashChoice.CLOSE

    val dialog = ComposeDialog(owner, Dialog.ModalityType.APPLICATION_MODAL)
    dialog.title = "Amethyst"
    dialog.setContent {
        MaterialTheme(colorScheme = colors) {
            Surface(Modifier.fillMaxSize()) {
                CrashContent(
                    throwable = throwable,
                    report = report,
                    onSend = {
                        choice = CrashChoice.SEND
                        dialog.isVisible = false
                    },
                    onClose = { dialog.isVisible = false },
                )
            }
        }
    }
    dialog.setSize(600, 620)
    dialog.setLocationRelativeTo(owner)
    dialog.isVisible = true // blocks until closed
    dialog.dispose()
    return choice
}

@Composable
private fun CrashContent(
    throwable: Throwable,
    report: String,
    onSend: () -> Unit,
    onClose: () -> Unit,
) {
    // Nothing here scrolls but the stack itself, which takes whatever height is left.
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Icon(symbol = MaterialSymbols.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
        Text(stringRes(Res.string.crash_window_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        Text(
            stringRes(Res.string.crash_window_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        // The one line a developer asks for first: what was thrown.
        SelectionContainer {
            Text(
                throwable.summary(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp),
            )
        }
        // Holds the buttons at the bottom while the details are folded (folded, they take no height).
        Column(Modifier.weight(1f)) {
            ErrorDetails(report, Modifier.weight(1f), fillHeight = true)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringRes(Res.string.close)) }
            Button(onClick = onSend, modifier = Modifier.padding(start = 8.dp)) { Text(stringRes(Res.string.crash_window_send)) }
        }
    }
}

private fun Throwable.summary(): String = listOfNotNull(this::class.simpleName, message).joinToString(": ")
