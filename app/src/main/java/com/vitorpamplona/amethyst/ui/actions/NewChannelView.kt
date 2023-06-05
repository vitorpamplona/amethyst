package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun NewChannelView(onClose: () -> Unit, accountViewModel: AccountViewModel, channel: Channel? = null) {
    val postViewModel: NewChannelViewModel = viewModel()
    postViewModel.load(accountViewModel.account, channel)

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
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

                    val scope = rememberCoroutineScope()

                    PostButton(
                        onPost = {
                            scope.launch(Dispatchers.IO) {
                                postViewModel.create()
                                onClose()
                            }
                        },
                        postViewModel.channelName.value.text.isNotBlank()
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                OutlinedTextField(
                    label = { Text(text = stringResource(R.string.channel_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.channelName.value,
                    onValueChange = { postViewModel.channelName.value = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.my_awesome_group),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                )

                Spacer(modifier = Modifier.height(15.dp))

                OutlinedTextField(
                    label = { Text(text = stringResource(R.string.picture_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.channelPicture.value,
                    onValueChange = { postViewModel.channelPicture.value = it },
                    placeholder = {
                        Text(
                            text = "http://mygroup.com/logo.jpg",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(15.dp))

                OutlinedTextField(
                    label = { Text(text = stringResource(R.string.description)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    value = postViewModel.channelDescription.value,
                    onValueChange = { postViewModel.channelDescription.value = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.about_us),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    maxLines = 10

                )
            }
        }
    }
}
