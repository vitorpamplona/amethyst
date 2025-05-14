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
package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import java.io.PrintWriter
import java.io.StringWriter

@Composable
fun InformationDialog(
    title: String,
    textContent: String?,
    throwable: Throwable,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onDismiss: () -> Unit,
) {
    val str = textContent ?: throwable.localizedMessage ?: throwable.message ?: throwable.javaClass.simpleName

    val stack =
        remember(throwable) {
            val writer = StringWriter()
            writer.append("\n")

            throwable.printStackTrace(PrintWriter(writer))

            writer.toString()
        }

    InformationDialog(title = title, textContent = str, moreInfo = stack, buttonColors, onDismiss)
}

@Composable
fun InformationDialog(
    title: String,
    textContent: String,
    moreInfo: String? = null,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SelectionContainer {
                Text(textContent)
            }
        },
        confirmButton = {
            Row(
                modifier =
                    Modifier
                        .padding(all = 8.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                moreInfo?.let {
                    val clipboardManager = LocalClipboardManager.current
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(it))
                    }) {
                        Text(stringRes(R.string.copy_stack_to_clipboard))
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = buttonColors,
                    contentPadding = PaddingValues(horizontal = Size16dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Done,
                            contentDescription = null,
                        )
                        Spacer(StdHorzSpacer)
                        Text(stringRes(R.string.error_dialog_button_ok))
                    }
                }
            }
        },
    )
}
