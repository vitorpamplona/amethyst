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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * NIP-32: lets the user tag any post with a hashtag by publishing a kind 1985 label
 * event (under the `#t` tag-association namespace). The label is public so that the
 * author's and the labeler's followers can discover the post in the hashtag feed.
 */
@Composable
fun AddHashtagLabelDialog(
    note: Note,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    var hashtag by remember { mutableStateOf("") }

    // Strip the leading '#', drop whitespace and lowercase so the stored label matches the
    // hashtag-feed convention. A blank result disables the confirm button.
    val sanitized =
        hashtag
            .trim()
            .removePrefix("#")
            .filterNot { it.isWhitespace() }
            .lowercase()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.add_hashtag_label_title)) },
        text = {
            Column {
                Text(stringRes(R.string.add_hashtag_label_explainer))
                OutlinedTextField(
                    value = hashtag,
                    onValueChange = { hashtag = it },
                    singleLine = true,
                    label = { Text(stringRes(R.string.add_hashtag_label_field)) },
                    prefix = { Text("#") },
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Done,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = sanitized.isNotEmpty(),
                onClick = {
                    accountViewModel.labelWithHashtag(note, sanitized)
                    onDismiss()
                },
            ) {
                Text(stringRes(R.string.add_hashtag_label_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}
