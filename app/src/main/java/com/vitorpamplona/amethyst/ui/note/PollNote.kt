package com.vitorpamplona.amethyst.ui.note

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.TranslateableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun PollNote(
    note: Note,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val pollViewModel: PollNoteViewModel = viewModel()
    pollViewModel.load(note)

    pollViewModel.pollEvent?.pollOptions()?.forEach { poll_op ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TranslateableRichTextViewer(
                poll_op.value,
                canPreview,
                modifier = Modifier
                    .width(250.dp)
                    .border(BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.32f)))
                    .padding(4.dp),
                pollViewModel.pollEvent?.tags(),
                backgroundColor,
                accountViewModel,
                navController
            )

            ZapVote(note, accountViewModel, pollViewModel, poll_op.key)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ZapVote(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    pollViewModel: PollNoteViewModel,
    pollOption: Int,
    modifier: Modifier = Modifier
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val zapsState by baseNote.live().zaps.observeAsState()
    val zappedNote = zapsState?.note

    var wantsToZap by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(Modifier.size(20.dp))
            .combinedClickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 24.dp),
                onClick = {
                    if (!accountViewModel.isWriteable()) {
                        scope.launch {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.login_with_a_private_key_to_be_able_to_send_zaps),
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        }
                    } else if (pollViewModel.isPollClosed) {
                        scope.launch {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.poll_is_closed),
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        }
                    } else if (pollViewModel.isVoteAmountAtomic) {
                        accountViewModel.zap(
                            baseNote,
                            pollViewModel.valueMaximum!!.toLong() * 1000,
                            pollOption,
                            "",
                            context
                        ) {
                            scope.launch {
                                Toast
                                    .makeText(context, it, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    } else {
                        wantsToZap = true
                    }
                },
                onLongClick = {}
            )
    ) {
        if (wantsToZap) {
            ZapVoteAmountChoicePopup(
                baseNote,
                accountViewModel,
                pollViewModel,
                pollOption,
                onDismiss = {
                    wantsToZap = false
                },
                onError = {
                    scope.launch {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        if (zappedNote?.isPollOptionZappedBy(pollOption, account.userProfile()) == true) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = stringResource(R.string.zaps),
                modifier = Modifier.size(20.dp),
                tint = BitcoinOrange
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = stringResource(id = R.string.zaps),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        }
    }

    if (zappedNote?.isZappedBy(account.userProfile()) == true) {
        Text(
            showAmount(zappedNote.zappedPollOptionAmount(pollOption)),
            fontSize = 14.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZapVoteAmountChoicePopup(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    pollViewModel: PollNoteViewModel,
    pollOption: Int,
    onDismiss: () -> Unit,
    onError: (text: String) -> Unit
) {
    val context = LocalContext.current

    var textAmount by rememberSaveable { mutableStateOf("") }

    val colorInValid = TextFieldDefaults.outlinedTextFieldColors(
        focusedBorderColor = MaterialTheme.colors.error,
        unfocusedBorderColor = Color.Red
    )
    val colorValid = TextFieldDefaults.outlinedTextFieldColors(
        focusedBorderColor = MaterialTheme.colors.primary,
        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    )

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(10.dp)
            ) {
                val amount = pollViewModel.amount(textAmount)

                OutlinedTextField(
                    value = textAmount,
                    onValueChange = { textAmount = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(150.dp),
                    colors = if (pollViewModel.isValidAmount(amount)) colorValid else colorInValid,
                    label = {
                        Text(
                            text = stringResource(R.string.poll_zap_amount),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    placeholder = {
                        Text(
                            text = pollViewModel.voteAmountPlaceHolderText(context),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    }
                )

                Button(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    enabled = pollViewModel.isValidAmount(amount),
                    onClick = {
                        if (amount != null) {
                            accountViewModel.zap(
                                baseNote,
                                amount * 1000,
                                pollOption,
                                "",
                                context,
                                onError
                            )
                        }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults
                        .buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        )
                ) {
                    Text(
                        "⚡ ${showAmount(amount?.toBigDecimal()?.setScale(1))}",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (amount != null) {
                                    accountViewModel.zap(
                                        baseNote,
                                        amount * 1000,
                                        pollOption,
                                        "",
                                        context,
                                        onError
                                    )
                                }
                                onDismiss()
                            },
                            onLongClick = {}
                        )
                    )
                }
            }
        }
    }
}
