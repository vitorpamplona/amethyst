package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NewUserMetadataView(onClose: () -> Unit, account: Account) {
    val postViewModel: NewUserMetadataViewModel = viewModel()

    LaunchedEffect(Unit) {
        postViewModel.load(account)
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
        ) {
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = {
                        postViewModel.clear()
                        onClose()
                    })

                    PostButton(
                        onPost = {
                            postViewModel.create()
                            onClose()
                        },
                        postViewModel.userName.value.isNotBlank()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(1f), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        label = { Text(text = "Display Name") },
                        modifier =  Modifier.weight(1f),
                        value = postViewModel.displayName.value,
                        onValueChange = { postViewModel.displayName.value = it },
                        placeholder = {
                            Text(
                                text = "My display name",
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        singleLine = true
                    )

                    Text("@", Modifier.padding(5.dp))

                    OutlinedTextField(
                        label = { Text(text = "Username") },
                        modifier = Modifier.weight(1f),
                        value = postViewModel.userName.value,
                        onValueChange = { postViewModel.userName.value = it },
                        placeholder = {
                            Text(
                                text = "My username",
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = "About me") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    value = postViewModel.about.value,
                    onValueChange = { postViewModel.about.value = it },
                    placeholder = {
                        Text(
                            text = "About me",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = "Avatar URL") },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.picture.value,
                    onValueChange = { postViewModel.picture.value = it },
                    placeholder = {
                        Text(
                            text = "https://mywebsite.com/me.jpg",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = "Banner URL") },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.banner.value,
                    onValueChange = { postViewModel.banner.value = it },
                    placeholder = {
                        Text(
                            text = "https://mywebsite.com/mybanner.jpg",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = "Website URL") },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.website.value,
                    onValueChange = { postViewModel.website.value = it },
                    placeholder = {
                        Text(
                            text = "https://mywebsite.com",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = "NIP-05") },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.nip05.value,
                    onValueChange = { postViewModel.nip05.value = it },
                    placeholder = {
                        Text(
                            text = "_@mywebsite.com",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    label = { Text(text = "LN Address") },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.lnAddress.value,
                    onValueChange = { postViewModel.lnAddress.value = it },
                    placeholder = {
                        Text(
                            text = "me@mylightiningnode.com",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = "LN URL (outdated)") },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.lnURL.value,
                    onValueChange = { postViewModel.lnURL.value = it },
                    placeholder = {
                        Text(
                            text = "LNURL...",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    singleLine = true
                )

            }
        }
    }
}
