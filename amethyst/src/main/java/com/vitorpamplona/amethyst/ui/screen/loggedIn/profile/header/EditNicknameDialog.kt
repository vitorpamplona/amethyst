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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText

/**
 * Edits the nickname (petname) and private note (summary) the account keeps for
 * [user] in its own kind:30382 contact card. Both fields are saved NIP-44
 * encrypted, so only this account can read them. Blank fields clear the value.
 */
@Composable
fun EditNicknameDialog(
    user: User,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val nickname = remember { mutableStateOf("") }
    val summary = remember { mutableStateOf("") }

    // Prefill with the card's current encrypted values, if any.
    LaunchedEffect(user) {
        nickname.value = accountViewModel.account.contactCards.petName(user.pubkeyHex) ?: ""
        summary.value = accountViewModel.account.contactCards.summary(user.pubkeyHex) ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringRes(R.string.nickname_dialog_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringRes(R.string.nickname_dialog_explainer),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
                TextField(
                    value = nickname.value,
                    onValueChange = { nickname.value = it },
                    singleLine = true,
                    label = {
                        Text(text = stringRes(R.string.nickname_label))
                    },
                    modifier = Modifier,
                )
                TextField(
                    value = summary.value,
                    onValueChange = { summary.value = it },
                    label = {
                        Text(text = stringRes(R.string.nickname_summary_label))
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    accountViewModel.updateContactCardPetName(
                        user = user,
                        petName = nickname.value.trim().ifBlank { null },
                        summary = summary.value.trim().ifBlank { null },
                    )
                    onDismiss()
                },
            ) {
                Text(stringRes(R.string.save))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
            ) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}
