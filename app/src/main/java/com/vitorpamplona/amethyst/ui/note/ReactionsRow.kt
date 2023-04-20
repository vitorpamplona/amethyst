package com.vitorpamplona.amethyst.ui.note

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

@Composable
fun ReactionsRow(baseNote: Note, accountViewModel: AccountViewModel) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    var wantsToReplyTo by remember {
        mutableStateOf<Note?>(null)
    }

    var wantsToQuote by remember {
        mutableStateOf<Note?>(null)
    }

    if (wantsToReplyTo != null) {
        NewPostView({ wantsToReplyTo = null }, wantsToReplyTo, null, account)
    }

    if (wantsToQuote != null) {
        NewPostView({ wantsToQuote = null }, null, wantsToQuote, account)
    }

    Spacer(modifier = Modifier.height(8.dp))

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.height(20.dp),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center
    ) {
        items(5) {
            when (it) {
                0 -> Row(verticalAlignment = CenterVertically) {
                    ReplyReaction(baseNote, accountViewModel) {
                        wantsToReplyTo = baseNote
                    }
                }
                1 -> Row(verticalAlignment = CenterVertically) {
                    BoostReaction(baseNote, accountViewModel) {
                        wantsToQuote = baseNote
                    }
                }
                2 -> Row(verticalAlignment = CenterVertically) {
                    LikeReaction(baseNote, accountViewModel)
                }
                3 -> Row(verticalAlignment = CenterVertically) {
                    ZapReaction(baseNote, accountViewModel)
                }
                4 -> Row(verticalAlignment = CenterVertically) {
                    ViewCountReaction(baseNote.idHex)
                }
            }
        }
    }
}

@Composable
fun ReplyReaction(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    showCounter: Boolean = true,
    onPress: () -> Unit
) {
    val repliesState by baseNote.live().replies.observeAsState()
    val replies = repliesState?.note?.replies ?: emptySet()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    IconButton(
        modifier = Modifier.size(20.dp),
        onClick = {
            if (accountViewModel.isWriteable()) {
                onPress()
            } else {
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.login_with_a_private_key_to_be_able_to_reply),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    ) {
        ReplyIcon()
    }

    if (showCounter) {
        Text(
            " ${showCount(replies.size)}",
            fontSize = 14.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
        )
    }
}

@Composable
private fun ReplyIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_comment),
        null,
        modifier = Modifier.size(15.dp),
        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    )
}

@Composable
private fun BoostReaction(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    onQuotePress: () -> Unit
) {
    val boostsState by baseNote.live().boosts.observeAsState()
    val boostedNote = boostsState?.note

    val grayTint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var wantsToBoost by remember { mutableStateOf(false) }

    IconButton(
        modifier = Modifier.then(Modifier.size(20.dp)),
        onClick = {
            if (accountViewModel.isWriteable()) {
                if (accountViewModel.hasBoosted(baseNote)) {
                    accountViewModel.deleteBoostsTo(baseNote)
                } else {
                    wantsToBoost = true
                }
            } else {
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.login_with_a_private_key_to_be_able_to_boost_posts),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    ) {
        if (wantsToBoost) {
            BoostTypeChoicePopup(
                baseNote,
                accountViewModel,
                onDismiss = {
                    wantsToBoost = false
                },
                onQuote = {
                    wantsToBoost = false
                    onQuotePress()
                }
            )
        }

        if (boostedNote?.isBoostedBy(accountViewModel.userProfile()) == true) {
            Icon(
                painter = painterResource(R.drawable.ic_retweeted),
                null,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_retweet),
                null,
                modifier = Modifier.size(20.dp),
                tint = grayTint
            )
        }
    }

    Text(
        " ${showCount(boostedNote?.boosts?.size)}",
        fontSize = 14.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    )
}

@Composable
fun LikeReaction(
    baseNote: Note,
    accountViewModel: AccountViewModel
) {
    val reactionsState by baseNote.live().reactions.observeAsState()
    val reactedNote = reactionsState?.note ?: return

    val grayTint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    IconButton(
        modifier = Modifier.then(Modifier.size(20.dp)),
        onClick = {
            if (accountViewModel.isWriteable()) {
                if (accountViewModel.hasReactedTo(baseNote)) {
                    accountViewModel.deleteReactionTo(baseNote)
                } else {
                    accountViewModel.reactTo(baseNote)
                }
            } else {
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.login_with_a_private_key_to_like_posts),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    ) {
        if (reactedNote.isReactedBy(accountViewModel.userProfile())) {
            Icon(
                painter = painterResource(R.drawable.ic_liked),
                null,
                modifier = Modifier.size(16.dp),
                tint = Color.Unspecified
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_like),
                null,
                modifier = Modifier.size(16.dp),
                tint = grayTint
            )
        }
    }

    Text(
        " ${showCount(reactedNote.reactions.size)}",
        fontSize = 14.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ZapReaction(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    textModifier: Modifier = Modifier
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val zapsState by baseNote.live().zaps.observeAsState()
    val zappedNote = zapsState?.note
    val zapMessage = ""

    var wantsToZap by remember { mutableStateOf(false) }
    var wantsToChangeZapAmount by remember { mutableStateOf(false) }
    var wantsToSetCustomZap by remember { mutableStateOf(false) }
    val grayTint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var zappingProgress by remember { mutableStateOf(0f) }

    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .then(Modifier.size(20.dp))
            .combinedClickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 24.dp),
                onClick = {
                    if (account.zapAmountChoices.isEmpty()) {
                        scope.launch {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.no_zap_amount_setup_long_press_to_change),
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        }
                    } else if (!accountViewModel.isWriteable()) {
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
                        scope.launch(Dispatchers.IO) {
                            accountViewModel.zap(
                                baseNote,
                                account.zapAmountChoices.first() * 1000,
                                null,
                                zapMessage,
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
                                zapType = LnZapEvent.ZapType.PUBLIC
                            )
                        }
                    } else if (account.zapAmountChoices.size > 1) {
                        wantsToZap = true
                    }
                },
                onLongClick = {
                    wantsToChangeZapAmount = true
                },
                onDoubleClick = {
                    wantsToSetCustomZap = true
                }
            )
    ) {
        if (wantsToZap) {
            ZapAmountChoicePopup(
                baseNote,
                accountViewModel,
                onDismiss = {
                    wantsToZap = false
                    zappingProgress = 0f
                },
                onChangeAmount = {
                    wantsToZap = false
                    wantsToChangeZapAmount = true
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
        if (wantsToChangeZapAmount) {
            UpdateZapAmountDialog({ wantsToChangeZapAmount = false }, account = account)
        }

        if (wantsToSetCustomZap) {
            ZapCustomDialog({ wantsToSetCustomZap = false }, account = account, accountViewModel, baseNote)
        }

        if (zappedNote?.isZappedBy(account.userProfile()) == true) {
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
                    tint = grayTint
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

    var zapAmount by remember { mutableStateOf<BigDecimal?>(null) }

    LaunchedEffect(key1 = zapsState) {
        withContext(Dispatchers.IO) {
            zapAmount = zappedNote?.zappedAmount()
        }
    }

    Text(
        showAmount(zapAmount),
        fontSize = 14.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
        modifier = textModifier
    )
}

@Composable
private fun ViewCountReaction(idHex: String) {
    val uri = LocalUriHandler.current
    val grayTint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)

    IconButton(
        modifier = Modifier.size(20.dp),
        onClick = { uri.openUri("https://counter.amethyst.social/$idHex/") }
    ) {
        Icon(
            imageVector = Icons.Outlined.BarChart,
            null,
            modifier = Modifier.size(19.dp),
            tint = grayTint
        )
    }

    Row() {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("https://counter.amethyst.social/$idHex.svg?label=+&color=00000000")
                .diskCachePolicy(CachePolicy.DISABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = stringResource(R.string.view_count),
            modifier = Modifier.height(24.dp),
            colorFilter = ColorFilter.tint(grayTint)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoostTypeChoicePopup(baseNote: Note, accountViewModel: AccountViewModel, onDismiss: () -> Unit, onQuote: () -> Unit) {
    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -50),
        onDismissRequest = { onDismiss() }
    ) {
        FlowRow() {
            Button(
                modifier = Modifier.padding(horizontal = 3.dp),
                onClick = {
                    accountViewModel.boost(baseNote)
                    onDismiss()
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults
                    .buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
            ) {
                Text(stringResource(R.string.boost), color = Color.White, textAlign = TextAlign.Center)
            }

            Button(
                modifier = Modifier.padding(horizontal = 3.dp),
                onClick = onQuote,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults
                    .buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
            ) {
                Text(stringResource(R.string.quote), color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ZapAmountChoicePopup(
    baseNote: Note,
    accountViewModel: AccountViewModel,
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
                        scope.launch(Dispatchers.IO) {
                            accountViewModel.zap(
                                baseNote,
                                amountInSats * 1000,
                                null,
                                zapMessage,
                                context,
                                onError,
                                onProgress,
                                LnZapEvent.ZapType.PUBLIC
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
                                        null,
                                        zapMessage,
                                        context,
                                        onError,
                                        onProgress,
                                        LnZapEvent.ZapType.PUBLIC
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

fun showCount(count: Int?): String {
    if (count == null) return ""
    if (count == 0) return ""

    return when {
        count >= 1000000000 -> "${(count / 1000000000f).roundToInt()}G"
        count >= 1000000 -> "${(count / 1000000f).roundToInt()}M"
        count >= 1000 -> "${(count / 1000f).roundToInt()}k"
        else -> "$count"
    }
}

val OneGiga = BigDecimal(1000000000)
val OneMega = BigDecimal(1000000)
val OneKilo = BigDecimal(1000)

fun showAmount(amount: BigDecimal?): String {
    if (amount == null) return ""
    if (amount.abs() < BigDecimal(0.01)) return ""

    return when {
        amount >= OneGiga -> "%.1fG".format(amount.div(OneGiga).setScale(1, RoundingMode.HALF_UP))
        amount >= OneMega -> "%.1fM".format(amount.div(OneMega).setScale(1, RoundingMode.HALF_UP))
        amount >= OneKilo -> "%.1fk".format(amount.div(OneKilo).setScale(1, RoundingMode.HALF_UP))
        else -> "%.0f".format(amount)
    }
}
