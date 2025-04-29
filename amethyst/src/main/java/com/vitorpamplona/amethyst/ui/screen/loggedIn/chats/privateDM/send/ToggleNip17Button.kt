/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.IncognitoIconOff
import com.vitorpamplona.amethyst.ui.note.IncognitoIconOn
import com.vitorpamplona.amethyst.ui.note.QuickActionAlertDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.launch

@Composable
fun ToggleNip17Button(
    channelScreenModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
) {
    var wantsToActivateNIP17 by remember { mutableStateOf(false) }

    // First, ensure we have DVMs loaded
    val dvmList =
        remember(channelScreenModel.room) {
            if (channelScreenModel.availableDvms.isEmpty()) {
                channelScreenModel.getKind5050DVMs()
            } else {
                channelScreenModel.availableDvms
            }
        }

    // Store the list if empty
    if (channelScreenModel.availableDvms.isEmpty() && dvmList.isNotEmpty()) {
        channelScreenModel.availableDvms = dvmList
    }

    // Check if this is a new DVM conversation by looking at the To field
    val isDvmInToField =
        channelScreenModel.availableDvms.any { dvm ->
            channelScreenModel.toUsers.text.contains(dvm.pubkey) ||
                channelScreenModel.toUsers.text.contains(
                    com.vitorpamplona.quartz.utils.Hex
                        .decode(dvm.pubkey)
                        .toNpub(),
                )
        }

    // Check if this is an existing DVM conversation by looking at room users
    val isExistingDvmConversation =
        channelScreenModel.room?.let { room ->
            // Check if any user in the room is a known DVM
            room.users.any { userPubkey ->
                channelScreenModel.availableDvms.any { dvm -> dvm.pubkey == userPubkey }
            }
        } ?: false

    // Combined check for any DVM involvement
    val isDvmConversation = isDvmInToField || isExistingDvmConversation

    // If this is a DVM conversation, force nip17 to false to maintain consistency
    if (isDvmConversation && channelScreenModel.nip17) {
        channelScreenModel.nip17 = false
    }

    if (wantsToActivateNIP17) {
        NewFeatureNIP17AlertDialog(
            accountViewModel = accountViewModel,
            onConfirm = { channelScreenModel.toggleNIP04And24() },
            onDismiss = { wantsToActivateNIP17 = false },
        )
    }

    IconButton(
        modifier = Modifier.width(30.dp),
        onClick = {
            // Only allow toggle if not a DVM conversation
            if (isDvmConversation) {
                // If DVM conversation, show toast explaining why NIP-17 is disabled
                accountViewModel.toastManager.toast(
                    R.string.dvm_nip17_disabled_title,
                    R.string.dvm_nip17_disabled_message,
                )
            } else if (
                !accountViewModel.account.settings.hideNIP17WarningDialog &&
                !channelScreenModel.nip17 &&
                !channelScreenModel.requiresNIP17
            ) {
                wantsToActivateNIP17 = true
            } else {
                channelScreenModel.toggleNIP04And24()
            }
        },
    ) {
        if (channelScreenModel.nip17 && !isDvmConversation) {
            IncognitoIconOn(
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            IncognitoIconOff(
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .size(20.dp),
                tint =
                    if (isDvmConversation) {
                        MaterialTheme.colorScheme.placeholderText.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.placeholderText
                    },
            )
        }
    }
}

@Composable
fun NewFeatureNIP17AlertDialog(
    accountViewModel: AccountViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    QuickActionAlertDialog(
        title = stringRes(R.string.new_feature_nip17_might_not_be_available_title),
        textContent = stringRes(R.string.new_feature_nip17_might_not_be_available_description),
        buttonIconResource = R.drawable.incognito,
        buttonText = stringRes(R.string.new_feature_nip17_activate),
        onClickDoOnce = {
            scope.launch { onConfirm() }
            onDismiss()
        },
        onClickDontShowAgain = {
            scope.launch {
                onConfirm()
                accountViewModel.account.settings.setHideNIP17WarningDialog()
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
