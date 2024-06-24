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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun NewChannelView(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
    channel: PublicChatChannel? = null,
) {
    val postViewModel: NewChannelViewModel = viewModel()
    postViewModel.load(accountViewModel.account, channel)

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
            ),
    ) {
        Surface {
            Column(
                modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState()),
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

                    PostButton(
                        onPost = {
                            postViewModel.create()
                            onClose()
                        },
                        postViewModel.channelName.value.text
                            .isNotBlank(),
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(R.string.channel_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.channelName.value,
                    onValueChange = { postViewModel.channelName.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(R.string.my_awesome_group),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                )

                Spacer(modifier = Modifier.height(15.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(R.string.picture_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.channelPicture.value,
                    onValueChange = { postViewModel.channelPicture.value = it },
                    placeholder = {
                        Text(
                            text = "http://mygroup.com/logo.jpg",
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                )

                Spacer(modifier = Modifier.height(15.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(R.string.description)) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    value = postViewModel.channelDescription.value,
                    onValueChange = { postViewModel.channelDescription.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(R.string.about_us),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    maxLines = 10,
                )
            }
        }
    }
}
