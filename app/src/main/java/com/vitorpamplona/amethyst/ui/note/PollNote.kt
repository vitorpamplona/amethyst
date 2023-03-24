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
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
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
    val pollEvent = note.event as PollNoteEvent
    val consensusThreshold = pollEvent.consensusThreshold()

    pollEvent.pollOptions().forEach { poll_op ->
        Row(Modifier.fillMaxWidth()) {
            val modifier = Modifier
                .weight(1f)
                .border(BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.32f)))
                .padding(4.dp)

            TranslateableRichTextViewer(
                poll_op.value,
                canPreview,
                modifier,
                pollEvent.tags(),
                backgroundColor,
                accountViewModel,
                navController
            )

            ZapVote(note, accountViewModel, poll_op.key)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ZapVote(
    baseNote: Note,
    accountViewModel: AccountViewModel,
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

    val pollEvent = baseNote.event as PollNoteEvent
    val valueMaximum = pollEvent.valueMaximum()
    val valueMinimum = pollEvent.valueMinimum()

    val isPollClosed: Boolean = pollEvent.closedAt()?.let { // allow 2 minute leeway for zap to propagate
        baseNote.createdAt()?.plus(it * (86400 + 120))!! > Date().time / 1000
    } == true
    val isVoteAmountAtomic = valueMaximum != null && valueMinimum != null && valueMinimum == valueMaximum

    Row(
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
                    } else if (isPollClosed) {
                        scope.launch {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.poll_is_closed),
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        }
                    } else if (isVoteAmountAtomic) {
                        accountViewModel.zap(
                            baseNote,
                            valueMaximum!!.toLong() * 1000,
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
                pollOption,
                valueMinimum,
                valueMaximum,
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

        if (zappedNote?.isPollOptionZapped(pollOption) == true) {
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

    Text(
        showAmount(zappedNote?.zappedPollOptionAmount(pollOption)),
        fontSize = 14.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ZapVoteAmountChoicePopup(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    pollOption: Int,
    valueMinimum: Int?,
    valueMaximum: Int?,
    onDismiss: () -> Unit,
    onError: (text: String) -> Unit
) {
    val context = LocalContext.current

    var textAmount by rememberSaveable { mutableStateOf("") }

    val placeHolderText = if (valueMinimum == null && valueMaximum == null) {
        stringResource(R.string.sats)
    } else if (valueMinimum == null) {
        "1—$valueMaximum " + stringResource(R.string.sats)
    } else if (valueMaximum == null) {
        ">$valueMinimum " + stringResource(R.string.sats)
    } else {
        "$valueMinimum—$valueMaximum " + stringResource(R.string.sats)
    }

    val amount = if (textAmount.isEmpty()) { null } else {
        try {
            textAmount.toLong()
        } catch (e: Exception) { null }
    }

    var isValidAmount = false
    if (amount == null) {
        isValidAmount = false
    } else if (valueMinimum == null && valueMaximum == null) {
        if (amount > 0) {
            isValidAmount = true
        }
    } else if (valueMinimum == null) {
        if (amount > 0 && amount <= valueMaximum!!) {
            isValidAmount = true
        }
    } else if (valueMaximum == null) {
        if (amount >= valueMinimum) {
            isValidAmount = true
        }
    } else {
        if ((valueMinimum <= amount) && (amount <= valueMaximum)) {
            isValidAmount = true
        }
    }

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
                modifier = Modifier
                    .background(MaterialTheme.colors.primary)
                    .padding(10.dp)
            ) {
                OutlinedTextField(
                    value = textAmount,
                    onValueChange = { textAmount = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(150.dp),
                    colors = if (isValidAmount) colorValid else colorInValid,
                    label = {
                        Text(
                            text = stringResource(R.string.poll_zap_amount),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    placeholder = {
                        Text(
                            text = placeHolderText,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    }
                )

                if (amount != null && isValidAmount) {
                    Button(
                        modifier = Modifier.padding(horizontal = 3.dp),
                        onClick = {
                            accountViewModel.zap(
                                baseNote,
                                amount * 1000,
                                pollOption,
                                "",
                                context,
                                onError
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults
                            .buttonColors(
                                backgroundColor = MaterialTheme.colors.primary
                            )
                    ) {
                        Text(
                            "⚡ ${showAmount(amount.toBigDecimal().setScale(1))}",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    accountViewModel.zap(
                                        baseNote,
                                        amount * 1000,
                                        pollOption,
                                        "",
                                        context,
                                        onError
                                    )
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
}
