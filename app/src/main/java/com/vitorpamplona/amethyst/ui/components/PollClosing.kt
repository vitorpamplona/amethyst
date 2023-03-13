package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R

@Composable
fun PollClosing() {
    var text by rememberSaveable { mutableStateOf("") }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(150.dp),
            label = {
                Text(
                    text = stringResource(R.string.poll_closing_time),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.poll_closing_time_days),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            }
        )
    }
}

@Preview
@Composable
fun PollClosingPreview() {
    PollClosing()
}
