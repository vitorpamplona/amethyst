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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

/**
 * Dialog for adding a custom emoji to an owned emoji pack (NIP-30 kind 30030).
 *
 * The [onConfirm] callback receives the new [EmojiUrlTag] alongside an `isPrivate`
 * flag: when `true`, the caller is expected to store the entry in the event's
 * encrypted `.content` (NIP-51 private tags) rather than as a public tag.
 *
 * NOTE: Private emojis are currently only visible to the pack owner when viewing
 * their own pack here. They are NOT surfaced in the reaction menu or in the `:`
 * autocomplete picker, because those consumers read public tags only via
 * [com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.mergePack].
 * The dialog surfaces a warning so users understand the tradeoff.
 */
@Composable
fun AddEmojiDialog(
    onDismiss: () -> Unit,
    onConfirm: (EmojiUrlTag, Boolean) -> Unit,
) {
    var shortcode by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var packAddressText by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    val shortcodeValid by remember {
        derivedStateOf {
            shortcode.isNotBlank() && EmojiUrlTag.isValidShortcode(shortcode)
        }
    }

    val shortcodeShowError by remember {
        derivedStateOf {
            shortcode.isNotBlank() && !EmojiUrlTag.isValidShortcode(shortcode)
        }
    }

    val canConfirm by remember {
        derivedStateOf {
            shortcodeValid && url.isNotBlank()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringRes(R.string.emoji_add_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.Top,
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = shortcode,
                    onValueChange = { shortcode = it.trim(':').trim() },
                    label = { Text(stringRes(R.string.emoji_shortcode_label)) },
                    isError = shortcodeShowError,
                    supportingText = {
                        if (shortcodeShowError) {
                            Text(stringRes(R.string.emoji_shortcode_invalid))
                        }
                    },
                )
                Spacer(DoubleVertSpacer)
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringRes(R.string.emoji_url_label)) },
                )
                Spacer(DoubleVertSpacer)
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = packAddressText,
                    onValueChange = { packAddressText = it },
                    label = { Text(stringRes(R.string.emoji_pack_address_label)) },
                )
                Spacer(DoubleVertSpacer)
                FilterChip(
                    selected = isPrivate,
                    onClick = { isPrivate = !isPrivate },
                    label = { Text(stringRes(R.string.emoji_private_toggle)) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                        )
                    },
                )
                Spacer(DoubleVertSpacer)
                Text(
                    text =
                        stringRes(
                            if (isPrivate) {
                                R.string.emoji_private_explainer
                            } else {
                                R.string.emoji_public_explainer
                            },
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirm,
                onClick = {
                    val parsedAddress = packAddressText.trim().takeIf { it.isNotEmpty() }?.let { Address.parse(it) }
                    onConfirm(
                        EmojiUrlTag(code = shortcode, url = url.trim(), emojiSet = parsedAddress),
                        isPrivate,
                    )
                },
            ) {
                Text(stringRes(R.string.add))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}
