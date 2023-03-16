package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.NewPollViewModel

@Composable
fun PollOption(pollViewModel: NewPollViewModel, optionIndex: Int) {
    Row() {
        OutlinedTextField(
            value = pollViewModel.pollOptions[optionIndex],
            onValueChange = { pollViewModel.pollOptions[optionIndex] = it },
            label = {
                Text(
                    text = stringResource(R.string.poll_option_index).format(optionIndex),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.poll_option_description),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            }

        )
        if (optionIndex > 1) {
            Button(
                modifier = Modifier
                    .padding(start = 6.dp, top = 2.dp)
                    .imePadding(),
                onClick = { pollViewModel.pollOptions.removeAt(optionIndex) },
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.32f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            ) {
                Image(
                    painterResource(id = android.R.drawable.ic_delete),
                    contentDescription = "Remove poll option button",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun PollOptionPreview() {
    PollOption(NewPollViewModel(), 0)
}
