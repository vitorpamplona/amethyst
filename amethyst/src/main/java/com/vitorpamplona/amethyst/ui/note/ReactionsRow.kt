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

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.InLineIconRenderer
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.routeToMessage
import com.vitorpamplona.amethyst.ui.note.types.EditState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DarkerGreen
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.HalfDoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.ui.theme.ModifierWidth3dp
import com.vitorpamplona.amethyst.ui.theme.NoSoTinyBorders
import com.vitorpamplona.amethyst.ui.theme.ReactionRowExpandButton
import com.vitorpamplona.amethyst.ui.theme.ReactionRowHeight
import com.vitorpamplona.amethyst.ui.theme.ReactionRowHeightWithPadding
import com.vitorpamplona.amethyst.ui.theme.ReactionRowZapraiser
import com.vitorpamplona.amethyst.ui.theme.ReactionRowZapraiserWithPadding
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size18dp
import com.vitorpamplona.amethyst.ui.theme.Size19Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.Size28Modifier
import com.vitorpamplona.amethyst.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.TinyBorders
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.reactionBox
import com.vitorpamplona.amethyst.ui.theme.ripple24dp
import com.vitorpamplona.amethyst.ui.theme.selectedReactionBoxModifier
import com.vitorpamplona.quartz.encoders.Nip30CustomEmoji
import com.vitorpamplona.quartz.events.BaseTextNoteEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReactionsRow(
    baseNote: Note,
    showReactionDetail: Boolean,
    addPadding: Boolean,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val wantsToSeeReactions = remember(baseNote) { mutableStateOf(false) }

    InnerReactionRow(baseNote, showReactionDetail, addPadding, wantsToSeeReactions, editState, accountViewModel, nav)

    LoadAndDisplayZapraiser(baseNote, showReactionDetail, wantsToSeeReactions, accountViewModel)

    if (showReactionDetail && wantsToSeeReactions.value) {
        ReactionDetailGallery(baseNote, nav, accountViewModel)
        Spacer(modifier = HalfDoubleVertSpacer)
    }
}

@Composable
private fun InnerReactionRow(
    baseNote: Note,
    showReactionDetail: Boolean,
    addPadding: Boolean,
    wantsToSeeReactions: MutableState<Boolean>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    GenericInnerReactionRow(
        showReactionDetail = showReactionDetail,
        addPadding = addPadding,
        one = {
            WatchReactionsZapsBoostsAndDisplayIfExists(baseNote) {
                RenderShowIndividualReactionsButton(wantsToSeeReactions, accountViewModel)
            }
        },
        two = {
            ReplyReactionWithDialog(
                baseNote,
                MaterialTheme.colorScheme.placeholderText,
                accountViewModel,
                nav,
            )
        },
        three = {
            BoostWithDialog(
                baseNote,
                editState,
                MaterialTheme.colorScheme.placeholderText,
                accountViewModel,
                nav,
            )
        },
        four = {
            LikeReaction(baseNote, MaterialTheme.colorScheme.placeholderText, accountViewModel, nav)
        },
        five = {
            ZapReaction(baseNote, MaterialTheme.colorScheme.placeholderText, accountViewModel, nav = nav)
        },
        six = {
            ShareReaction(
                note = baseNote,
                grayTint = MaterialTheme.colorScheme.placeholderText,
            )
        },
    )
}

@Composable
fun ShareReaction(
    note: Note,
    grayTint: Color,
    barChartModifier: Modifier = Size19Modifier,
) {
    val context = LocalContext.current

    ClickableBox(
        modifier = barChartModifier,
        onClick = {
            val sendIntent =
                Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        externalLinkForNote(note),
                    )
                    putExtra(
                        Intent.EXTRA_TITLE,
                        stringRes(context, R.string.quick_action_share_browser_link),
                    )
                }

            val shareIntent =
                Intent.createChooser(
                    sendIntent,
                    stringRes(context, R.string.quick_action_share),
                )
            ContextCompat.startActivity(context, shareIntent, null)
        },
    ) {
        ShareIcon(barChartModifier, grayTint)
    }
}

@Composable
private fun GenericInnerReactionRow(
    showReactionDetail: Boolean,
    addPadding: Boolean,
    one: @Composable () -> Unit,
    two: @Composable () -> Unit,
    three: @Composable () -> Unit,
    four: @Composable () -> Unit,
    five: @Composable () -> Unit,
    six: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = CenterVertically,
        horizontalArrangement = RowColSpacing,
        modifier = if (addPadding) ReactionRowHeightWithPadding else ReactionRowHeight,
    ) {
        if (showReactionDetail) {
            Row(
                verticalAlignment = CenterVertically,
                modifier = ReactionRowExpandButton,
            ) {
                one()
            }
        }

        Row(verticalAlignment = CenterVertically, horizontalArrangement = RowColSpacing, modifier = Modifier.weight(1f)) { two() }

        Row(verticalAlignment = CenterVertically, horizontalArrangement = RowColSpacing, modifier = Modifier.weight(1f)) { three() }

        Row(verticalAlignment = CenterVertically, horizontalArrangement = RowColSpacing, modifier = Modifier.weight(1f)) { four() }

        Row(verticalAlignment = CenterVertically, modifier = Modifier.weight(1f)) { five() }

        Row(verticalAlignment = CenterVertically, modifier = Modifier) { six() }
    }
}

@Composable
private fun LoadAndDisplayZapraiser(
    baseNote: Note,
    showReactionDetail: Boolean,
    wantsToSeeReactions: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
) {
    val zapraiserAmount = baseNote.event?.zapraiserAmount() ?: 0
    if (zapraiserAmount > 0) {
        Box(
            modifier = if (showReactionDetail) ReactionRowZapraiserWithPadding else ReactionRowZapraiser,
            contentAlignment = CenterStart,
        ) {
            RenderZapRaiser(baseNote, zapraiserAmount, wantsToSeeReactions.value, accountViewModel)
        }
    }
}

@Immutable data class ZapraiserStatus(
    val progress: Float,
    val left: String,
)

@Composable
fun RenderZapRaiser(
    baseNote: Note,
    zapraiserAmount: Long,
    details: Boolean,
    accountViewModel: AccountViewModel,
) {
    val zapsState by baseNote.live().zaps.observeAsState()

    var zapraiserStatus by remember { mutableStateOf(ZapraiserStatus(0F, "$zapraiserAmount")) }

    LaunchedEffect(key1 = zapsState) {
        zapsState?.note?.let {
            accountViewModel.calculateZapraiser(baseNote) { newStatus ->
                if (zapraiserStatus != newStatus) {
                    zapraiserStatus = newStatus
                }
            }
        }
    }

    val color =
        if (zapraiserStatus.progress > 0.99) {
            DarkerGreen
        } else {
            MaterialTheme.colorScheme.mediumImportanceLink
        }

    LinearProgressIndicator(
        modifier =
            remember(details) {
                Modifier
                    .fillMaxWidth()
                    .height(if (details) 24.dp else 4.dp)
            },
        color = color,
        progress = { zapraiserStatus.progress },
    )

    if (details) {
        Box(
            contentAlignment = Center,
            modifier = TinyBorders,
        ) {
            val totalPercentage by
                remember(zapraiserStatus) {
                    derivedStateOf { "${(zapraiserStatus.progress * 100).roundToInt()}%" }
                }

            Text(
                text =
                    stringRes(id = R.string.sats_to_complete, totalPercentage, zapraiserStatus.left),
                modifier = NoSoTinyBorders,
                color = MaterialTheme.colorScheme.placeholderText,
                fontSize = Font14SP,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WatchReactionsZapsBoostsAndDisplayIfExists(
    baseNote: Note,
    content: @Composable () -> Unit,
) {
    val hasReactions by
        baseNote
            .live()
            .hasReactions
            .observeAsState(
                baseNote.zaps.isNotEmpty() ||
                    baseNote.boosts.isNotEmpty() ||
                    baseNote.reactions.isNotEmpty(),
            )

    if (hasReactions) {
        content()
    }
}

fun <T, K, R> LiveData<T>.combineWith(
    liveData1: LiveData<K>,
    block: (T?, K?) -> R,
): LiveData<R> {
    val result = MediatorLiveData<R>()
    result.addSource(this) { result.value = block(this.value, liveData1.value) }
    result.addSource(liveData1) { result.value = block(this.value, liveData1.value) }
    return result
}

fun <T, K, P, R> LiveData<T>.combineWith(
    liveData1: LiveData<K>,
    liveData2: LiveData<P>,
    block: (T?, K?, P?) -> R,
): LiveData<R> {
    val result = MediatorLiveData<R>()
    result.addSource(this) { result.value = block(this.value, liveData1.value, liveData2.value) }
    result.addSource(liveData1) { result.value = block(this.value, liveData1.value, liveData2.value) }
    result.addSource(liveData2) { result.value = block(this.value, liveData1.value, liveData2.value) }
    return result
}

@Composable
private fun RenderShowIndividualReactionsButton(
    wantsToSeeReactions: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
) {
    ClickableBox(
        onClick = { wantsToSeeReactions.value = !wantsToSeeReactions.value },
        modifier = Size20Modifier,
    ) {
        CrossfadeIfEnabled(
            targetState = wantsToSeeReactions.value,
            label = "RenderShowIndividualReactionsButton",
            accountViewModel = accountViewModel,
        ) {
            if (it) {
                ExpandLessIcon(modifier = Size22Modifier, R.string.close_all_reactions_to_this_post)
            } else {
                ExpandMoreIcon(modifier = Size22Modifier, R.string.open_all_reactions_to_this_post)
            }
        }
    }
}

@Composable
private fun ReactionDetailGallery(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val defaultBackgroundColor = MaterialTheme.colorScheme.background
    val backgroundColor = remember { mutableStateOf<Color>(defaultBackgroundColor) }

    val hasReactions by
        baseNote
            .live()
            .hasReactions
            .observeAsState(
                baseNote.zaps.isNotEmpty() ||
                    baseNote.boosts.isNotEmpty() ||
                    baseNote.reactions.isNotEmpty(),
            )

    if (hasReactions) {
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier.padding(start = 10.dp, top = 5.dp),
        ) {
            Column {
                WatchZapAndRenderGallery(baseNote, backgroundColor, nav, accountViewModel)
                WatchBoostsAndRenderGallery(baseNote, nav, accountViewModel)
                WatchReactionsAndRenderGallery(baseNote, nav, accountViewModel)
            }
        }
    }
}

@Composable
private fun WatchBoostsAndRenderGallery(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val boostsEvents by baseNote.live().boosts.observeAsState()

    boostsEvents?.let {
        if (it.note.boosts.isNotEmpty()) {
            RenderBoostGallery(
                it,
                nav,
                accountViewModel,
            )
        }
    }
}

@Composable
private fun WatchReactionsAndRenderGallery(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val reactionsState by baseNote.live().reactions.observeAsState()
    val reactionEvents = reactionsState?.note?.reactions ?: return

    if (reactionEvents.isNotEmpty()) {
        reactionEvents.forEach {
            val reactions = remember(it.key) { it.value.toImmutableList() }
            RenderLikeGallery(
                it.key,
                reactions,
                nav,
                accountViewModel,
            )
        }
    }
}

@Composable
private fun WatchZapAndRenderGallery(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val zapsState by baseNote.live().zaps.observeAsState()

    var zapEvents by
        remember(zapsState) {
            mutableStateOf(
                accountViewModel.cachedDecryptAmountMessageInGroup(baseNote),
            )
        }

    LaunchedEffect(key1 = zapsState) {
        accountViewModel.decryptAmountMessageInGroup(baseNote) { zapEvents = it }
    }

    if (zapEvents.isNotEmpty()) {
        RenderZapGallery(
            zapEvents,
            backgroundColor,
            accountViewModel,
            nav,
        )
    }
}

@Composable
private fun BoostWithDialog(
    baseNote: Note,
    editState: State<GenericLoadable<EditState>>,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var wantsToQuote by remember { mutableStateOf<Note?>(null) }
    var wantsToFork by remember { mutableStateOf<Note?>(null) }

    if (wantsToQuote != null) {
        NewPostView(
            onClose = { wantsToQuote = null },
            baseReplyTo = null,
            quote = wantsToQuote,
            version = (editState.value as? GenericLoadable.Loaded)?.loaded?.modificationToShow?.value,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    if (wantsToFork != null) {
        val replyTo =
            remember(wantsToFork) {
                val forkEvent = wantsToFork?.event
                if (forkEvent is BaseTextNoteEvent) {
                    val hex = forkEvent.replyingTo()
                    wantsToFork?.replyTo?.filter { it.event?.id() == hex }?.firstOrNull()
                } else {
                    null
                }
            }

        NewPostView(
            onClose = { wantsToFork = null },
            baseReplyTo = replyTo,
            fork = wantsToFork,
            version = (editState.value as? GenericLoadable.Loaded)?.loaded?.modificationToShow?.value,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    BoostReaction(
        baseNote,
        grayTint,
        accountViewModel,
        onQuotePress = { wantsToQuote = baseNote },
        onForkPress = { wantsToFork = baseNote },
    )
}

@Composable
private fun ReplyReactionWithDialog(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var wantsToReplyTo by remember { mutableStateOf<Note?>(null) }

    if (wantsToReplyTo != null) {
        NewPostView(
            onClose = { wantsToReplyTo = null },
            baseReplyTo = wantsToReplyTo,
            quote = null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    ReplyReaction(baseNote, grayTint, accountViewModel) { wantsToReplyTo = baseNote }
}

@Composable
fun ReplyReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    showCounter: Boolean = true,
    iconSizeModifier: Modifier = Size19Modifier,
    onPress: () -> Unit,
) {
    ClickableBox(
        modifier = iconSizeModifier,
        onClick = {
            if (baseNote.isDraft()) {
                accountViewModel.toast(
                    R.string.draft_note,
                    R.string.it_s_not_possible_to_reply_to_a_draft_note,
                )
            } else {
                if (accountViewModel.isWriteable()) {
                    onPress()
                } else {
                    accountViewModel.toast(
                        R.string.read_only_user,
                        R.string.login_with_a_private_key_to_be_able_to_reply,
                    )
                }
            }
        },
    ) {
        CommentIcon(iconSizeModifier, grayTint)
    }

    if (showCounter) {
        ReplyCounter(baseNote, grayTint, accountViewModel)
    }
}

@Composable
fun ReplyCounter(
    baseNote: Note,
    textColor: Color,
    accountViewModel: AccountViewModel,
) {
    val repliesState by baseNote.live().replyCount.observeAsState(baseNote.replies.size)

    SlidingAnimationCount(repliesState, textColor, accountViewModel)
}

@Composable
private fun SlidingAnimationCount(
    baseCount: Int,
    textColor: Color,
    accountViewModel: AccountViewModel,
) {
    if (accountViewModel.settings.featureSet == FeatureSetType.PERFORMANCE) {
        TextCount(baseCount, textColor)
    } else {
        AnimatedContent<Int>(
            targetState = baseCount,
            transitionSpec = AnimatedContentTransitionScope<Int>::transitionSpec,
            label = "SlidingAnimationCount",
        ) { count ->
            TextCount(count, textColor)
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
private fun <S> AnimatedContentTransitionScope<S>.transitionSpec(): ContentTransform = slideAnimation

@ExperimentalAnimationApi
val slideAnimation: ContentTransform =
    (
        slideInVertically(animationSpec = tween(durationMillis = 100)) { height -> height } +
            fadeIn(
                animationSpec = tween(durationMillis = 100),
            )
    ).togetherWith(
        slideOutVertically(animationSpec = tween(durationMillis = 100)) { height -> -height } +
            fadeOut(
                animationSpec = tween(durationMillis = 100),
            ),
    )

@Composable
fun TextCount(
    count: Int,
    textColor: Color,
) {
    Text(
        text = showCount(count),
        fontSize = Font14SP,
        color = textColor,
        maxLines = 1,
    )
}

@Composable
fun SlidingAnimationAmount(
    amount: String,
    textColor: Color,
    accountViewModel: AccountViewModel,
) {
    if (accountViewModel.settings.featureSet == FeatureSetType.PERFORMANCE) {
        Text(
            text = amount,
            fontSize = Font14SP,
            color = textColor,
            maxLines = 1,
        )
    } else {
        AnimatedContent(
            targetState = amount,
            transitionSpec = AnimatedContentTransitionScope<String>::transitionSpec,
            label = "SlidingAnimationAmount",
        ) { count ->
            Text(
                text = count,
                fontSize = Font14SP,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun BoostReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    iconSizeModifier: Modifier = Size19Modifier,
    iconSize: Dp = Size20dp,
    onQuotePress: () -> Unit,
    onForkPress: () -> Unit,
) {
    var wantsToBoost by remember { mutableStateOf(false) }

    ClickableBox(
        modifier = iconSizeModifier,
        onClick = {
            accountViewModel.tryBoost(baseNote) { wantsToBoost = true }
        },
    ) {
        ObserveBoostIcon(baseNote, accountViewModel) { hasBoosted ->
            RepostedIcon(iconSizeModifier, if (hasBoosted) Color.Unspecified else grayTint)
        }

        if (wantsToBoost) {
            BoostTypeChoicePopup(
                baseNote,
                iconSize,
                accountViewModel,
                onDismiss = { wantsToBoost = false },
                onQuote = {
                    wantsToBoost = false
                    onQuotePress()
                },
                onRepost = {
                    accountViewModel.boost(baseNote)
                },
                onFork = {
                    wantsToBoost = false
                    onForkPress()
                },
            )
        }
    }

    BoostText(baseNote, grayTint, accountViewModel)
}

@Composable
fun ObserveBoostIcon(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (Boolean) -> Unit,
) {
    val hasBoosted by
        remember(baseNote) {
            baseNote
                .live()
                .boosts
                .map { it.note.isBoostedBy(accountViewModel.userProfile()) }
                .distinctUntilChanged()
        }.observeAsState(
            baseNote.isBoostedBy(accountViewModel.userProfile()),
        )

    inner(hasBoosted)
}

@Composable
fun BoostText(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
) {
    val boostState by baseNote.live().boostCount.observeAsState(baseNote.boosts.size)

    SlidingAnimationCount(boostState, grayTint, accountViewModel)
}

@Composable
fun LikeReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
    iconSize: Dp = Size18dp,
    heartSizeModifier: Modifier = Size18Modifier,
    iconFontSize: TextUnit = Font14SP,
) {
    var wantsToChangeReactionSymbol by remember { mutableStateOf(false) }
    var wantsToReact by remember { mutableStateOf(false) }

    ClickableBox(
        onClick = {
            likeClick(
                accountViewModel,
                baseNote,
                onMultipleChoices = { wantsToReact = true },
                onWantsToSignReaction = { accountViewModel.reactToOrDelete(baseNote) },
            )
        },
        onLongClick = { wantsToChangeReactionSymbol = true },
    ) {
        ObserveLikeIcon(baseNote, accountViewModel) { reactionType ->
            CrossfadeIfEnabled(targetState = reactionType, contentAlignment = Center, label = "LikeIcon", accountViewModel = accountViewModel) {
                if (reactionType != null) {
                    RenderReactionType(reactionType, heartSizeModifier, iconFontSize)
                } else {
                    LikeIcon(heartSizeModifier, grayTint)
                }
            }
        }

        if (wantsToChangeReactionSymbol) {
            UpdateReactionTypeDialog(
                { wantsToChangeReactionSymbol = false },
                accountViewModel = accountViewModel,
                nav,
            )
        }

        if (wantsToReact) {
            ReactionChoicePopup(
                baseNote,
                iconSize,
                accountViewModel,
                onDismiss = { wantsToReact = false },
                onChangeAmount = {
                    wantsToReact = false
                    wantsToChangeReactionSymbol = true
                },
            )
        }
    }

    ObserveLikeText(baseNote) { reactionCount -> SlidingAnimationCount(reactionCount, grayTint, accountViewModel) }
}

@Composable
fun ObserveLikeIcon(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (String?) -> Unit,
) {
    val reactionsState by baseNote.live().reactions.observeAsState()

    @Suppress("ProduceStateDoesNotAssignValue")
    val reactionType by
        produceState(initialValue = null as String?, key1 = reactionsState) {
            val newReactionType = accountViewModel.loadReactionTo(reactionsState?.note)
            if (value != newReactionType) {
                value = newReactionType
            }
        }

    inner(reactionType)
}

@Composable
private fun RenderReactionType(
    reactionType: String,
    iconSizeModifier: Modifier = Size20Modifier,
    iconFontSize: TextUnit,
) {
    if (reactionType.isNotEmpty() && reactionType[0] == ':') {
        val renderable =
            remember(reactionType) {
                persistentListOf(
                    Nip30CustomEmoji.ImageUrlType(reactionType.removePrefix(":").substringAfter(":")),
                )
            }

        InLineIconRenderer(
            renderable,
            style = SpanStyle(color = Color.White),
            fontSize = iconFontSize,
            maxLines = 1,
        )
    } else {
        when (reactionType) {
            "+" -> LikedIcon(iconSizeModifier)
            "-" -> Text(text = "\uD83D\uDC4E", maxLines = 1, fontSize = iconFontSize)
            else -> Text(text = reactionType, maxLines = 1, fontSize = iconFontSize)
        }
    }
}

@Composable
fun ObserveLikeText(
    baseNote: Note,
    inner: @Composable (Int) -> Unit,
) {
    val reactionCount by baseNote.live().reactionCount.observeAsState(0)

    inner(reactionCount)
}

private fun likeClick(
    accountViewModel: AccountViewModel,
    baseNote: Note,
    onMultipleChoices: () -> Unit,
    onWantsToSignReaction: () -> Unit,
) {
    if (baseNote.isDraft()) {
        accountViewModel.toast(
            R.string.draft_note,
            R.string.it_s_not_possible_to_react_to_a_draft_note,
        )
        return
    }
    if (accountViewModel.account.settings.reactionChoices.value
            .isEmpty()
    ) {
        accountViewModel.toast(
            R.string.no_reactions_setup,
            R.string.no_reaction_type_setup_long_press_to_change,
        )
    } else if (!accountViewModel.isWriteable()) {
        accountViewModel.toast(
            R.string.read_only_user,
            R.string.login_with_a_private_key_to_like_posts,
        )
    } else if (accountViewModel.account.settings.reactionChoices.value.size == 1) {
        onWantsToSignReaction()
    } else if (accountViewModel.account.settings.reactionChoices.value.size > 1) {
        onMultipleChoices()
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ZapReaction(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    iconSize: Dp = Size20dp,
    iconSizeModifier: Modifier = Size20Modifier,
    animationSize: Dp = 14.dp,
    nav: INav,
) {
    var wantsToZap by remember { mutableStateOf(false) }
    var wantsToChangeZapAmount by remember { mutableStateOf(false) }
    var wantsToSetCustomZap by remember { mutableStateOf(false) }
    var showErrorMessageDialog by remember { mutableStateOf<List<String>>(emptyList()) }
    var wantsToPay by
        remember(baseNote) {
            mutableStateOf<ImmutableList<ZapPaymentHandler.Payable>>(
                persistentListOf(),
            )
        }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var zappingProgress by remember { mutableFloatStateOf(0f) }

    Row(
        verticalAlignment = CenterVertically,
        modifier =
            iconSizeModifier.combinedClickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple24dp,
                onClick = {
                    zapClick(
                        baseNote,
                        accountViewModel,
                        context,
                        onZappingProgress = { progress: Float -> scope.launch { zappingProgress = progress } },
                        onMultipleChoices = { wantsToZap = true },
                        onError = { _, message ->
                            scope.launch {
                                zappingProgress = 0f
                                showErrorMessageDialog = showErrorMessageDialog + message
                            }
                        },
                        onPayViaIntent = { wantsToPay = it },
                    )
                },
                onLongClick = { wantsToChangeZapAmount = true },
                onDoubleClick = { wantsToSetCustomZap = true },
            ),
    ) {
        if (wantsToZap) {
            ZapAmountChoicePopup(
                baseNote = baseNote,
                popupYOffset = iconSize,
                accountViewModel = accountViewModel,
                onDismiss = {
                    wantsToZap = false
                    zappingProgress = 0f
                },
                onChangeAmount = {
                    wantsToZap = false
                    wantsToChangeZapAmount = true
                },
                onError = { _, message ->
                    scope.launch {
                        zappingProgress = 0f
                        showErrorMessageDialog = showErrorMessageDialog + message
                    }
                },
                onProgress = { scope.launch(Dispatchers.Main) { zappingProgress = it } },
                onPayViaIntent = { wantsToPay = it },
            )
        }

        if (showErrorMessageDialog.isNotEmpty()) {
            val msg = showErrorMessageDialog.joinToString("\n")
            ErrorMessageDialog(
                title = stringRes(id = R.string.error_dialog_zap_error),
                textContent = msg,
                onClickStartMessage = {
                    baseNote.author?.let {
                        scope.launch(Dispatchers.IO) {
                            val route = routeToMessage(it, msg, accountViewModel)
                            nav.nav(route)
                        }
                    }
                },
                onDismiss = { showErrorMessageDialog = emptyList() },
            )
        }

        if (wantsToChangeZapAmount) {
            UpdateZapAmountDialog(
                onClose = { wantsToChangeZapAmount = false },
                accountViewModel = accountViewModel,
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
                        showErrorMessageDialog = showErrorMessageDialog + it
                    }
                },
                justShowError = {
                    scope.launch {
                        showErrorMessageDialog = showErrorMessageDialog + it
                    }
                },
            )
        }

        if (wantsToSetCustomZap) {
            ZapCustomDialog(
                onClose = { wantsToSetCustomZap = false },
                onError = { _, message ->
                    scope.launch {
                        zappingProgress = 0f
                        showErrorMessageDialog = showErrorMessageDialog + message
                    }
                },
                onProgress = { scope.launch(Dispatchers.Main) { zappingProgress = it } },
                onPayViaIntent = { wantsToPay = it },
                accountViewModel = accountViewModel,
                baseNote = baseNote,
            )
        }

        if (zappingProgress > 0.00001 && zappingProgress < 0.99999) {
            Spacer(ModifierWidth3dp)

            CircularProgressIndicator(
                progress =
                    animateFloatAsState(
                        targetValue = zappingProgress,
                        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                        label = "ZapIconIndicator",
                    ).value,
                modifier = remember { Modifier.size(animationSize) },
                strokeWidth = 2.dp,
            )
        } else {
            ObserveZapIcon(
                baseNote,
                accountViewModel,
            ) { wasZappedByLoggedInUser ->
                CrossfadeIfEnabled(targetState = wasZappedByLoggedInUser.value, label = "ZapIcon", accountViewModel = accountViewModel) {
                    if (it) {
                        ZappedIcon(iconSizeModifier)
                    } else {
                        OutlinedZapIcon(iconSizeModifier, grayTint)
                    }
                }
            }
        }
    }

    ObserveZapAmountText(baseNote, accountViewModel) { zapAmountTxt ->
        SlidingAnimationAmount(zapAmountTxt, grayTint, accountViewModel)
    }
}

fun zapClick(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    context: Context,
    onZappingProgress: (Float) -> Unit,
    onMultipleChoices: () -> Unit,
    onError: (String, String) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
) {
    if (baseNote.isDraft()) {
        accountViewModel.toast(
            R.string.draft_note,
            R.string.it_s_not_possible_to_zap_to_a_draft_note,
        )
        return
    }

    if (accountViewModel.account.settings.zapAmountChoices.value
            .isEmpty()
    ) {
        accountViewModel.toast(
            R.string.error_dialog_zap_error,
            R.string.no_zap_amount_setup_long_press_to_change,
        )
    } else if (!accountViewModel.isWriteable()) {
        accountViewModel.toast(
            R.string.error_dialog_zap_error,
            R.string.login_with_a_private_key_to_be_able_to_send_zaps,
        )
    } else if (accountViewModel.account.settings.zapAmountChoices.value.size == 1) {
        accountViewModel.zap(
            baseNote,
            accountViewModel.account.settings.zapAmountChoices.value
                .first() * 1000,
            null,
            "",
            context,
            onError = onError,
            onProgress = { onZappingProgress(it) },
            onPayViaIntent = onPayViaIntent,
        )
    } else if (accountViewModel.account.settings.zapAmountChoices.value.size > 1) {
        onMultipleChoices()
    }
}

@Composable
fun ObserveZapIcon(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (MutableState<Boolean>) -> Unit,
) {
    val wasZappedByLoggedInUser = remember { mutableStateOf(false) }

    if (!wasZappedByLoggedInUser.value) {
        val zapsState by baseNote.live().zaps.observeAsState()

        LaunchedEffect(key1 = zapsState) {
            if (zapsState?.note?.zapPayments?.isNotEmpty() == true || zapsState?.note?.zaps?.isNotEmpty() == true) {
                accountViewModel.calculateIfNoteWasZappedByAccount(baseNote) { newWasZapped ->
                    if (wasZappedByLoggedInUser.value != newWasZapped) {
                        wasZappedByLoggedInUser.value = newWasZapped
                    }
                }
            }
        }
    }

    inner(wasZappedByLoggedInUser)
}

@Composable
fun ObserveZapAmountText(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (String) -> Unit,
) {
    val zapsState by baseNote.live().zaps.observeAsState()

    if (zapsState?.note?.zapPayments?.isNotEmpty() == true) {
        @Suppress("ProduceStateDoesNotAssignValue")
        val zapAmountTxt by
            produceState(initialValue = showAmount(baseNote.zapsAmount), key1 = zapsState) {
                zapsState?.note?.let {
                    accountViewModel.calculateZapAmount(it) { newZapAmount ->
                        if (value != newZapAmount) {
                            value = newZapAmount
                        }
                    }
                }
            }

        inner(zapAmountTxt)
    } else {
        inner(showAmount(zapsState?.note?.zapsAmount))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoostTypeChoicePopup(
    baseNote: Note,
    iconSize: Dp,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    onQuote: () -> Unit,
    onRepost: () -> Unit,
    onFork: () -> Unit,
) {
    val iconSizePx = with(LocalDensity.current) { -iconSize.toPx().toInt() }

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, iconSizePx),
        onDismissRequest = { onDismiss() },
    ) {
        FlowRow {
            Button(
                modifier = Modifier.padding(horizontal = 3.dp),
                onClick = {
                    if (accountViewModel.isWriteable()) {
                        accountViewModel.boost(baseNote)
                        onDismiss()
                    } else {
                        onRepost()
                        onDismiss()
                    }
                },
                shape = ButtonBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(stringRes(R.string.boost), color = Color.White, textAlign = TextAlign.Center)
            }

            Button(
                modifier = Modifier.padding(horizontal = 3.dp),
                onClick = onQuote,
                shape = ButtonBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(stringRes(R.string.quote), color = Color.White, textAlign = TextAlign.Center)
            }

            Button(
                modifier = Modifier.padding(horizontal = 3.dp),
                onClick = onFork,
                shape = ButtonBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(stringRes(R.string.fork), color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun ReactionChoicePopup(
    baseNote: Note,
    iconSize: Dp,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    onChangeAmount: () -> Unit,
) {
    val iconSizePx = with(LocalDensity.current) { -iconSize.toPx().toInt() }

    val reactions by accountViewModel.account.settings.reactionChoices
        .collectAsStateWithLifecycle()
    val toRemove = remember { baseNote.reactedBy(accountViewModel.userProfile()).toImmutableSet() }

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, iconSizePx),
        onDismissRequest = { onDismiss() },
        properties = PopupProperties(focusable = true),
    ) {
        ReactionChoicePopupContent(
            reactions,
            toRemove = toRemove,
            onClick = { reactionType ->
                accountViewModel.reactToOrDelete(
                    baseNote,
                    reactionType,
                )
                onDismiss()
            },
            onChangeAmount,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionChoicePopupContent(
    listOfReactions: ImmutableList<String>,
    toRemove: ImmutableSet<String>,
    onClick: (reactionType: String) -> Unit,
    onChangeAmount: () -> Unit,
) {
    Box(HalfPadding, contentAlignment = Center) {
        ElevatedCard(
            shape = SmallBorder,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                listOfReactions.forEach { reactionType ->
                    ActionableReactionButton(
                        reactionType = reactionType,
                        onClick = { onClick(reactionType) },
                        onChangeAmount = onChangeAmount,
                        toRemove = toRemove,
                    )
                }

                ClickableBox(modifier = reactionBox, onClick = onChangeAmount) {
                    ChangeReactionIcon(modifier = Size28Modifier, MaterialTheme.colorScheme.placeholderText)
                }
            }
        }
    }
}

@Preview()
@Composable
fun ReactionChoicePopupPeeview() {
    ThemeComparisonColumn {
        ReactionChoicePopupContent(
            persistentListOf(
                "\uD83D\uDE80",
                "\uD83E\uDEC2",
                "\uD83D\uDC40",
                "\uD83D\uDE02",
                "\uD83C\uDF89",
                "\uD83E\uDD14",
                "\uD83D\uDE31",
                "\uD83E\uDD14",
                "\uD83D\uDE31",
                "+",
                "-",
                "\uD83C\uDF89",
                "\uD83E\uDD14",
                "\uD83D\uDE31",
                "\uD83E\uDD14",
                "\uD83D\uDE31",
            ),
            onClick = {},
            onChangeAmount = {},
            toRemove = persistentSetOf("\uD83D\uDE80", "\uD83E\uDD14", "\uD83D\uDE31"),
        )
    }
}

@Composable
private fun ActionableReactionButton(
    reactionType: String,
    onClick: () -> Unit,
    onChangeAmount: () -> Unit,
    toRemove: ImmutableSet<String>,
) {
    ClickableBox(
        modifier = if (reactionType in toRemove) MaterialTheme.colorScheme.selectedReactionBoxModifier else reactionBox,
        onClick = onClick,
        onLongClick = onChangeAmount,
    ) {
        RenderReaction(reactionType)
    }
}

@Composable
fun RenderReaction(reactionType: String) {
    if (reactionType.startsWith(":")) {
        val noStartColon = reactionType.removePrefix(":")
        val url = noStartColon.substringAfter(":")

        InLineIconRenderer(
            persistentListOf(
                Nip30CustomEmoji.ImageUrlType(url),
            ),
            style = SpanStyle(color = MaterialTheme.colorScheme.onBackground),
            maxLines = 1,
            fontSize = 22.sp,
        )
    } else {
        when (reactionType) {
            "+" -> {
                LikedIcon(modifier = Size28Modifier)
            }

            "-" -> {
                Text(
                    text = "\uD83D\uDC4E",
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    fontSize = 22.sp,
                )
            }
            else -> {
                Text(
                    reactionType,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    fontSize = 22.sp,
                )
            }
        }
    }
}

@Composable
fun ZapAmountChoicePopup(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    popupYOffset: Dp,
    onDismiss: () -> Unit,
    onChangeAmount: () -> Unit,
    onError: (title: String, text: String) -> Unit,
    onProgress: (percent: Float) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
) {
    val zapAmountChoices by
        accountViewModel.account.settings.zapAmountChoices
            .collectAsStateWithLifecycle()

    ZapAmountChoicePopup(baseNote, zapAmountChoices, accountViewModel, popupYOffset, onDismiss, onChangeAmount, onError, onProgress, onPayViaIntent)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ZapAmountChoicePopup(
    baseNote: Note,
    zapAmountChoices: ImmutableList<Long>,
    accountViewModel: AccountViewModel,
    popupYOffset: Dp,
    onDismiss: () -> Unit,
    onChangeAmount: () -> Unit,
    onError: (title: String, text: String) -> Unit,
    onProgress: (percent: Float) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
) {
    val context = LocalContext.current
    val zapMessage = ""

    val yOffset = with(LocalDensity.current) { -popupYOffset.toPx().toInt() }

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, yOffset),
        onDismissRequest = { onDismiss() },
        properties = PopupProperties(focusable = true),
    ) {
        FlowRow(horizontalArrangement = Arrangement.Center) {
            zapAmountChoices.forEach { amountInSats ->
                Button(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    onClick = {
                        accountViewModel.zap(
                            baseNote,
                            amountInSats * 1000,
                            null,
                            zapMessage,
                            context,
                            true,
                            onError,
                            onProgress,
                            onPayViaIntent,
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
                        "⚡ ${showAmount(amountInSats.toBigDecimal().setScale(1))}",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier.combinedClickable(
                                onClick = {
                                    accountViewModel.zap(
                                        baseNote,
                                        amountInSats * 1000,
                                        null,
                                        zapMessage,
                                        context,
                                        true,
                                        onError,
                                        onProgress,
                                        onPayViaIntent,
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

fun showCount(count: Int?): String {
    if (count == null) return ""
    if (count == 0) return ""

    return when {
        count >= 1000000000 -> "${(count / 1000000000f).roundToInt()}G"
        count >= 1000000 -> "${(count / 1000000f).roundToInt()}M"
        count >= 10000 -> "${(count / 1000f).roundToInt()}k"
        else -> "$count"
    }
}
