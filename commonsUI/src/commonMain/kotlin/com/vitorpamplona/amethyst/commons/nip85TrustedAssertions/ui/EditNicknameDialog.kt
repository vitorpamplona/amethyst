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
package com.vitorpamplona.amethyst.commons.nip85TrustedAssertions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.ContactCardsState
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nickname_cancel
import com.vitorpamplona.amethyst.commons.resources.nickname_dialog_explainer
import com.vitorpamplona.amethyst.commons.resources.nickname_dialog_title
import com.vitorpamplona.amethyst.commons.resources.nickname_label
import com.vitorpamplona.amethyst.commons.resources.nickname_save
import com.vitorpamplona.amethyst.commons.resources.nickname_summary_label
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Edits the nickname (petname) and private note (summary) the account keeps for
 * [user] in its own kind:30382 contact card. Both fields are saved NIP-44
 * encrypted, so only this account can read them. Blank fields clear the value.
 *
 * Typing `:` offers the account's NIP-30 custom emojis; the mappings for any
 * shortcode used are embedded (also encrypted) so the nickname renders with them.
 *
 * Shared by every front end: the caller supplies the account's [contactCards]
 * and publishes the result in [onSave] (e.g. through its outbox relays). On
 * Android, compose `WatchAndLoadMyEmojiList` alongside so the emoji packs load.
 */
@Composable
fun EditNicknameDialog(
    user: User,
    contactCards: ContactCardsState,
    onSave: (petName: String?, summary: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val nickname = rememberTextFieldState()
    val summary = rememberTextFieldState()
    val emojiSuggestions = remember(contactCards) { EmojiSuggestionState(contactCards.emojiPacks) }
    // which field the emoji autocomplete should insert into: the last one edited
    val emojiTarget = remember { mutableStateOf<TextFieldState?>(null) }

    // Prefill with the card's current encrypted values, if any. Decryption can
    // be slow on external signers, so don't clobber anything already typed.
    LaunchedEffect(user) {
        contactCards
            .petName(user.pubkeyHex)
            ?.takeIf { nickname.text.isEmpty() }
            ?.let { nickname.setTextAndPlaceCursorAtEnd(it) }
        contactCards
            .summary(user.pubkeyHex)
            ?.takeIf { summary.text.isEmpty() }
            ?.let { summary.setTextAndPlaceCursorAtEnd(it) }
    }

    // Feed the word under the cursor of the last-edited field to the autocomplete.
    LaunchedEffect(nickname, summary) {
        fun watch(field: TextFieldState) {
            emojiTarget.value = field
            if (field.selection.collapsed) {
                emojiSuggestions.processCurrentWord(field.currentWord())
            }
        }
        launch { snapshotFlow { nickname.text }.collect { watch(nickname) } }
        launch { snapshotFlow { summary.text }.collect { watch(summary) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(Res.string.nickname_dialog_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(Res.string.nickname_dialog_explainer),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    state = nickname,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = {
                        Text(text = stringResource(Res.string.nickname_label))
                    },
                )
                OutlinedTextField(
                    state = summary,
                    label = {
                        Text(text = stringResource(Res.string.nickname_summary_label))
                    },
                )
                ShowEmojiSuggestionList(
                    emojiSuggestions,
                    onSelect = { emoji ->
                        emojiTarget.value?.let { emojiSuggestions.autocompleteInto(it, emoji) }
                    },
                    onFullSize = { emoji ->
                        emojiTarget.value?.let { emojiSuggestions.autocompleteInto(it, emoji) }
                    },
                    modifier = Modifier.heightIn(max = 200.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        nickname.text
                            .toString()
                            .trim()
                            .ifBlank { null },
                        summary.text
                            .toString()
                            .trim()
                            .ifBlank { null },
                    )
                    onDismiss()
                },
            ) {
                Text(stringResource(Res.string.nickname_save))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
            ) {
                Text(stringResource(Res.string.nickname_cancel))
            }
        },
    )
}
