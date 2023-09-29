package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.theme.placeholderText

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
                color = MaterialTheme.colorScheme.placeholderText
            )
        },
        placeholder = {
            Text(
                text = stringResource(R.string.poll_zap_recipients),
                color = MaterialTheme.colorScheme.placeholderText
            )
        }

    )
}
