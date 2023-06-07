package com.vitorpamplona.amethyst.ui.actions

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NewUserMetadataView(onClose: () -> Unit, account: Account) {
    val postViewModel: NewUserMetadataViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        postViewModel.load(account)

        launch(Dispatchers.IO) {
            postViewModel.imageUploadingError.collect { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface() {
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
                        true
                    )
                }

                Column(
                    modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            label = { Text(text = stringResource(R.string.display_name)) },
                            modifier = Modifier.weight(1f),
                            value = postViewModel.displayName.value,
                            onValueChange = { postViewModel.displayName.value = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.my_display_name),
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
                            label = { Text(text = stringResource(R.string.username)) },
                            modifier = Modifier.weight(1f),
                            value = postViewModel.userName.value,
                            onValueChange = { postViewModel.userName.value = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.my_username),
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                )
                            },
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringResource(R.string.about_me)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        value = postViewModel.about.value,
                        onValueChange = { postViewModel.about.value = it },
                        placeholder = {
                            Text(
                                text = stringResource(id = R.string.about_me),
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
                        label = { Text(text = stringResource(R.string.avatar_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.picture.value,
                        onValueChange = { postViewModel.picture.value = it },
                        placeholder = {
                            Text(
                                text = "https://mywebsite.com/me.jpg",
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        leadingIcon = {
                            UploadFromGallery(
                                isUploading = postViewModel.isUploadingImageForPicture,
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                modifier = Modifier.padding(start = 5.dp)
                            ) {
                                postViewModel.uploadForPicture(it, context)
                            }
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringResource(R.string.banner_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.banner.value,
                        onValueChange = { postViewModel.banner.value = it },
                        placeholder = {
                            Text(
                                text = "https://mywebsite.com/mybanner.jpg",
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        leadingIcon = {
                            UploadFromGallery(
                                isUploading = postViewModel.isUploadingImageForBanner,
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                modifier = Modifier.padding(start = 5.dp)
                            ) {
                                postViewModel.uploadForBanner(it, context)
                            }
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringResource(R.string.website_url)) },
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
                        label = { Text(text = stringResource(R.string.nip_05)) },
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
                        label = { Text(text = stringResource(R.string.ln_address)) },
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
                        label = { Text(text = stringResource(R.string.ln_url_outdated)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.lnURL.value,
                        onValueChange = { postViewModel.lnURL.value = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.lnurl),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringResource(R.string.twitter)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.twitter.value,
                        onValueChange = { postViewModel.twitter.value = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.twitter_proof_url_template),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringResource(R.string.mastodon)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.mastodon.value,
                        onValueChange = { postViewModel.mastodon.value = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.mastodon_proof_url_template),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringResource(R.string.github)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.github.value,
                        onValueChange = { postViewModel.github.value = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.github_proof_url_template),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        }
                    )
                }
            }
        }
    }
}
