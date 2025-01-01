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
package com.vitorpamplona.amethyst.ui.actions

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectSingleFromGallery
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.CloseButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SaveButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun NewUserMetadataView(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val postViewModel: NewUserMetadataViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        postViewModel.load(accountViewModel.account)
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
    ) {
        Surface {
            Column(
                modifier = Modifier.padding(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(
                        onPress = {
                            postViewModel.clear()
                            onClose()
                        },
                    )

                    SaveButton(
                        onPost = {
                            postViewModel.create()
                            onClose()
                        },
                        true,
                    )
                }

                Column(
                    modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.display_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.displayName.value,
                        onValueChange = { postViewModel.displayName.value = it },
                        placeholder = {
                            Text(
                                text = stringRes(R.string.my_display_name),
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        keyboardOptions =
                            KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.Sentences,
                            ),
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.about_me)) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        value = postViewModel.about.value,
                        onValueChange = { postViewModel.about.value = it },
                        placeholder = {
                            Text(
                                text = stringRes(id = R.string.about_me),
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        keyboardOptions =
                            KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.Sentences,
                            ),
                        maxLines = 10,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.avatar_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.picture.value,
                        onValueChange = { postViewModel.picture.value = it },
                        placeholder = {
                            Text(
                                text = "https://mywebsite.com/me.jpg",
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        leadingIcon = {
                            SelectSingleFromGallery(
                                isUploading = postViewModel.isUploadingImageForPicture,
                                tint = MaterialTheme.colorScheme.placeholderText,
                                modifier = Modifier.padding(start = 5.dp),
                            ) {
                                postViewModel.uploadForPicture(it, context, onError = accountViewModel::toast)
                            }
                        },
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.banner_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.banner.value,
                        onValueChange = { postViewModel.banner.value = it },
                        placeholder = {
                            Text(
                                text = "https://mywebsite.com/mybanner.jpg",
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        leadingIcon = {
                            SelectSingleFromGallery(
                                isUploading = postViewModel.isUploadingImageForBanner,
                                tint = MaterialTheme.colorScheme.placeholderText,
                                modifier = Modifier.padding(start = 5.dp),
                            ) {
                                postViewModel.uploadForBanner(it, context, onError = accountViewModel::toast)
                            }
                        },
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.pronouns)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.pronouns.value,
                        onValueChange = { postViewModel.pronouns.value = it },
                        placeholder = {
                            Text(
                                text = "they/them, ...",
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.website_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.website.value,
                        onValueChange = { postViewModel.website.value = it },
                        placeholder = {
                            Text(
                                text = "https://mywebsite.com",
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.nip_05)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.nip05.value,
                        onValueChange = { postViewModel.nip05.value = it },
                        placeholder = {
                            Text(
                                text = "_@mywebsite.com",
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.ln_address)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.lnAddress.value,
                        onValueChange = { postViewModel.lnAddress.value = it },
                        placeholder = {
                            Text(
                                text = "me@mylightningnode.com",
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.ln_url_outdated)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.lnURL.value,
                        onValueChange = { postViewModel.lnURL.value = it },
                        placeholder = {
                            Text(
                                text = stringRes(R.string.lnurl),
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.twitter)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.twitter.value,
                        onValueChange = { postViewModel.twitter.value = it },
                        placeholder = {
                            Text(
                                text = stringRes(R.string.twitter_proof_url_template),
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.mastodon)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.mastodon.value,
                        onValueChange = { postViewModel.mastodon.value = it },
                        placeholder = {
                            Text(
                                text = stringRes(R.string.mastodon_proof_url_template),
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(R.string.github)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.github.value,
                        onValueChange = { postViewModel.github.value = it },
                        placeholder = {
                            Text(
                                text = stringRes(R.string.github_proof_url_template),
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                    )
                }
            }
        }
    }
}
