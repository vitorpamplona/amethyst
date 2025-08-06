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
package com.vitorpamplona.amethyst.ui.note.creators.zappolls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.actions.NewPollOption
import com.vitorpamplona.amethyst.ui.actions.NewPollVoteValueRange
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.ShortNotePostViewModel
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun PollField(postViewModel: ShortNotePostViewModel) {
    val optionsList = postViewModel.pollOptions
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        optionsList.forEach { value ->
            NewPollOption(postViewModel, value.key)
        }

        NewPollVoteValueRange(postViewModel)

        Button(
            onClick = {
                // postViewModel.pollOptions[postViewModel.pollOptions.size] = ""
                optionsList[optionsList.size] = ""
            },
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.placeholderText,
                ),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.placeholderText,
                ),
        ) {
            Image(
                painter = painterRes(resourceId = android.R.drawable.ic_input_add, 1),
                contentDescription = "Add poll option button",
                modifier = Size18Modifier,
            )
        }
    }
}
