/**
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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import com.vitorpamplona.amethyst.ui.theme.ripple24dp

@Composable
fun ClickableBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.clickable(
            role = Role.Button,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple24dp,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun ClickAndHoldBox(
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            // Button is pressed
            onPress()
        } else {
            // Button is released
            onRelease()
        }
    }

    // Animation for the button scale
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.5f else 1.0f, // Scale up when recording
        animationSpec = tween(durationMillis = 150), // Smooth animation
    )

    // Animation for the button color
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
        animationSpec = tween(durationMillis = 150),
    )

    Box(
        modifier
            .scale(scale)
            .background(backgroundColor, CircleShape)
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple24dp,
                onClick = { },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(isPressed)
    }
}

@Composable
fun ClickAndHoldBoxComposable(
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
    onRelease: suspend () -> Unit,
    onCancel: suspend () -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        val pressInteractions = mutableListOf<PressInteraction.Press>()
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    if (pressInteractions.isEmpty()) {
                        onPress()
                    }
                    pressInteractions.add(interaction)
                }
                is PressInteraction.Release -> {
                    onRelease()
                    pressInteractions.remove(interaction.press)
                }
                is PressInteraction.Cancel -> {
                    onCancel()
                    pressInteractions.remove(interaction.press)
                }
            }
            isPressed = pressInteractions.isNotEmpty()
        }
    }

    // Animation for the button scale
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.5f else 1.0f, // Scale up when recording
        animationSpec = tween(durationMillis = 150), // Smooth animation
    )

    // Animation for the button color
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
        animationSpec = tween(durationMillis = 150),
    )

    Box(
        modifier
            .scale(scale)
            .background(backgroundColor, CircleShape)
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple24dp,
                onClick = { },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(isPressed)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickableBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.combinedClickable(
            role = Role.Button,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple24dp,
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
