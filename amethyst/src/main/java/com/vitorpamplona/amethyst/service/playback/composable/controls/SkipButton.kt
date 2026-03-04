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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.playback.composable.SKIP_SECONDS
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

private val FadeIn = fadeIn()
private val FadeOut = fadeOut()

@Preview
@Composable
fun SkipBackButtonPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            SkipButton(isForward = false, onClick = {})
        }
    }
}

@Preview
@Composable
fun SkipForwardButtonPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            SkipButton(isForward = true, onClick = {})
        }
    }
}

@Composable
fun AnimatedSkipButton(
    controllerVisible: State<Boolean>,
    isForward: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = FadeIn,
        exit = FadeOut,
    ) {
        SkipButton(isForward = isForward, onClick = onClick)
    }
}

@Composable
fun SkipButton(
    isForward: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isForward) Icons.Default.Forward10 else Icons.Default.Replay10
    val label = if (isForward) stringRes(R.string.skip_forward, SKIP_SECONDS) else stringRes(R.string.skip_back, SKIP_SECONDS)
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}
