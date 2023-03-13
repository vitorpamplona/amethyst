package com.vitorpamplona.amethyst.ui.components

import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.vitorpamplona.amethyst.R

@Composable
fun PollRecipientsField() {
    var text by rememberSaveable() { mutableStateOf("") }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = {
            Text(
                text = stringResource(R.string.poll_zap_recipients),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        },
        placeholder = {
            Text(
                text = stringResource(R.string.poll_zap_recipients),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        }

    )
}
