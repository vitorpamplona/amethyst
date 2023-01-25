package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.UrlPreview
import com.vitorpamplona.amethyst.ui.components.VideoView
import com.vitorpamplona.amethyst.ui.components.imageExtension
import com.vitorpamplona.amethyst.ui.components.isValidURL
import com.vitorpamplona.amethyst.ui.components.noProtocolUrlValidator
import com.vitorpamplona.amethyst.ui.components.videoExtension
import com.vitorpamplona.amethyst.ui.navigation.UploadFromGallery
import com.vitorpamplona.amethyst.ui.note.ReplyInformation
import com.vitorpamplona.amethyst.ui.screen.UserLine
import kotlinx.coroutines.delay
import nostr.postr.events.TextNoteEvent


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NewPostView(onClose: () -> Unit, baseReplyTo: Note? = null, account: Account) {
    val postViewModel: NewPostViewModel = viewModel()

    postViewModel.load(account, baseReplyTo)

    val context = LocalContext.current

    // initialize focus reference to be able to request focus programmatically
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
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
                        postViewModel.cancel()
                        onClose()
                    })

                    UploadFromGallery {
                        postViewModel.upload(it, context)
                    }

                    PostButton(
                        onPost = {
                            postViewModel.sendPost()
                            onClose()
                        },
                        postViewModel.message.text.isNotBlank()
                    )
                }

                if (postViewModel.replyTos != null && baseReplyTo?.event is TextNoteEvent) {
                    ReplyInformation(postViewModel.replyTos, postViewModel.mentions, "✖ ") {
                        postViewModel.removeFromReplyList(it)
                    }
                }

                OutlinedTextField(
                    value = postViewModel.message,
                    onValueChange = {
                        postViewModel.updateMessage(it)
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colors.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                keyboardController?.show()
                            }
                        },
                    placeholder = {
                        Text(
                            text = "What's on your mind?",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    colors = TextFieldDefaults
                        .outlinedTextFieldColors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        ),
                    visualTransformation = UrlUserTagTransformation(MaterialTheme.colors.primary),
                    textStyle = TextStyle(textDirection = TextDirection.Content)
                )

                val userSuggestions = postViewModel.userSuggestions
                if (userSuggestions.isNotEmpty()) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = 10.dp,
                            bottom = 10.dp
                        )
                    ) {
                        itemsIndexed(userSuggestions, key = { _, item -> item.pubkeyHex }) { index, item ->
                            UserLine(item) {
                                postViewModel.autocompleteWithUser(item)
                            }
                        }
                    }
                }

                val myUrlPreview = postViewModel.urlPreview
                if (myUrlPreview != null) {
                    Column(modifier = Modifier.padding(top = 5.dp)) {
                        if (isValidURL(myUrlPreview)) {
                            val removedParamsFromUrl = myUrlPreview.split("?")[0].toLowerCase()
                            if (imageExtension.matcher(removedParamsFromUrl).matches()) {
                                AsyncImage(
                                    model = myUrlPreview,
                                    contentDescription = myUrlPreview,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .fillMaxWidth()
                                        .clip(shape = RoundedCornerShape(15.dp))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                                            RoundedCornerShape(15.dp)
                                        )
                                )
                            } else if (videoExtension.matcher(removedParamsFromUrl).matches()) {
                                VideoView(myUrlPreview)
                            } else {
                                UrlPreview(myUrlPreview, myUrlPreview)
                            }
                        } else if (noProtocolUrlValidator.matcher(myUrlPreview).matches()) {
                            UrlPreview("https://$myUrlPreview", myUrlPreview)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloseButton(onCancel: () -> Unit) {
    Button(
        onClick = {
            onCancel()
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = Color.Gray
            )
    ) {
        Text(text = "Cancel", color = Color.White)
    }
}

@Composable
fun PostButton(onPost: () -> Unit = {}, isActive: Boolean, modifier: Modifier = Modifier) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            )
    ) {
        Text(text = "Post", color = Color.White)
    }
}

@Composable
fun CreateButton(onPost: () -> Unit = {}, isActive: Boolean, modifier: Modifier = Modifier) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            )
    ) {
        Text(text = "Create", color = Color.White)
    }
}

@Composable
fun SearchButton(onPost: () -> Unit = {}, isActive: Boolean, modifier: Modifier = Modifier) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            null,
            modifier = Modifier.size(26.dp),
            tint = Color.White
        )
    }
}
