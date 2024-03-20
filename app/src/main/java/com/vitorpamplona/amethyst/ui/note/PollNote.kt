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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.routeToMessage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.StringToastMsg
import com.vitorpamplona.amethyst.ui.theme.BigPadding
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PollNote(
    baseNote: Note,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val pollViewModel: PollNoteViewModel = viewModel(key = "PollNoteViewModel${baseNote.idHex}")

    pollViewModel.load(accountViewModel.account, baseNote)

    PollNote(
        baseNote = baseNote,
        pollViewModel = pollViewModel,
        canPreview = canPreview,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun PollNote(
    baseNote: Note,
    pollViewModel: PollNoteViewModel,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    WatchZapsAndUpdateTallies(baseNote, pollViewModel)

    pollViewModel.tallies.forEach { option ->
        OptionNote(
            option,
            pollViewModel,
            baseNote,
            accountViewModel,
            canPreview,
            backgroundColor,
            nav,
        )
    }
}

@Composable
private fun WatchZapsAndUpdateTallies(
    baseNote: Note,
    pollViewModel: PollNoteViewModel,
) {
    val zapsState by baseNote.live().zaps.observeAsState()

    LaunchedEffect(key1 = zapsState) { pollViewModel.refreshTallies() }
}

@Composable
private fun OptionNote(
    poolOption: PollOption,
    pollViewModel: PollNoteViewModel,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
) {
    val tags = remember(baseNote) { baseNote.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        if (!pollViewModel.canZap.value) {
            val color =
                if (poolOption.consensusThreadhold.value) {
                    Color.Green.copy(alpha = 0.32f)
                } else {
                    MaterialTheme.colorScheme.mediumImportanceLink
                }

            ZapVote(
                baseNote,
                poolOption,
                pollViewModel = pollViewModel,
                nonClickablePrepend = {
                    RenderOptionAfterVote(
                        poolOption,
                        color,
                        canPreview,
                        tags,
                        backgroundColor,
                        accountViewModel,
                        nav,
                    )
                },
                clickablePrepend = {},
                accountViewModel = accountViewModel,
                nav = nav,
            )
        } else {
            ZapVote(
                baseNote,
                poolOption,
                pollViewModel = pollViewModel,
                nonClickablePrepend = {},
                clickablePrepend = {
                    RenderOptionBeforeVote(
                        poolOption.descriptor,
                        canPreview,
                        tags,
                        backgroundColor,
                        accountViewModel,
                        nav,
                    )
                },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun RenderOptionAfterVote(
    poolOption: PollOption,
    color: Color,
    canPreview: Boolean,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth(0.75f)
            .clip(shape = QuoteBorder)
            .border(
                2.dp,
                color,
                QuoteBorder,
            ),
    ) {
        LinearProgressIndicator(
            modifier = Modifier.matchParentSize(),
            color = color,
            progress = {
                poolOption.tally.value.toFloat()
            },
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier =
                    remember {
                        Modifier
                            .padding(horizontal = 10.dp)
                            .width(45.dp)
                    },
            ) {
                Text(
                    text = "${(poolOption.tally.value.toFloat() * 100).roundToInt()}%",
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier =
                    remember {
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 15.dp, horizontal = 10.dp)
                    },
            ) {
                TranslatableRichTextViewer(
                    poolOption.descriptor,
                    canPreview,
                    quotesLeft = 1,
                    Modifier,
                    tags,
                    backgroundColor,
                    poolOption.descriptor,
                    accountViewModel,
                    nav,
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
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth(0.75f)
            .clip(shape = QuoteBorder)
            .border(
                2.dp,
                MaterialTheme.colorScheme.primary,
                QuoteBorder,
            ),
    ) {
        TranslatableRichTextViewer(
            content = description,
            canPreview = canPreview,
            quotesLeft = 1,
            modifier = BigPadding,
            tags = tags,
            backgroundColor = backgroundColor,
            id = description,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ZapVote(
    baseNote: Note,
    poolOption: PollOption,
    modifier: Modifier = Modifier,
    pollViewModel: PollNoteViewModel,
    nonClickablePrepend: @Composable () -> Unit,
    clickablePrepend: @Composable () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val isLoggedUser by remember { derivedStateOf { accountViewModel.isLoggedUser(baseNote.author) } }

    var wantsToZap by remember { mutableStateOf(false) }
    var wantsToPay by remember {
        mutableStateOf<ImmutableList<ZapPaymentHandler.Payable>>(
            persistentListOf(),
        )
    }

    var zappingProgress by remember { mutableStateOf(0f) }
    var showErrorMessageDialog by remember { mutableStateOf<StringToastMsg?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    nonClickablePrepend()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.combinedClickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 24.dp),
                onClick = {
                    if (!accountViewModel.isWriteable()) {
                        accountViewModel.toast(
                            R.string.read_only_user,
                            R.string.login_with_a_private_key_to_be_able_to_send_zaps,
                        )
                    } else if (pollViewModel.isPollClosed()) {
                        accountViewModel.toast(
                            R.string.poll_unable_to_vote,
                            R.string.poll_is_closed_explainer,
                        )
                    } else if (isLoggedUser) {
                        accountViewModel.toast(
                            R.string.poll_unable_to_vote,
                            R.string.poll_author_no_vote,
                        )
                    } else if (pollViewModel.isVoteAmountAtomic() && poolOption.zappedByLoggedIn.value) {
                        // only allow one vote per option when min==max, i.e. atomic vote amount specified
                        accountViewModel.toast(
                            R.string.poll_unable_to_vote,
                            R.string.one_vote_per_user_on_atomic_votes,
                        )
                        return@combinedClickable
                    } else if (
                        accountViewModel.account.zapAmountChoices.size == 1 &&
                        pollViewModel.isValidInputVoteAmount(
                            accountViewModel.account.zapAmountChoices.first(),
                        )
                    ) {
                        accountViewModel.zap(
                            baseNote,
                            accountViewModel.account.zapAmountChoices.first() * 1000,
                            poolOption.option,
                            "",
                            context,
                            onError = { title, message ->
                                zappingProgress = 0f
                                showErrorMessageDialog = StringToastMsg(title, message)
                            },
                            onProgress = { scope.launch(Dispatchers.Main) { zappingProgress = it } },
                            onPayViaIntent = {},
                            zapType = accountViewModel.account.defaultZapType,
                        )
                    } else {
                        wantsToZap = true
                    }
                },
            ),
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
                onChangeAmount = { wantsToZap = false },
                onError = { title, message ->
                    showErrorMessageDialog = StringToastMsg(title, message)
                    zappingProgress = 0f
                },
                onProgress = { scope.launch(Dispatchers.Main) { zappingProgress = it } },
                onPayViaIntent = { wantsToPay = it },
            )
        }

        if (wantsToPay.isNotEmpty()) {
            PayViaIntentDialog(
                payingInvoices = wantsToPay,
                accountViewModel = accountViewModel,
                onClose = { wantsToPay = persistentListOf() },
                onError = {
                    wantsToPay = persistentListOf()
                    scope.launch {
                        zappingProgress = 0f
                        showErrorMessageDialog =
                            StringToastMsg(
                                context.getString(R.string.error_dialog_zap_error),
                                it,
                            )
                    }
                },
            )
        }

        showErrorMessageDialog?.let { toast ->
            ErrorMessageDialog(
                title = toast.title,
                textContent = toast.msg,
                onClickStartMessage = {
                    baseNote.author?.let { nav(routeToMessage(it, toast.msg, accountViewModel)) }
                },
                onDismiss = { showErrorMessageDialog = null },
            )
        }

        clickablePrepend()

        if (poolOption.zappedByLoggedIn.value) {
            zappingProgress = 1f
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = stringResource(R.string.zaps),
                modifier = Modifier.size(20.dp),
                tint = BitcoinOrange,
            )
        } else {
            if (zappingProgress < 0.1 || zappingProgress > 0.99) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = stringResource(id = R.string.zaps),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            } else {
                Spacer(Modifier.width(3.dp))
                CircularProgressIndicator(
                    progress = zappingProgress,
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }

    // only show tallies after a user has zapped note
    if (!pollViewModel.canZap.value) {
        val amountStr = remember(poolOption.zappedValue.value) { showAmount(poolOption.zappedValue.value) }
        Text(
            text = amountStr,
            fontSize = Font14SP,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = modifier,
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
    onError: (title: String, text: String) -> Unit,
    onProgress: (percent: Float) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
) {
    val context = LocalContext.current

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val defaultZapType by
        remember(accountState) {
            derivedStateOf { accountState?.account?.defaultZapType ?: LnZapEvent.ZapType.PRIVATE }
        }

    val zapMessage = ""

    val sortedOptions =
        remember(accountState) { pollViewModel.createZapOptionsThatMatchThePollingParameters() }

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -100),
        onDismissRequest = { onDismiss() },
    ) {
        FlowRow(horizontalArrangement = Arrangement.Center) {
            sortedOptions.forEach { amountInSats ->
                val zapAmount = remember { "⚡ ${showAmount(amountInSats.toBigDecimal().setScale(1))}" }

                Button(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    onClick = {
                        accountViewModel.zap(
                            baseNote,
                            amountInSats * 1000,
                            pollOption,
                            zapMessage,
                            context,
                            onError,
                            onProgress,
                            onPayViaIntent,
                            defaultZapType,
                        )
                        onDismiss()
                    },
                    shape = ButtonBorder,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Text(
                        text = zapAmount,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier.combinedClickable(
                                onClick = {
                                    accountViewModel.zap(
                                        baseNote,
                                        amountInSats * 1000,
                                        pollOption,
                                        zapMessage,
                                        context,
                                        onError,
                                        onProgress,
                                        onPayViaIntent,
                                        defaultZapType,
                                    )
                                    onDismiss()
                                },
                                onLongClick = { onChangeAmount() },
                            ),
                    )
                }
            }
        }
    }
}
