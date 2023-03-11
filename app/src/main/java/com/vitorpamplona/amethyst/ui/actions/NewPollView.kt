package com.vitorpamplona.amethyst.ui.actions

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.ui.components.*
import com.vitorpamplona.amethyst.ui.note.ReplyInformation
import com.vitorpamplona.amethyst.ui.screen.loggedIn.UserLine
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NewPollView(onClose: () -> Unit, baseReplyTo: Note? = null, quote: Note? = null, account: Account) {
    val pollViewModel: NewPollViewModel = viewModel()

    val context = LocalContext.current

    // initialize focus reference to be able to request focus programmatically
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        pollViewModel.load(account, baseReplyTo, quote)
        delay(100)
        focusRequester.requestFocus()

        pollViewModel.imageUploadingError.collect { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                        .imePadding()
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClosePollButton(onCancel = {
                            pollViewModel.cancel()
                            onClose()
                        })

                        PollButton(
                            onPost = {
                                pollViewModel.sendPost()
                                onClose()
                            },
                            isActive = pollViewModel.message.text.isNotBlank() &&
                                !pollViewModel.isUploadingImage
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                        ) {
                            if (pollViewModel.replyTos != null && baseReplyTo?.event is TextNoteEvent) {
                                ReplyInformation(pollViewModel.replyTos, pollViewModel.mentions, account, "✖ ") {
                                    pollViewModel.removeFromReplyList(it)
                                }
                            }

                            OutlinedTextField(
                                value = pollViewModel.message,
                                onValueChange = {
                                    pollViewModel.updateMessage(it)
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
                                        text = stringResource(R.string.primary_poll_description),
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                    )
                                },
                                colors = TextFieldDefaults
                                    .outlinedTextFieldColors(
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent
                                    ),
                                visualTransformation = UrlUserTagTransformation(MaterialTheme.colors.primary),
                                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                            )
                        }
                    }

                    val userSuggestions = pollViewModel.userSuggestions
                    if (userSuggestions.isNotEmpty()) {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                top = 10.dp
                            ),
                            modifier = Modifier.heightIn(0.dp, 300.dp)
                        ) {
                            itemsIndexed(
                                userSuggestions,
                                key = { _, item -> item.pubkeyHex }
                            ) { index, item ->
                                UserLine(item, account) {
                                    pollViewModel.autocompleteWithUser(item)
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        /*UploadFromGallery(
                            isUploading = pollViewModel.isUploadingImage
                        ) {
                            pollViewModel.upload(it, context)
                        }*/
                    }
                }
            }
        }
    }
}

@Composable
fun ClosePollButton(onCancel: () -> Unit) {
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
        Icon(
            painter = painterResource(id = R.drawable.ic_close),
            contentDescription = stringResource(id = R.string.cancel),
            modifier = Modifier.size(20.dp),
            tint = Color.White
        )
    }
}

@Composable
fun PollButton(modifier: Modifier = Modifier, onPost: () -> Unit = {}, isActive: Boolean) {
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
        Text(text = stringResource(R.string.post_poll), color = Color.White)
    }
}
