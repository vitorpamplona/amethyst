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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.PostButton
import com.vitorpamplona.amethyst.ui.note.ZapIcon
import com.vitorpamplona.amethyst.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Stable data class Reward(val amount: BigDecimal)

@Composable
fun DisplayReward(
    baseReward: Reward,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var popupExpanded by remember { mutableStateOf(false) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { popupExpanded = true },
        ) {
            ClickableText(
                text = AnnotatedString("#bounty"),
                onClick = { nav("Hashtag/bounty") },
                style =
                    LocalTextStyle.current.copy(
                        color =
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = 0.52f,
                            ),
                    ),
            )

            RenderPledgeAmount(baseNote, baseReward, accountViewModel)
        }

        if (popupExpanded) {
            AddBountyAmountDialog(baseNote, accountViewModel) { popupExpanded = false }
        }
    }
}

@Composable
private fun RenderPledgeAmount(
    baseNote: Note,
    baseReward: Reward,
    accountViewModel: AccountViewModel,
) {
    val repliesState by baseNote.live().replies.observeAsState()
    var reward by remember {
        mutableStateOf<String>(
            showAmount(baseReward.amount),
        )
    }

    var hasPledge by remember {
        mutableStateOf<Boolean>(
            false,
        )
    }

    LaunchedEffect(key1 = repliesState) {
        launch(Dispatchers.IO) {
            repliesState?.note?.pledgedAmountByOthers()?.let {
                val newRewardAmount = showAmount(baseReward.amount.add(it))
                if (newRewardAmount != reward) {
                    reward = newRewardAmount
                }
            }
            val newHasPledge = repliesState?.note?.hasPledgeBy(accountViewModel.userProfile()) == true
            if (hasPledge != newHasPledge) {
                launch(Dispatchers.Main) { hasPledge = newHasPledge }
            }
        }
    }

    if (hasPledge) {
        ZappedIcon(modifier = Size20Modifier)
    } else {
        ZapIcon(modifier = Size20Modifier, MaterialTheme.colorScheme.placeholderText)
    }

    Text(
        text = reward,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
    )
}

class AddBountyAmountViewModel : ViewModel() {
    private var account: Account? = null
    private var bounty: Note? = null

    var nextAmount by mutableStateOf(TextFieldValue(""))

    fun load(
        account: Account,
        bounty: Note?,
    ) {
        this.account = account
        this.bounty = bounty
    }

    fun sendPost() {
        val newValue = nextAmount.text.trim().toLongOrNull()

        if (newValue != null) {
            viewModelScope.launch {
                account?.sendPost(
                    message = newValue.toString(),
                    replyTo = listOfNotNull(bounty),
                    mentions = listOfNotNull(bounty?.author),
                    tags = listOf("bounty-added-reward"),
                    wantsToMarkAsSensitive = false,
                    replyingTo = null,
                    root = null,
                    directMentions = setOf(),
                    forkedFrom = null,
                    draftTag = null,
                )

                nextAmount = TextFieldValue("")
            }
        }
    }

    fun cancel() {
        nextAmount = TextFieldValue("")
    }

    fun hasChanged(): Boolean {
        return nextAmount.text.trim().toLongOrNull() != null
    }
}

@Composable
fun AddBountyAmountDialog(
    bounty: Note,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
) {
    val postViewModel: AddBountyAmountViewModel = viewModel()
    postViewModel.load(accountViewModel.account, bounty)
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface {
            Column(
                modifier = Modifier.padding(10.dp).width(IntrinsicSize.Min),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CloseButton(
                        onPress = {
                            postViewModel.cancel()
                            onClose()
                        },
                    )

                    PostButton(
                        onPost = {
                            postViewModel.sendPost()
                            onClose()
                        },
                        isActive = postViewModel.hasChanged(),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringResource(R.string.pledge_amount_in_sats)) },
                        value = postViewModel.nextAmount,
                        onValueChange = { postViewModel.nextAmount = it },
                        keyboardOptions =
                            KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Number,
                            ),
                        placeholder = {
                            Text(
                                text = "10000, 50000, 5000000",
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        singleLine = true,
                    )
                }
            }
        }
    }
}
