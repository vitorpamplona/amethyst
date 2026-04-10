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
package com.vitorpamplona.amethyst.ios.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.ios.account.AccountState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    account: AccountState.LoggedIn,
    localCache: IosLocalCache,
    relayManager: IosRelayConnectionManager,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val user = localCache.getUserIfExists(account.pubKeyHex)
    val currentMetadata =
        user
            ?.metadataOrNull()
            ?.flow
            ?.value
            ?.info

    var displayName by remember { mutableStateOf(currentMetadata?.displayName ?: "") }
    var name by remember { mutableStateOf(currentMetadata?.name ?: "") }
    var about by remember { mutableStateOf(currentMetadata?.about ?: "") }
    var pictureUrl by remember { mutableStateOf(currentMetadata?.picture ?: "") }
    var bannerUrl by remember { mutableStateOf(currentMetadata?.banner ?: "") }
    var nip05 by remember { mutableStateOf(currentMetadata?.nip05 ?: "") }
    var lud16 by remember { mutableStateOf(currentMetadata?.lud16 ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Edit Profile") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                TextButton(
                    onClick = onBack,
                ) {
                    Text("Cancel")
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // Avatar preview
            UserAvatar(
                userHex = account.pubKeyHex,
                pictureUrl = pictureUrl.ifBlank { null },
                size = 80.dp,
                contentDescription = "Profile picture preview",
            )

            Spacer(Modifier.height(24.dp))

            // Display Name
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                placeholder = { Text("How you appear to others") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Username
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Username") },
                placeholder = { Text("Short username (e.g. satoshi)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // About / Bio
            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                label = { Text("About") },
                placeholder = { Text("Tell the world about yourself") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Profile Picture URL
            OutlinedTextField(
                value = pictureUrl,
                onValueChange = { pictureUrl = it },
                label = { Text("Profile Picture URL") },
                placeholder = { Text("https://example.com/avatar.jpg") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Banner URL
            OutlinedTextField(
                value = bannerUrl,
                onValueChange = { bannerUrl = it },
                label = { Text("Banner URL") },
                placeholder = { Text("https://example.com/banner.jpg") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // NIP-05
            OutlinedTextField(
                value = nip05,
                onValueChange = { nip05 = it },
                label = { Text("NIP-05 Identifier") },
                placeholder = { Text("you@example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Lightning Address (LUD-16)
            OutlinedTextField(
                value = lud16,
                onValueChange = { lud16 = it },
                label = { Text("Lightning Address (LUD-16)") },
                placeholder = { Text("you@walletofsatoshi.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            errorMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Save button
            Button(
                onClick = {
                    if (account.isReadOnly) {
                        errorMessage = "Cannot edit profile in read-only mode"
                        return@Button
                    }
                    isSaving = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val latestEvent = localCache.getLatestMetadataEvent(account.pubKeyHex)
                            val template =
                                if (latestEvent != null) {
                                    MetadataEvent.updateFromPast(
                                        latest = latestEvent,
                                        name = name,
                                        displayName = displayName,
                                        picture = pictureUrl,
                                        banner = bannerUrl,
                                        about = about,
                                        nip05 = nip05,
                                        lnAddress = lud16,
                                    )
                                } else {
                                    MetadataEvent.createNew(
                                        name = name,
                                        displayName = displayName,
                                        picture = pictureUrl,
                                        banner = bannerUrl,
                                        about = about,
                                        nip05 = nip05,
                                        lnAddress = lud16,
                                    )
                                }

                            val signedEvent = account.signer.sign(template)
                            localCache.consumeMetadata(signedEvent)
                            relayManager.broadcastToAll(signedEvent)
                            onSaved()
                        } catch (e: Exception) {
                            errorMessage = "Failed to save: ${e.message}"
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving && !account.isReadOnly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save Profile")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
