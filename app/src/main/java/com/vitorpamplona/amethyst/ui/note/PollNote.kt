package com.vitorpamplona.amethyst.ui.note

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

@Composable
fun PollNote(
    baseNote: Note,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val pollViewModel: PollNoteViewModel = viewModel(
        key = baseNote.idHex + "PollNoteViewModel"
    )

    pollViewModel.load(accountViewModel.account, baseNote)

    PollNote(
        baseNote = baseNote,
        pollViewModel = pollViewModel,
        canPreview = canPreview,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav
    )
}

@Composable
fun PollNote(
    baseNote: Note,
    pollViewModel: PollNoteViewModel,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    WatchZapsAndUpdateTallies(baseNote, pollViewModel)

    val tallies by pollViewModel.tallies.collectAsState()

    tallies.forEach { poll_op ->
        OptionNote(
            poll_op,
            pollViewModel,
            baseNote,
            accountViewModel,
            canPreview,
            backgroundColor,
            nav
        )
    }
}

@Composable
private fun WatchZapsAndUpdateTallies(
    baseNote: Note,
    pollViewModel: PollNoteViewModel
) {
    val zapsState by baseNote.live().zaps.observeAsState()

    LaunchedEffect(key1 = zapsState) {
        launch(Dispatchers.Default) {
            pollViewModel.refreshTallies()
        }
    }
}

@Composable
private fun OptionNote(
    poolOption: PollOption,
    pollViewModel: PollNoteViewModel,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    canPreview: Boolean,
    backgroundColor: Color,
    nav: (String) -> Unit
) {
    val tags = remember(baseNote) {
        baseNote.event?.tags()?.toImmutableListOfLists() ?: ImmutableListOfLists()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        if (!pollViewModel.canZap()) {
            val color = if (poolOption.consensusThreadhold) {
                Color.Green.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colors.primary.copy(alpha = 0.32f)
            }

            ZapVote(
                baseNote,
                poolOption,
                accountViewModel,
                pollViewModel,
                nonClickablePrepend = {
                    RenderOptionAfterVote(
                        poolOption.descriptor,
                        poolOption.tally.toFloat(),
                        color,
                        canPreview,
                        tags,
                        backgroundColor,
                        accountViewModel,
                        nav
                    )
                },
                clickablePrepend = {
                }
            )
        } else {
            ZapVote(
                baseNote,
                poolOption,
                accountViewModel,
                pollViewModel,
                nonClickablePrepend = {},
                clickablePrepend = {
                    RenderOptionBeforeVote(poolOption.descriptor, canPreview, tags, backgroundColor, accountViewModel, nav)
                }
            )
        }
    }
}

@Composable
private fun RenderOptionAfterVote(
    description: String,
    totalRatio: Float,
    color: Color,
    canPreview: Boolean,
    tags: ImmutableListOfLists<String>,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val totalPercentage = remember(totalRatio) {
        "${(totalRatio * 100).roundToInt()}%"
    }

    Box(
        Modifier
            .fillMaxWidth(0.75f)
            .clip(shape = RoundedCornerShape(15.dp))
            .border(
                2.dp,
                color,
                RoundedCornerShape(15.dp)
            )
    ) {
        LinearProgressIndicator(
            modifier = Modifier.matchParentSize(),
            color = color,
            progress = totalRatio
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = remember {
                    Modifier
                        .padding(horizontal = 10.dp)
                        .width(40.dp)
                }
            ) {
                Text(
                    text = totalPercentage,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = remember {
                    Modifier
                        .fillMaxWidth()
                        .padding(15.dp)
                }
            ) {
                TranslatableRichTextViewer(
                    description,
                    canPreview,
                    remember { Modifier },
                    tags,
                    backgroundColor,
                    accountViewModel,
                    nav
                )
            }
        }
    }
}

@Composable
private fun RenderOptionBeforeVote(
    description: String,
    canPreview: Boolean,
    tags: ImmutableListOfLists<String>,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth(0.75f)
            .clip(shape = RoundedCornerShape(15.dp))
            .border(
                2.dp,
                MaterialTheme.colors.primary,
                RoundedCornerShape(15.dp)
            )
    ) {
        TranslatableRichTextViewer(
            description,
            canPreview,
            remember { Modifier.padding(15.dp) },
            tags,
            backgroundColor,
            accountViewModel,
            nav
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ZapVote(
    baseNote: Note,
    poolOption: PollOption,
    accountViewModel: AccountViewModel,
    pollViewModel: PollNoteViewModel,
    modifier: Modifier = Modifier,
    nonClickablePrepend: @Composable () -> Unit,
    clickablePrepend: @Composable () -> Unit
) {
    val isLoggedUser by remember {
        derivedStateOf {
            accountViewModel.isLoggedUser(baseNote.author)
        }
    }

    var wantsToZap by remember { mutableStateOf(false) }
    var zappingProgress by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                } else if (isLoggedUser) {
                    scope.launch {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.poll_author_no_vote),
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    }
                } else if (pollViewModel.isVoteAmountAtomic() && poolOption.zappedByLoggedIn) {
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
                } else if (accountViewModel.account.zapAmountChoices.size == 1 &&
                    pollViewModel.isValidInputVoteAmount(accountViewModel.account.zapAmountChoices.first())
                ) {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.zap(
                            baseNote,
                            accountViewModel.account.zapAmountChoices.first() * 1000,
                            poolOption.option,
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
                            zapType = accountViewModel.account.defaultZapType
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
                poolOption.option,
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

        if (poolOption.zappedByLoggedIn) {
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

    // only show tallies after a user has zapped note
    if (!pollViewModel.canZap()) {
        Text(
            showAmount(poolOption.zappedValue),
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
    val defaultZapType by remember(accountState) {
        derivedStateOf {
            accountState?.account?.defaultZapType ?: LnZapEvent.ZapType.PRIVATE
        }
    }

    val zapMessage = ""
    val scope = rememberCoroutineScope()

    val sortedOptions = remember(accountState) {
        pollViewModel.createZapOptionsThatMatchThePollingParameters()
    }

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -100),
        onDismissRequest = { onDismiss() }
    ) {
        FlowRow(horizontalArrangement = Arrangement.Center) {
            sortedOptions.forEach { amountInSats ->
                val zapAmount = remember {
                    "⚡ ${showAmount(amountInSats.toBigDecimal().setScale(1))}"
                }

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
                                defaultZapType
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
                        text = zapAmount,
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
                                        defaultZapType
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
