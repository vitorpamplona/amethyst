package com.vitorpamplona.amethyst.ui.note

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProgressIndicatorDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.ripple.rememberRipple
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

@Composable
fun ReactionsRow(baseNote: Note, showReactions: Boolean, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val grayTint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)

    var wantsToSeeReactions = remember {
        mutableStateOf<Boolean>(false)
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(verticalAlignment = CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
        if (showReactions) {
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier
                    .width(65.dp)
                    .padding(start = 31.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                ExpandButton(baseNote, wantsToSeeReactions)
            }
        }

        Row(verticalAlignment = CenterVertically, modifier = remember { Modifier.weight(1f) }) {
            ReplyReactionWithDialog(accountViewModel, nav, baseNote, grayTint)
        }
        Row(verticalAlignment = CenterVertically, modifier = remember { Modifier.weight(1f) }) {
            BoostWithDialog(accountViewModel, nav, baseNote, grayTint)
        }
        Row(verticalAlignment = CenterVertically, modifier = remember { Modifier.weight(1f) }) {
            LikeReaction(baseNote, grayTint, accountViewModel)
        }
        Row(verticalAlignment = CenterVertically, modifier = remember { Modifier.weight(1f) }) {
            ZapReaction(baseNote, grayTint, accountViewModel)
        }
        Row(verticalAlignment = CenterVertically, modifier = remember { Modifier.weight(1f) }) {
            ViewCountReaction(baseNote.idHex, grayTint)
        }
    }

    if (showReactions && wantsToSeeReactions.value) {
        ReactionDetailGallery(baseNote, nav, accountViewModel)
    }
}

@Composable
private fun ExpandButton(baseNote: Note, wantsToSeeReactions: MutableState<Boolean>) {
    val zapsState by baseNote.live().zaps.observeAsState()
    val boostsState by baseNote.live().boosts.observeAsState()
    val reactionsState by baseNote.live().reactions.observeAsState()

    val hasReactions by remember(zapsState, boostsState, reactionsState) {
        derivedStateOf {
            baseNote.zaps.isNotEmpty() ||
                baseNote.boosts.isNotEmpty() ||
                baseNote.reactions.isNotEmpty()
        }
    }

    if (hasReactions) {
        IconButton(
            onClick = {
                wantsToSeeReactions.value = !wantsToSeeReactions.value
            },
            modifier = Modifier.size(20.dp)
        ) {
            if (wantsToSeeReactions.value) {
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.22f)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.22f)
                )
            }
        }
    }
}

@Composable
private fun ReactionDetailGallery(
    baseNote: Note,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    val zapsState by baseNote.live().zaps.observeAsState()
    val boostsState by baseNote.live().boosts.observeAsState()
    val reactionsState by baseNote.live().reactions.observeAsState()

    val hasReactions by remember(zapsState, boostsState, reactionsState) {
        derivedStateOf {
            baseNote.zaps.isNotEmpty() ||
                baseNote.boosts.isNotEmpty() ||
                baseNote.reactions.isNotEmpty()
        }
    }

    if (hasReactions) {
        Row(verticalAlignment = CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 5.dp)) {
            Column() {
                val zapEvents by remember(zapsState) { derivedStateOf { baseNote.zaps.toImmutableMap() } }
                val boostEvents by remember(boostsState) { derivedStateOf { baseNote.boosts.toImmutableList() } }
                val likeEvents by remember(reactionsState) { derivedStateOf { baseNote.reactions.toImmutableList() } }

                val hasZapEvents by remember(zapsState) { derivedStateOf { baseNote.zaps.isNotEmpty() } }
                val hasBoostEvents by remember(boostsState) { derivedStateOf { baseNote.boosts.isNotEmpty() } }
                val hasLikeEvents by remember(reactionsState) { derivedStateOf { baseNote.reactions.isNotEmpty() } }

                if (hasZapEvents) {
                    RenderZapGallery(
                        zapEvents,
                        MaterialTheme.colors.background,
                        nav,
                        accountViewModel
                    )
                }

                if (hasBoostEvents) {
                    RenderBoostGallery(
                        boostEvents,
                        MaterialTheme.colors.background,
                        nav,
                        accountViewModel
                    )
                }

                if (hasLikeEvents) {
                    RenderLikeGallery(
                        likeEvents,
                        MaterialTheme.colors.background,
                        nav,
                        accountViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun BoostWithDialog(
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    baseNote: Note,
    grayTint: Color
) {
    var wantsToQuote by remember {
        mutableStateOf<Note?>(null)
    }

    if (wantsToQuote != null) {
        NewPostView({ wantsToQuote = null }, null, wantsToQuote, accountViewModel, nav)
    }

    BoostReaction(baseNote, grayTint, accountViewModel) {
        wantsToQuote = baseNote
    }
}

@Composable
private fun ReplyReactionWithDialog(
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    baseNote: Note,
    grayTint: Color
) {
    var wantsToReplyTo by remember {
        mutableStateOf<Note?>(null)
    }

    if (wantsToReplyTo != null) {
        NewPostView({ wantsToReplyTo = null }, wantsToReplyTo, null, accountViewModel, nav)
    }

    ReplyReaction(baseNote, grayTint, accountViewModel) {
        wantsToReplyTo = baseNote
    }
}

@Composable
fun ReplyReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    showCounter: Boolean = true,
    iconSize: Dp = 20.dp,
    onPress: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val iconButtonModifier = remember {
        Modifier.size(iconSize)
    }

    val iconModifier = remember {
        Modifier.size(iconSize)
    }

    IconButton(
        modifier = iconButtonModifier,
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
        Icon(
            painter = painterResource(R.drawable.ic_comment),
            null,
            modifier = iconModifier,
            tint = grayTint
        )
    }

    if (showCounter) {
        ReplyCounter(baseNote, grayTint)
    }
}

@Composable
fun ReplyCounter(baseNote: Note, textColor: Color) {
    val repliesState by baseNote.live().replies.observeAsState()
    val replyCount by remember(repliesState) {
        derivedStateOf {
            " " + showCount(repliesState?.note?.replies?.size)
        }
    }

    Text(
        text = replyCount,
        fontSize = 14.sp,
        color = textColor
    )
}

@Composable
fun BoostReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    iconSize: Dp = 20.dp,
    onQuotePress: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var wantsToBoost by remember { mutableStateOf(false) }

    val iconButtonModifier = remember {
        Modifier.size(iconSize)
    }

    IconButton(
        modifier = iconButtonModifier,
        onClick = {
            if (accountViewModel.isWriteable()) {
                if (accountViewModel.hasBoosted(baseNote)) {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.deleteBoostsTo(baseNote)
                    }
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

        BoostIcon(baseNote, iconSize, grayTint, accountViewModel.userProfile())
    }

    BoostText(baseNote, grayTint)
}

@Composable
fun BoostIcon(baseNote: Note, iconSize: Dp = 20.dp, grayTint: Color, loggedIn: User) {
    val boostsState by baseNote.live().boosts.observeAsState()

    val iconTint by remember(boostsState) {
        derivedStateOf {
            if (boostsState?.note?.isBoostedBy(loggedIn) == true) Color.Unspecified else grayTint
        }
    }

    val iconModifier = remember {
        Modifier.size(iconSize)
    }

    Icon(
        painter = painterResource(R.drawable.ic_retweeted),
        null,
        modifier = iconModifier,
        tint = iconTint
    )
}

@Composable
fun BoostText(baseNote: Note, grayTint: Color) {
    val boostsState by baseNote.live().boosts.observeAsState()
    val boostCount by remember(boostsState) {
        derivedStateOf {
            " " + showCount(boostsState?.note?.boosts?.size)
        }
    }

    Text(
        boostCount,
        fontSize = 14.sp,
        color = grayTint
    )
}

@Composable
fun LikeReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    iconSize: Dp = 20.dp,
    heartSize: Dp = 16.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val iconButtonModifier = remember {
        Modifier.size(iconSize)
    }

    IconButton(
        modifier = iconButtonModifier,
        onClick = {
            if (accountViewModel.isWriteable()) {
                scope.launch(Dispatchers.IO) {
                    if (accountViewModel.hasReactedTo(baseNote)) {
                        accountViewModel.deleteReactionTo(baseNote)
                    } else {
                        accountViewModel.reactTo(baseNote)
                    }
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
        LikeIcon(baseNote, heartSize, grayTint, accountViewModel.userProfile())
    }

    LikeText(baseNote, grayTint)
}

@Composable
fun LikeIcon(baseNote: Note, iconSize: Dp = 20.dp, grayTint: Color, loggedIn: User) {
    val reactionsState by baseNote.live().reactions.observeAsState()

    var wasReactedByLoggedIn by remember(reactionsState) {
        mutableStateOf(false)
    }

    LaunchedEffect(key1 = reactionsState) {
        launch(Dispatchers.Default) {
            val newWasReactedByLoggedIn = reactionsState?.note?.isReactedBy(loggedIn) == true
            if (wasReactedByLoggedIn != newWasReactedByLoggedIn) {
                wasReactedByLoggedIn = newWasReactedByLoggedIn
            }
        }
    }

    val iconModifier = remember {
        Modifier.size(iconSize)
    }

    if (wasReactedByLoggedIn) {
        Icon(
            painter = painterResource(R.drawable.ic_liked),
            null,
            modifier = iconModifier,
            tint = Color.Unspecified
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.ic_like),
            null,
            modifier = iconModifier,
            tint = grayTint
        )
    }
}

@Composable
fun LikeText(baseNote: Note, grayTint: Color) {
    val reactionsState by baseNote.live().reactions.observeAsState()

    var reactionsCount by remember(reactionsState) {
        mutableStateOf("")
    }

    LaunchedEffect(key1 = reactionsState) {
        launch(Dispatchers.Default) {
            val newReactionsCount = " " + showCount(reactionsState?.note?.reactions?.size)
            if (reactionsCount != newReactionsCount) {
                reactionsCount = newReactionsCount
            }
        }
    }

    Text(
        reactionsCount,
        fontSize = 14.sp,
        color = grayTint
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ZapReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    iconSize: Dp = 20.dp,
    animationSize: Dp = 14.dp
) {
    var wantsToZap by remember { mutableStateOf(false) }
    var wantsToChangeZapAmount by remember { mutableStateOf(false) }
    var wantsToSetCustomZap by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var zappingProgress by remember { mutableStateOf(0f) }

    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .size(iconSize)
            .combinedClickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 24.dp),
                onClick = {
                    zapClick(
                        baseNote,
                        accountViewModel,
                        scope,
                        context,
                        onZappingProgress = { progress: Float ->
                            zappingProgress = progress
                        },
                        onMultipleChoices = {
                            wantsToZap = true
                        }
                    )
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
            UpdateZapAmountDialog({ wantsToChangeZapAmount = false }, accountViewModel = accountViewModel)
        }

        if (wantsToSetCustomZap) {
            ZapCustomDialog({ wantsToSetCustomZap = false }, accountViewModel, baseNote)
        }

        if (zappingProgress > 0.00001 && zappingProgress < 0.99999) {
            Spacer(Modifier.width(3.dp))

            val animatedProgress = animateFloatAsState(
                targetValue = zappingProgress,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
            ).value

            CircularProgressIndicator(
                progress = animatedProgress,
                modifier = remember { Modifier.size(animationSize) },
                strokeWidth = 2.dp
            )
        } else {
            ZapIcon(
                baseNote,
                iconSize,
                grayTint,
                accountViewModel
            )
        }
    }

    ZapAmountText(baseNote, grayTint, accountViewModel)
}

private fun zapClick(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    scope: CoroutineScope,
    context: Context,
    onZappingProgress: (Float) -> Unit,
    onMultipleChoices: () -> Unit
) {
    if (accountViewModel.account.zapAmountChoices.isEmpty()) {
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
    } else if (accountViewModel.account.zapAmountChoices.size == 1) {
        scope.launch(Dispatchers.IO) {
            accountViewModel.zap(
                baseNote,
                accountViewModel.account.zapAmountChoices.first() * 1000,
                null,
                "",
                context,
                onError = {
                    scope.launch {
                        onZappingProgress(0f)
                        Toast
                            .makeText(context, it, Toast.LENGTH_SHORT)
                            .show()
                    }
                },
                onProgress = {
                    scope.launch(Dispatchers.Main) {
                        onZappingProgress(it)
                    }
                },
                zapType = accountViewModel.account.defaultZapType
            )
        }
    } else if (accountViewModel.account.zapAmountChoices.size > 1) {
        onMultipleChoices()
    }
}

@Composable
private fun ZapIcon(
    baseNote: Note,
    iconSize: Dp,
    grayTint: Color,
    accountViewModel: AccountViewModel
) {
    var wasZappedByLoggedInUser by remember { mutableStateOf(false) }
    val zapsState by baseNote.live().zaps.observeAsState()

    LaunchedEffect(key1 = zapsState) {
        launch(Dispatchers.Default) {
            zapsState?.note?.let {
                if (!wasZappedByLoggedInUser) {
                    val newWasZapped = accountViewModel.calculateIfNoteWasZappedByAccount(it)

                    if (wasZappedByLoggedInUser != newWasZapped) {
                        wasZappedByLoggedInUser = newWasZapped
                    }
                }
            }
        }
    }

    if (wasZappedByLoggedInUser) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = stringResource(R.string.zaps),
            modifier = remember { Modifier.size(iconSize) },
            tint = BitcoinOrange
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Bolt,
            contentDescription = stringResource(id = R.string.zaps),
            modifier = remember { Modifier.size(iconSize) },
            tint = grayTint
        )
    }
}

@Composable
private fun ZapAmountText(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel
) {
    val zapsState by baseNote.live().zaps.observeAsState()

    var zapAmountTxt by remember { mutableStateOf("") }

    LaunchedEffect(key1 = zapsState) {
        launch(Dispatchers.Default) {
            zapsState?.note?.let {
                val newZapAmount = showAmount(accountViewModel.calculateZapAmount(it))
                if (newZapAmount != zapAmountTxt) {
                    zapAmountTxt = newZapAmount
                }
            }
        }
    }

    Text(
        zapAmountTxt,
        fontSize = 14.sp,
        color = grayTint
    )
}

@Composable
fun ViewCountReaction(
    idHex: String,
    grayTint: Color,
    iconSize: Dp = 20.dp,
    barChartSize: Dp = 19.dp,
    numberSize: Dp = 24.dp
) {
    val uri = LocalUriHandler.current
    val context = LocalContext.current

    val iconButtonModifier = remember {
        Modifier.size(barChartSize)
    }

    val iconModifier = remember {
        Modifier.height(numberSize)
    }

    val colorFilter = remember {
        ColorFilter.tint(grayTint)
    }

    IconButton(
        modifier = Modifier.size(iconSize),
        onClick = { uri.openUri("https://counter.amethyst.social/$idHex/") }
    ) {
        Icon(
            imageVector = Icons.Outlined.BarChart,
            null,
            modifier = iconButtonModifier,
            tint = grayTint
        )
    }

    val request = remember {
        ImageRequest.Builder(context)
            .data("https://counter.amethyst.social/$idHex.svg?label=+&color=00000000")
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Row {
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.view_count),
            modifier = iconModifier,
            colorFilter = colorFilter
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
        FlowRow {
            val scope = rememberCoroutineScope()
            Button(
                modifier = Modifier.padding(horizontal = 3.dp),
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.boost(baseNote)
                        onDismiss()
                    }
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
                                        null,
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
