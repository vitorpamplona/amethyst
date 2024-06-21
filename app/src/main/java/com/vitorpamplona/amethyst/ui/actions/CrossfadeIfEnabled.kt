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
package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.fastForEach
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun <T> CrossfadeIfEnabled(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    animationSpec: FiniteAnimationSpec<Float> = tween(),
    label: String = "Crossfade",
    accountViewModel: AccountViewModel,
    content: @Composable (T) -> Unit,
) {
    if (accountViewModel.settings.featureSet == FeatureSetType.PERFORMANCE) {
        Box(modifier, contentAlignment) {
            content(targetState)
        }
    } else {
        MyCrossfade(targetState, modifier, contentAlignment, animationSpec, label, content)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <T> MyCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    animationSpec: FiniteAnimationSpec<Float> = tween(),
    label: String = "Crossfade",
    content: @Composable (T) -> Unit,
) {
    val transition = updateTransition(targetState, label)
    transition.MyCrossfade(modifier, contentAlignment, animationSpec, content = content)
}

@ExperimentalAnimationApi
@Composable
fun <T> Transition<T>.MyCrossfade(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    animationSpec: FiniteAnimationSpec<Float> = tween(),
    contentKey: (targetState: T) -> Any? = { it },
    content: @Composable (targetState: T) -> Unit,
) {
    val currentlyVisible = remember { mutableStateListOf<T>().apply { add(currentState) } }
    val contentMap =
        remember {
            mutableMapOf<T, @Composable () -> Unit>()
        }
    if (currentState == targetState) {
        // If not animating, just display the current state
        if (currentlyVisible.size != 1 || currentlyVisible[0] != targetState) {
            // Remove all the intermediate items from the list once the animation is finished.
            currentlyVisible.removeAll { it != targetState }
            contentMap.clear()
        }
    }
    if (!contentMap.contains(targetState)) {
        // Replace target with the same key if any
        val replacementId =
            currentlyVisible.indexOfFirst {
                contentKey(it) == contentKey(targetState)
            }
        if (replacementId == -1) {
            currentlyVisible.add(targetState)
        } else {
            currentlyVisible[replacementId] = targetState
        }
        contentMap.clear()
        currentlyVisible.fastForEach { stateForContent ->
            contentMap[stateForContent] = {
                val alpha by animateFloat(
                    transitionSpec = { animationSpec },
                ) { if (it == stateForContent) 1f else 0f }
                Box(Modifier.graphicsLayer { this.alpha = alpha }, contentAlignment) {
                    content(stateForContent)
                }
            }
        }
    }

    Box(modifier, contentAlignment) {
        currentlyVisible.fastForEach {
            key(contentKey(it)) {
                contentMap[it]?.invoke()
            }
        }
    }
}
