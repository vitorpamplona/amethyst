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
package com.vitorpamplona.amethyst.ui.note.creators.polls

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.ShortNotePostViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip88Polls.poll.tags.OptionTag
import com.vitorpamplona.quartz.utils.RandomInstance

@Composable
fun PollOptionsField(postViewModel: ShortNotePostViewModel) {
    val optionsList = postViewModel.pollOptions
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        optionsList.forEach { option ->
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = postViewModel.pollOptions[option.key]?.label ?: "",
                onValueChange = {
                    postViewModel.updatePollOption(option.key, it)
                },
                label = {
                    Text(
                        text = stringRes(R.string.poll_option_index, option.key + 1),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                placeholder = {
                    Text(
                        text = stringRes(R.string.poll_option_description),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            postViewModel.removePollOption(option.key)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringRes(R.string.clear),
                        )
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        PollDeadlinePicker(postViewModel)

        Spacer(Modifier.height(2.dp))

        Button(
            onClick = {
                postViewModel.pollOptions[postViewModel.pollOptions.size] = OptionTag(RandomInstance.randomChars(6), "")
            },
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        ) {
            Icon(Icons.Default.Add, contentDescription = stringRes(R.string.add_poll_option_button))
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview
@Composable
fun NewPollClosingPreview() {
    PollOptionsField(
        ShortNotePostViewModel(),
    )
}
