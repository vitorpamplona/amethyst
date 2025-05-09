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

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@Composable
fun ToggleNip17Button(
    channelScreenModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
) {
    var wantsToActivateNIP17 by remember { mutableStateOf(false) }

    // We'll use this to decide if we need to load DVMs
    val toFieldText = channelScreenModel.toUsers.text
    val roomUsers = channelScreenModel.room?.users ?: emptySet()

    // Only check for cached DVMs - don't trigger discovery yet
    val potentialDvmInToField =
        toFieldText.isNotBlank() &&
            (
                channelScreenModel.availableDvms.isNotEmpty() &&
                    channelScreenModel.availableDvms.any { dvm ->
                        toFieldText.contains(dvm.pubkey) ||
                            toFieldText.contains(
                                com.vitorpamplona.quartz.utils.Hex
                                    .decode(dvm.pubkey)
                                    .toNpub(),
                            )
                    }
            )

    // Check if this is an existing DVM conversation by looking at room users
    val isExistingDvmConversation =
        roomUsers.isNotEmpty() &&
            channelScreenModel.availableDvms.isNotEmpty() &&
            roomUsers.any { userPubkey ->
                channelScreenModel.availableDvms.any { dvm -> dvm.pubkey == userPubkey }
            }

    // Combined check for any DVM involvement
    val isDvmConversation = potentialDvmInToField || isExistingDvmConversation

    // When component is first created, check if room contains DVMs
    // and ensure NIP17 is disabled for DVM conversations
    LaunchedEffect(roomUsers) {
        // If there are room users to check and DVM list is empty, try to load DVMs
        if (roomUsers.isNotEmpty() && channelScreenModel.availableDvms.isEmpty()) {
            try {
                Log.d("DVM_DEBUG", "Loading DVMs in ToggleNip17Button LaunchedEffect...")
                // Load DVMs to check if this is a DVM conversation - use async/await to handle suspend function
                val dvmsDeferred = async { NIP90TextGenUtil.getTextGenerationDVMs(accountViewModel.account) }
                val dvms = dvmsDeferred.await()

                // Log discovered DVMs
                Log.d("DVM_DEBUG", "Found ${dvms.size} text generation DVMs in ToggleNip17Button")

                if (dvms.isNotEmpty()) {
                    channelScreenModel.availableDvms = dvms

                    // Check if any room user is a DVM
                    val isDvm =
                        roomUsers.any { userPubkey ->
                            dvms.any { dvm -> dvm.pubkey == userPubkey }
                        }

                    // If DVM conversation, force NIP17 to false
                    if (isDvm && channelScreenModel.nip17) {
                        Log.d("DVM_DEBUG", "Found DVM in conversation, disabling NIP17")
                        channelScreenModel.nip17 = false
                    }
                }
            } catch (e: Exception) {
                Log.e("DVM_DEBUG", "Error loading DVMs in ToggleNip17Button: ${e.message}", e)
            }
        }
    }

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

    // Coroutine scope for async DVM loading
    val coroutineScope = rememberCoroutineScope()

    IconButton(
        modifier = Modifier.width(30.dp),
        onClick = {
            // Handle DVM check if needed
            if (toFieldText.isNotBlank() &&
                channelScreenModel.availableDvms.isEmpty() &&
                !isDvmConversation
            ) {
                // Load DVMs in background to check if we're talking to a DVM
                coroutineScope.launch {
                    try {
                        // Use async-await to handle suspend function
                        val dvmsDeferred = async { NIP90TextGenUtil.getTextGenerationDVMs(accountViewModel.account) }
                        val dvms = dvmsDeferred.await()

                        // Log discovered DVMs
                        Log.d("DVM_DEBUG", "Button click: Found ${dvms.size} text generation DVMs")

                        if (dvms.isNotEmpty()) {
                            channelScreenModel.availableDvms = dvms
                        }
                    } catch (e: Exception) {
                        Log.e("DVM_DEBUG", "Error loading DVMs on button click: ${e.message}", e)
                    }
                }
            }

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
