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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun NewPollOption(
    pollViewModel: NewPostViewModel,
    optionIndex: Int,
) {
    Row {
        val deleteIcon: @Composable (() -> Unit) = {
            IconButton(
                onClick = {
                    pollViewModel.removePollOption(optionIndex)
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.clear),
                )
            }
        }

        OutlinedTextField(
            modifier = Modifier.weight(1F),
            value = pollViewModel.pollOptions[optionIndex] ?: "",
            onValueChange = {
                pollViewModel.updatePollOption(optionIndex, it)
            },
            label = {
                Text(
                    text = stringResource(R.string.poll_option_index).format(optionIndex + 1),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.poll_option_description),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            // colors = if (pollViewModel.pollOptions[optionIndex]?.isNotEmpty() == true) colorValid else
            // colorInValid,
            trailingIcon = if (optionIndex > 1) deleteIcon else null,
        )
    }
}

@Preview
@Composable
fun NewPollOptionPreview() {
    NewPollOption(NewPostViewModel(), 0)
}
