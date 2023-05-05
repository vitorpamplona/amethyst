package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.R

@Composable
fun NewPollOption(pollViewModel: NewPostViewModel, optionIndex: Int) {
    Row {
        val deleteIcon: @Composable (() -> Unit) = {
            IconButton(
                onClick = {
                    pollViewModel.pollOptions.remove(optionIndex)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.clear)
                )
            }
        }

        OutlinedTextField(
            modifier = Modifier.weight(1F),
            value = pollViewModel.pollOptions[optionIndex] ?: "",
            onValueChange = { pollViewModel.pollOptions[optionIndex] = it },
            label = {
                Text(
                    text = stringResource(R.string.poll_option_index).format(optionIndex + 1),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.poll_option_description),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            keyboardOptions = KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences
            ),
            // colors = if (pollViewModel.pollOptions[optionIndex]?.isNotEmpty() == true) colorValid else colorInValid,
            trailingIcon = if (optionIndex > 1) deleteIcon else null
        )
    }
}

@Preview
@Composable
fun NewPollOptionPreview() {
    NewPollOption(NewPostViewModel(), 0)
}
