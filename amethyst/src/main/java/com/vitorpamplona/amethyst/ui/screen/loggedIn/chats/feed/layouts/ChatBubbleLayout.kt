/*
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.note.CommentIcon
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleMaxSizeModifier
import com.vitorpamplona.amethyst.ui.theme.ChatPaddingGroupedModifier
import com.vitorpamplona.amethyst.ui.theme.ChatPaddingInnerQuoteModifier
import com.vitorpamplona.amethyst.ui.theme.ChatPaddingModifier
import com.vitorpamplona.amethyst.ui.theme.HalfHalfVertPadding
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing5dp
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.chatBubbleMe
import com.vitorpamplona.amethyst.ui.theme.chatBubbleThem
import com.vitorpamplona.amethyst.ui.theme.chatDraftBackground
import com.vitorpamplona.amethyst.ui.theme.messageBubbleLimits
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Half the height of a reaction chip, so the chip row overlaps the bubble by
// exactly its own vertical center.
private val ChatChipOverlapArrangement = Arrangement.spacedBy((-12).dp)

// Extra space reserved at the bottom of a bubble that has overlapping chips but no
// footer, so the chips ride over padding instead of the last line of text. The bubble
// already has 6dp bottom padding; the chips poke ~12dp in, so ~8dp more clears them.
private val ChatChipOverlapReserve = 8.dp

// Swipe-to-reply: releasing past the threshold fires the reply; the bubble never
// drags further than the max.
private val SwipeReplyThreshold = 56.dp
private val SwipeReplyMaxDrag = 72.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubbleLayout(
    isLoggedInUser: Boolean,
    isDraft: Boolean,
    innerQuote: Boolean,
    drawAuthorInfo: Boolean,
    groupPosition: ChatGroupPosition = ChatGroupPosition.SINGLE,
    // Jumbo-emoji messages render without a bubble fill.
    transparentBubble: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    shouldHighlight: Boolean = false,
    onHighlightFinished: (() -> Unit)? = null,
    onClick: () -> Boolean,
    onDoubleTap: (() -> Unit)? = null,
    // Drag the bubble toward the screen center to quote-reply. Null disables the
    // gesture (inner quotes, drafts).
    onSwipeReply: (() -> Unit)? = null,
    onAuthorClick: () -> Unit,
    actionMenu: @Composable (onDismiss: () -> Unit) -> Unit,
    reactionsRow: (@Composable () -> Unit)? = null,
    // Small footer in the bubble's bottom corner (timestamp, delivery, per-message
    // status glyphs). Null skips it entirely.
    footerRow: (@Composable () -> Unit)? = null,
    drawAuthorLine: @Composable () -> Unit,
    inner: @Composable (MutableState<Color>) -> Unit,
) {
    val loggedInColors = MaterialTheme.colorScheme.chatBubbleMe
    val otherColors = MaterialTheme.colorScheme.chatBubbleThem
    val defaultBackground = MaterialTheme.colorScheme.background
    val draftColor = MaterialTheme.colorScheme.chatDraftBackground

    // Keyed on the theme-derived inputs so bubbles recolor when the user switches
    // light/dark or accent while the chat is composed (MaterialTheme recomposes
    // in place — nothing recreates this composable on a theme change).
    val parentBg = parentBackgroundColor?.value
    val bgColor =
        remember(isLoggedInUser, isDraft, loggedInColors, otherColors, draftColor, defaultBackground, parentBg) {
            val base = parentBg ?: defaultBackground
            val bubble =
                when {
                    isLoggedInUser && isDraft -> draftColor
                    isLoggedInUser -> loggedInColors
                    else -> otherColors
                }
            mutableStateOf(bubble.compositeOver(base))
        }

    val highlightActive = remember { mutableStateOf(false) }

    LaunchedEffect(shouldHighlight) {
        if (shouldHighlight) {
            highlightActive.value = true
            delay(1500)
            highlightActive.value = false
            onHighlightFinished?.invoke()
        }
    }

    val highlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
    val animatedColor by animateColorAsState(
        targetValue = if (highlightActive.value) highlightColor.compositeOver(bgColor.value) else bgColor.value,
        animationSpec = tween(durationMillis = if (highlightActive.value) 300 else 800),
        label = "highlightAnimation",
    )

    val haptic = LocalHapticFeedback.current

    // Plain state during the drag (a coroutine per pointer frame is wasted work);
    // a single animation job settles it back on release.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val swipeScope = rememberCoroutineScope()
    // The caller passes a fresh onSwipeReply lambda every recomposition (it captures
    // the note), so read it through updated-state and key pointerInput on the stable
    // isLoggedInUser instead. Keying on the lambda would tear down and restart the
    // detector on every recomposition — stranding an in-flight drag and churning the
    // gesture coroutine on every idle row update.
    val latestOnSwipeReply by rememberUpdatedState(onSwipeReply)
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { SwipeReplyThreshold.toPx() } }
    val swipeMaxPx = remember(density) { with(density) { SwipeReplyMaxDrag.toPx() } }

    Box(
        modifier =
            when {
                innerQuote -> ChatPaddingInnerQuoteModifier
                groupPosition.isConnectedAbove -> ChatPaddingGroupedModifier
                else -> ChatPaddingModifier
            },
    ) {
        if (onSwipeReply != null) {
            // Gate composition on a boolean that flips only at the 0<->non-0 edges, so
            // the icon is added/removed twice per gesture instead of recomposing every
            // frame; the fade/scale reads dragOffset inside graphicsLayer (layer phase),
            // so the whole swipe animates without recomposing ChatBubbleLayout.
            val swiping by remember { derivedStateOf { dragOffset != 0f } }
            if (swiping) {
                Box(
                    modifier =
                        Modifier
                            .align(if (isLoggedInUser) Alignment.CenterEnd else Alignment.CenterStart)
                            .graphicsLayer {
                                val progress = (abs(dragOffset) / swipeThresholdPx).coerceIn(0f, 1f)
                                alpha = progress
                                scaleX = 0.6f + 0.4f * progress
                                scaleY = 0.6f + 0.4f * progress
                            },
                ) {
                    CommentIcon(Size20Modifier, MaterialTheme.colorScheme.placeholderText)
                }
            }
        }

        val swipeModifier =
            if (onSwipeReply != null) {
                Modifier
                    .graphicsLayer { translationX = dragOffset }
                    .pointerInput(isLoggedInUser) {
                        // Drag toward the screen center only; a haptic tick marks the
                        // commit point, releasing past it fires the reply.
                        var crossedThreshold = false

                        val settleBack = {
                            settleJob =
                                swipeScope.launch {
                                    animate(dragOffset, 0f) { value, _ -> dragOffset = value }
                                }
                        }

                        val applyDrag = { dragAmount: Float ->
                            val newOffset =
                                if (isLoggedInUser) {
                                    (dragOffset + dragAmount).coerceIn(-swipeMaxPx, 0f)
                                } else {
                                    (dragOffset + dragAmount).coerceIn(0f, swipeMaxPx)
                                }
                            if (!crossedThreshold && abs(newOffset) >= swipeThresholdPx) {
                                crossedThreshold = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            dragOffset = newOffset
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            // Claim the pointer only once the motion is clearly more
                            // horizontal than vertical; a mostly-vertical drag leaves the
                            // pointer unconsumed so the enclosing scroll keeps it and the
                            // bubble never moves while you scroll.
                            var overSlop = Offset.Zero
                            val drag =
                                awaitTouchSlopOrCancellation(down.id) { change, over ->
                                    if (abs(over.x) > abs(over.y)) {
                                        change.consume()
                                        overSlop = over
                                    }
                                }

                            if (drag != null) {
                                // Take over from any in-flight settle only now that we've
                                // committed to a horizontal drag. Cancelling earlier (on
                                // the down) would strand dragOffset when the gesture turns
                                // out to be a vertical scroll, since settleBack() below
                                // only runs on this path.
                                settleJob?.cancel()
                                crossedThreshold = false
                                applyDrag(overSlop.x)
                                try {
                                    val completed =
                                        horizontalDrag(drag.id) { change ->
                                            applyDrag(change.positionChange().x)
                                            change.consume()
                                        }
                                    if (completed && abs(dragOffset) >= swipeThresholdPx) {
                                        latestOnSwipeReply?.invoke()
                                    }
                                } finally {
                                    // Settle even if the drag coroutine is cancelled (e.g.
                                    // the bubble leaves composition mid-swipe); settleBack
                                    // launches on swipeScope, not this pointer coroutine,
                                    // so it still runs while that scope is alive.
                                    settleBack()
                                }
                            }
                        }
                    }
            } else {
                Modifier
            }

        Row(
            modifier = Modifier.fillMaxWidth().then(swipeModifier),
            horizontalArrangement = if (isLoggedInUser) Arrangement.End else Arrangement.Start,
        ) {
            InnerChatBubble(
                isLoggedInUser = isLoggedInUser,
                innerQuote = innerQuote,
                drawAuthorInfo = drawAuthorInfo,
                groupPosition = groupPosition,
                transparentBubble = transparentBubble,
                animatedColor = animatedColor,
                highlightActive = highlightActive.value,
                bgColor = bgColor,
                onClick = onClick,
                onDoubleTap = onDoubleTap,
                onAuthorClick = onAuthorClick,
                actionMenu = actionMenu,
                reactionsRow = reactionsRow,
                footerRow = footerRow,
                drawAuthorLine = drawAuthorLine,
                inner = inner,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InnerChatBubble(
    isLoggedInUser: Boolean,
    innerQuote: Boolean,
    drawAuthorInfo: Boolean,
    groupPosition: ChatGroupPosition,
    transparentBubble: Boolean,
    animatedColor: Color,
    highlightActive: Boolean,
    bgColor: MutableState<Color>,
    onClick: () -> Boolean,
    onDoubleTap: (() -> Unit)?,
    onAuthorClick: () -> Unit,
    actionMenu: @Composable (onDismiss: () -> Unit) -> Unit,
    reactionsRow: (@Composable () -> Unit)?,
    footerRow: (@Composable () -> Unit)?,
    drawAuthorLine: @Composable () -> Unit,
    inner: @Composable (MutableState<Color>) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val popupExpanded = remember { mutableStateOf(false) }

    val pressInteractionSource = remember { MutableInteractionSource() }
    val isPressed by pressInteractionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "bubblePressScale")

    val indication = LocalIndication.current
    val clickableModifier =
        remember {
            Modifier.combinedClickable(
                interactionSource = pressInteractionSource,
                indication = indication,
                onClick = {
                    // Only inner-quote bubbles do anything on tap (scroll to the quoted
                    // message); a normal bubble tap is a no-op.
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    popupExpanded.value = true
                },
                onDoubleClick =
                    onDoubleTap?.let { action ->
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            action()
                        }
                    },
            )
        }

    Column(
        horizontalAlignment = if (isLoggedInUser) Alignment.End else Alignment.Start,
        // Negative spacing pulls the reaction chips up so their vertical center
        // rides the bubble's bottom border instead of floating detached below it.
        verticalArrangement = if (reactionsRow != null) ChatChipOverlapArrangement else Arrangement.Top,
        modifier = if (innerQuote) Modifier else ChatBubbleMaxSizeModifier,
    ) {
        Surface(
            // Jumbo emoji show bare; the scroll-to highlight still tints them.
            color = if (transparentBubble && !highlightActive) Color.Transparent else animatedColor,
            shape = chatBubbleShapeFor(isLoggedInUser, if (innerQuote) ChatGroupPosition.SINGLE else groupPosition),
            modifier =
                clickableModifier.graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
        ) {
            Column(modifier = messageBubbleLimits, verticalArrangement = RowColSpacing5dp) {
                if (drawAuthorInfo) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isLoggedInUser) Arrangement.End else Arrangement.Start,
                        modifier = HalfHalfVertPadding.clickable(onClick = onAuthorClick),
                    ) {
                        drawAuthorLine()
                    }
                }

                inner(bgColor)

                if (footerRow != null) {
                    // Mirror the footer with the bubble so it sits on the same
                    // center-facing edge for both sides: the left for the logged-in user's
                    // own (right-aligned) bubbles, the right for everyone else's.
                    Box(modifier = Modifier.align(if (isLoggedInUser) Alignment.Start else Alignment.End)) {
                        footerRow()
                    }
                } else if (reactionsRow != null) {
                    // No footer to sit over — reserve space so the overlapping chips don't
                    // cover the last line of text.
                    Spacer(Modifier.height(ChatChipOverlapReserve))
                }
            }
        }

        reactionsRow?.invoke()
    }

    if (popupExpanded.value) {
        actionMenu {
            popupExpanded.value = false
        }
    }
}

@Preview
@Composable
private fun BubblePreview() {
    val bgColor =
        remember {
            mutableStateOf(Color.Transparent)
        }

    Column {
        ChatBubbleLayout(
            isLoggedInUser = false,
            isDraft = false,
            innerQuote = false,
            drawAuthorInfo = true,
            parentBackgroundColor = bgColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            symbol = MaterialSymbols.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape)
                                    .background(Color.LightGray),
                        )
                    },
                    name = {
                        Text("Someone else", fontWeight = FontWeight.Bold)
                    },
                )
            },
        ) { _ ->
            Text("This is my note")
        }

        ChatBubbleLayout(
            isLoggedInUser = true,
            isDraft = false,
            innerQuote = false,
            drawAuthorInfo = true,
            groupPosition = ChatGroupPosition.TOP,
            parentBackgroundColor = bgColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            symbol = MaterialSymbols.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape),
                        )
                    },
                    name = {
                        Text("Me", fontWeight = FontWeight.Bold)
                    },
                )
            },
        ) { _ ->
            Text("This is a very long long loong note")
        }

        ChatBubbleLayout(
            isLoggedInUser = true,
            isDraft = true,
            innerQuote = false,
            drawAuthorInfo = true,
            groupPosition = ChatGroupPosition.MIDDLE,
            parentBackgroundColor = bgColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            symbol = MaterialSymbols.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape),
                        )
                    },
                    name = {
                        Text("Me", fontWeight = FontWeight.Bold)
                    },
                )
            },
        ) { _ ->
            Text("This is a draft note")
        }

        ChatBubbleLayout(
            isLoggedInUser = true,
            isDraft = false,
            innerQuote = false,
            drawAuthorInfo = false,
            groupPosition = ChatGroupPosition.BOTTOM,
            parentBackgroundColor = bgColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            symbol = MaterialSymbols.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape),
                        )
                    },
                    name = {
                        Text("Me", fontWeight = FontWeight.Bold)
                    },
                )
            },
        ) { _ ->
            Text("Short note")
        }
    }
}
