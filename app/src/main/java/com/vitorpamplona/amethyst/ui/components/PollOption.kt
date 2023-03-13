package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R

@Composable
fun PollOption(optionIndex: Int) {
    var text by rememberSaveable() { mutableStateOf("") }

    Row() {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
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
        Button(
            modifier = Modifier
                .padding(start = 6.dp, top = 2.dp)
                .imePadding(),
            onClick = { /*TODO*/ },
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

@Preview
@Composable
fun PollOptionPreview() {
    PollOption(0)
}
