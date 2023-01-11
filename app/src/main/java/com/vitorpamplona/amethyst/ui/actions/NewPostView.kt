package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import nostr.postr.events.TextNoteEvent

class PostViewModel: ViewModel() {
    var account: Account? = null
    var message by mutableStateOf("")
    var replyingTo: Note? = null

    fun sendPost() {
        account?.sendPost(message, replyingTo)
    }
}

@Composable
fun NewPostView(onClose: () -> Unit, replyingTo: Note? = null, account: Account) {
    val postViewModel: PostViewModel = viewModel<PostViewModel>().apply {
        this.replyingTo = replyingTo
        this.account = account
    }

    val dialogProperties = DialogProperties()
    Dialog(
        onDismissRequest = { onClose() }, properties = dialogProperties
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
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
                    CloseButton(onCancel = onClose)

                    PostButton(
                        onPost = {
                            postViewModel.sendPost()
                            onClose()
                        }
                    )
                }

                if (replyingTo != null && replyingTo.event is TextNoteEvent) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val replyList = replyingTo.replyTo!!.plus(replyingTo).joinToString(", ",  "", "", 2) { it.idDisplayHex }
                        val withList = replyingTo.mentions!!.plus(replyingTo.author!!).joinToString(", ",  "", "", 2) { it.toBestDisplayName() }

                        Text(
                            "in reply to ${replyList} with ${withList}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    }
                }

                OutlinedTextField(
                    value = postViewModel.message,
                    onValueChange = { postViewModel.message = it },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colors.surface,
                            shape = RoundedCornerShape(8.dp)
                        ),
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
                        )

                )
            }
        }
    }
}

@Composable
private fun CloseButton(onCancel: () -> Unit) {
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
private fun PostButton(onPost: () -> Unit = {}) {
    Button(
        onClick = {
            onPost()
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
    ) {
        Text(text = "Post", color = Color.White)
    }
}
