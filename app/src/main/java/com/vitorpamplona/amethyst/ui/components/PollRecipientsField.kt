package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.NewPollViewModel

@Composable
fun PollRecipientsField(pollViewModel: NewPollViewModel) {

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth(),
        value = pollViewModel.zapRecipients[0],
        onValueChange = { /* TODO */ },
        enabled = false, // TODO enable add recipients
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
