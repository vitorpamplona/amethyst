package com.vitorpamplona.amethyst.ui.note

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.ui.components.TranslateableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.launch

@Composable
fun PollNote(
    note: Note,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val pollEvent = note.event as PollNoteEvent

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
                    } else if (account.zapAmountChoices.size == 1) {
                        accountViewModel.zap(
                            baseNote,
                            account.zapAmountChoices.first() * 1000,
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
                    } else if (account.zapAmountChoices.size > 1) {
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

        if (zappedNote?.isZappedBy(account.userProfile()) == true) {
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
        showAmount(zappedNote?.zappedAmount()),
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
    onDismiss: () -> Unit,
    onError: (text: String) -> Unit
) {
    val context = LocalContext.current

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -50),
        onDismissRequest = { onDismiss() }
    ) {
        FlowRow(horizontalArrangement = Arrangement.Center) {
            account.zapAmountChoices.forEach { amountInSats ->
                Button(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    onClick = {
                        accountViewModel.zap(baseNote, amountInSats * 1000, pollOption, "", context, onError)
                        onDismiss()
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
                                accountViewModel.zap(baseNote, amountInSats * 1000, pollOption, "", context, onError)
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
