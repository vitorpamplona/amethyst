package com.vitorpamplona.amethyst.ui.actions

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.ui.components.*
import com.vitorpamplona.amethyst.ui.note.ReplyInformation
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.UserLine
import kotlinx.coroutines.delay

@Composable
fun NewPollView(onClose: () -> Unit, baseReplyTo: Note? = null, quote: Note? = null, accountViewModel: AccountViewModel) {
    val pollViewModel: NewPostViewModel = viewModel()

    val context = LocalContext.current

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        pollViewModel.load(accountViewModel.account, baseReplyTo, quote)
        delay(100)

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
                        CloseButton(onCancel = {
                            pollViewModel.cancel()
                            onClose()
                        })

                        PollButton(
                            onPost = {
                                pollViewModel.sendPost()
                                onClose()
                            },
                            isActive = pollViewModel.message.text.isNotBlank() &&
                                pollViewModel.pollOptions.values.all { it.isNotEmpty() } &&
                                pollViewModel.isValidRecipients.value &&
                                pollViewModel.isValidvalueMaximum.value &&
                                pollViewModel.isValidvalueMinimum.value &&
                                pollViewModel.isValidConsensusThreshold.value &&
                                pollViewModel.isValidClosedAt.value
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
                                ReplyInformation(pollViewModel.replyTos, pollViewModel.mentions, accountViewModel, "✖ ") {
                                    pollViewModel.removeFromReplyList(it)
                                }
                            }

                            Text(stringResource(R.string.poll_heading_required))
                            // NewPollRecipientsField(pollViewModel, account)
                            NewPollPrimaryDescription(pollViewModel)
                            pollViewModel.pollOptions.values.forEachIndexed { index, _ ->
                                NewPollOption(pollViewModel, index)
                            }
                            Button(
                                onClick = { pollViewModel.pollOptions[pollViewModel.pollOptions.size] = "" },
                                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.32f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                )
                            ) {
                                Image(
                                    painterResource(id = android.R.drawable.ic_input_add),
                                    contentDescription = "Add poll option button",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(stringResource(R.string.poll_heading_optional))
                            NewPollVoteValueRange(pollViewModel)
                            NewPollConsensusThreshold(pollViewModel)
                            NewPollClosing(pollViewModel)
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
                            ) { _, item ->
                                UserLine(item, accountViewModel) {
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

/*@Preview
@Composable
fun NewPollViewPreview() {
    NewPollView(onClose = {}, account = Account(loggedIn = Persona()))
}*/
