/**
 * Copyright (c) 2024 Vitor Pamplona
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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.quartz.events.EmptyTagList

@Composable
fun NotifyRequestDialog(
    title: String,
    textContent: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val defaultBackground = MaterialTheme.colorScheme.background
            val background = remember { mutableStateOf(defaultBackground) }

            TranslatableRichTextViewer(
                textContent,
                canPreview = true,
                quotesLeft = 1,
                Modifier.fillMaxWidth(),
                EmptyTagList,
                background,
                textContent,
                accountViewModel,
                nav,
            )
        },
        confirmButton = {
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
                    Text(stringResource(R.string.error_dialog_button_ok))
                }
            }
        },
    )
}
