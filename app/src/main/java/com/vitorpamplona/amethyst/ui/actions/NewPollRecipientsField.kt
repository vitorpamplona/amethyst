package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel

@Composable
fun NewPollRecipientsField(pollViewModel: NewPostViewModel, account: Account) {
    // if no recipients, add user's pubkey
    if (pollViewModel.zapRecipients.isEmpty()) {
        pollViewModel.zapRecipients.add(account.userProfile().pubkeyHex)
    }

    // TODO allow add multiple recipients and check input validity

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
