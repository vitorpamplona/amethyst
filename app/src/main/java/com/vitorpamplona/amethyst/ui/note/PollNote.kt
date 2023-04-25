package com.vitorpamplona.amethyst.ui.note

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.roundToInt

@Composable
fun PollNote(
    baseNote: Note,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val zapsState by baseNote.live().zaps.observeAsState()
    val zappedNote = zapsState?.note ?: return

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val pollViewModel = PollNoteViewModel()
    pollViewModel.account = account
    pollViewModel.load(zappedNote)

    pollViewModel.pollEvent?.pollOptions()?.forEach { poll_op ->
        val optionTally = pollViewModel.optionVoteTally(poll_op.key)
        val color = if (
            pollViewModel.consensusThreshold != null &&
            optionTally >= pollViewModel.consensusThreshold!!
        ) {
            Color.Green.copy(alpha = 0.32f)
        } else {
            MaterialTheme.colors.primary.copy(alpha = 0.32f)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 3.dp)
        ) {
            if (pollViewModel.canZap()) {
                ZapVote(
                    baseNote,
                    accountViewModel,
                    pollViewModel,
                    poll_op.key,
                    nonClickablePrepend = {
                        Box(
                            Modifier.fillMaxWidth(0.75f).clip(shape = RoundedCornerShape(15.dp))
                                .border(
                                    2.dp,
                                    color,
                                    RoundedCornerShape(15.dp)
                                )
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier.matchParentSize(),
                                color = color,
                                progress = optionTally.toFloat()
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.padding(horizontal = 10.dp).width(40.dp)
                                ) {
                                    Text(
                                        text = "${(optionTally.toFloat() * 100).roundToInt()}%",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(modifier = Modifier.fillMaxWidth().padding(15.dp)) {
                                    TranslatableRichTextViewer(
                                        poll_op.value,
                                        canPreview,
                                        Modifier,
                                        pollViewModel.pollEvent?.tags(),
                                        backgroundColor,
                                        accountViewModel,
                                        navController
                                    )
                                }
                            }
                        }
                    },
                    clickablePrepend = {
                    }
                )
            } else {
                ZapVote(
                    baseNote,
                    accountViewModel,
                    pollViewModel,
                    poll_op.key,
                    nonClickablePrepend = {},
                    clickablePrepend = {
                        Box(
                            Modifier.fillMaxWidth(0.75f)
                                .clip(shape = RoundedCornerShape(15.dp))
                                .border(
                                    2.dp,
                                    MaterialTheme.colors.primary,
                                    RoundedCornerShape(15.dp)
                                )
                        ) {
                            TranslatableRichTextViewer(
                                poll_op.value,
                                canPreview,
                                Modifier.padding(15.dp),
                                pollViewModel.pollEvent?.tags(),
                                backgroundColor,
                                accountViewModel,
                                navController
                            )
                        }
                    }
                )
            }
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
    modifier: Modifier = Modifier,
    nonClickablePrepend: @Composable () -> Unit,
    clickablePrepend: @Composable () -> Unit
) {
    val zapsState by baseNote.live().zaps.observeAsState()
    val zappedNote = zapsState?.note

    var wantsToZap by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var zappingProgress by remember { mutableStateOf(0f) }

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    nonClickablePrepend()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.combinedClickable(
            role = Role.Button,
            // interactionSource = remember { MutableInteractionSource() },
            // indication = rememberRipple(bounded = false, radius = 24.dp),
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
                } else if (pollViewModel.isPollClosed()) {
                    scope.launch {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.poll_is_closed),
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    }
                } else if (accountViewModel.isLoggedUser(zappedNote?.author)) {
                    scope.launch {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.poll_author_no_vote),
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    }
                } else if (pollViewModel.isVoteAmountAtomic() && pollViewModel.isPollOptionZappedBy(pollOption, accountViewModel.userProfile())) {
                    // only allow one vote per option when min==max, i.e. atomic vote amount specified
                    scope.launch {
                        Toast
                            .makeText(
                                context,
                                R.string.one_vote_per_user_on_atomic_votes,
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    }
                    return@combinedClickable
                } else if (account.zapAmountChoices.size == 1 && pollViewModel.isValidInputVoteAmount(account.zapAmountChoices.first())) {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.zap(
                            baseNote,
                            account.zapAmountChoices.first() * 1000,
                            pollOption,
                            "",
                            context,
                            onError = {
                                scope.launch {
                                    zappingProgress = 0f
                                    Toast
                                        .makeText(context, it, Toast.LENGTH_SHORT)
                                        .show()
                                }
                            },
                            onProgress = {
                                scope.launch(Dispatchers.Main) {
                                    zappingProgress = it
                                }
                            },
                            zapType = account.defaultZapType
                        )
                    }
                } else {
                    wantsToZap = true
                }
            }
        )
    ) {
        if (wantsToZap) {
            FilteredZapAmountChoicePopup(
                baseNote,
                accountViewModel,
                pollViewModel,
                pollOption,
                onDismiss = {
                    wantsToZap = false
                    zappingProgress = 0f
                },
                onChangeAmount = {
                    wantsToZap = false
                },
                onError = {
                    scope.launch {
                        zappingProgress = 0f
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    }
                },
                onProgress = {
                    scope.launch(Dispatchers.Main) {
                        zappingProgress = it
                    }
                }
            )
        }

        clickablePrepend()

        var optionWasZappedByLoggedInUser by remember { mutableStateOf(false) }

        LaunchedEffect(key1 = zappedNote) {
            withContext(Dispatchers.IO) {
                if (!optionWasZappedByLoggedInUser) {
                    optionWasZappedByLoggedInUser = pollViewModel.isPollOptionZappedBy(pollOption, accountViewModel.userProfile())
                }
            }
        }

        if (optionWasZappedByLoggedInUser) {
            zappingProgress = 1f
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = stringResource(R.string.zaps),
                modifier = Modifier.size(20.dp),
                tint = BitcoinOrange
            )
        } else {
            if (zappingProgress < 0.1 || zappingProgress > 0.99) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = stringResource(id = R.string.zaps),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            } else {
                Spacer(Modifier.width(3.dp))
                CircularProgressIndicator(
                    progress = zappingProgress,
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }

    var wasZappedByLoggedInUser by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = zappedNote) {
        withContext(Dispatchers.IO) {
            if (!wasZappedByLoggedInUser) {
                wasZappedByLoggedInUser = zappedNote?.isZappedBy(accountViewModel.userProfile(), account) == true
            }
        }
    }

    // only show tallies after a user has zapped note
    if (baseNote.author == accountViewModel.userProfile() || wasZappedByLoggedInUser) {
        Text(
            showAmount(pollViewModel.zappedPollOptionAmount(pollOption)),
            fontSize = 14.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FilteredZapAmountChoicePopup(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    pollViewModel: PollNoteViewModel,
    pollOption: Int,
    onDismiss: () -> Unit,
    onChangeAmount: () -> Unit,
    onError: (text: String) -> Unit,
    onProgress: (percent: Float) -> Unit
) {
    val context = LocalContext.current

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return
    val zapMessage = ""
    val scope = rememberCoroutineScope()

    val options = account.zapAmountChoices.filter { pollViewModel.isValidInputVoteAmount(it) }.toMutableList()
    if (options.isEmpty()) {
        pollViewModel.valueMinimum?.let { minimum ->
            pollViewModel.valueMaximum?.let { maximum ->
                if (minimum != maximum) {
                    options.add(((minimum + maximum) / 2).toLong())
                }
            }
        }
    }
    pollViewModel.valueMinimum?.let { options.add(it.toLong()) }
    pollViewModel.valueMaximum?.let { options.add(it.toLong()) }
    val sortedOptions = options.toSet().sorted()

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -100),
        onDismissRequest = { onDismiss() }
    ) {
        FlowRow(horizontalArrangement = Arrangement.Center) {
            sortedOptions.forEach { amountInSats ->
                Button(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            accountViewModel.zap(
                                baseNote,
                                amountInSats * 1000,
                                pollOption,
                                zapMessage,
                                context,
                                onError,
                                onProgress,
                                account.defaultZapType
                            )
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults
                        .buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        )
                ) {
                    Text(
                        "⚡ ${showAmount(amountInSats.toBigDecimal().setScale(1))}",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    accountViewModel.zap(
                                        baseNote,
                                        amountInSats * 1000,
                                        pollOption,
                                        zapMessage,
                                        context,
                                        onError,
                                        onProgress,
                                        account.defaultZapType
                                    )
                                    onDismiss()
                                }
                            },
                            onLongClick = {
                                onChangeAmount()
                            }
                        )
                    )
                }
            }
        }
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
    onError: (text: String) -> Unit,
    onProgress: (percent: Float) -> Unit
) {
    val context = LocalContext.current

    var inputAmountText by rememberSaveable { mutableStateOf("") }

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
                var amount = pollViewModel.inputVoteAmountLong(inputAmountText)

                // only prompt for input amount if vote is not atomic
                if (!pollViewModel.isVoteAmountAtomic()) {
                    OutlinedTextField(
                        value = inputAmountText,
                        onValueChange = { inputAmountText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(150.dp),
                        colors = if (pollViewModel.isValidInputVoteAmount(amount)) colorValid else colorInValid,
                        label = {
                            Text(
                                text = stringResource(R.string.poll_zap_amount),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        placeholder = {
                            Text(
                                text = pollViewModel.voteAmountPlaceHolderText(context.getString(R.string.sats)),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        }
                    )
                } else { amount = pollViewModel.valueMaximum?.toLong() }

                val isValidInputAmount = pollViewModel.isValidInputVoteAmount(amount)
                Button(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    enabled = isValidInputAmount,
                    onClick = {
                        if (amount != null && isValidInputAmount) {
                            accountViewModel.zap(
                                baseNote,
                                amount * 1000,
                                pollOption,
                                "",
                                context,
                                onError,
                                onProgress
                            )
                            onDismiss()
                        }
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
                                if (amount != null && isValidInputAmount) {
                                    accountViewModel.zap(
                                        baseNote,
                                        amount * 1000,
                                        pollOption,
                                        "",
                                        context,
                                        onError,
                                        onProgress
                                    )
                                    onDismiss()
                                }
                            },
                            onLongClick = {}
                        )
                    )
                }
            }
        }
    }
}
