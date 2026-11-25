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
package com.vitorpamplona.amethyst.commons.androiddevices.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.androiddevices.DeviceStatus
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor

/** A phone's proportions, for a frame before the first picture says the real ones. */
internal const val PHONE_RATIO = 9f / 19.5f

/** The bezel around the screen: dark in either theme, as a phone's is. */
private val BezelColor = Color(0xFF0E0E10)
private val BezelShape = RoundedCornerShape(30.dp)
private val ScreenShape = RoundedCornerShape(24.dp)
private val BezelWidth = 6.dp

/**
 * A phone-shaped frame, as large as [ratio] fits in the space given, centered: a dark bezel with a
 * soft shadow around a rounded screen, outlined in the primary color while [focused] (keys go to
 * the device then). Every state of the panel draws in it, so the layout doesn't jump from "starting"
 * to the live screen.
 */
@Composable
internal fun DeviceFrame(
    ratio: Float,
    focused: Boolean,
    modifier: Modifier = Modifier,
    screenModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Widest that fits: by width when the space is taller than the phone, else by height.
        val byWidth = maxWidth / maxHeight < ratio
        Box(
            Modifier
                .aspectRatio(ratio, matchHeightConstraintsFirst = !byWidth)
                .shadow(14.dp, BezelShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(BezelColor, BezelShape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                    shape = BezelShape,
                ).padding(BezelWidth),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(ScreenShape)
                    .background(Color.Black)
                    .then(screenModifier),
                content = content,
            )
        }
    }
}

/** What the frame shows while there's no picture: starting, booting, or why there won't be one. */
@Composable
internal fun BoxScope.FrameMessage(
    text: String,
    progress: Boolean,
) {
    Column(
        Modifier.align(Alignment.Center).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (progress) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White.copy(alpha = 0.85f), strokeWidth = 2.5.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
    }
}

/** Green for ready, amber while it starts, gray when it can't be used. */
@Composable
internal fun statusColor(status: DeviceStatus?): Color =
    when (status) {
        DeviceStatus.ONLINE -> MaterialTheme.colorScheme.allGoodColor
        DeviceStatus.STARTING, DeviceStatus.BOOTING -> MaterialTheme.colorScheme.warningColor
        DeviceStatus.UNAUTHORIZED, DeviceStatus.NO_PERMISSION -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.grayText
    }

@Composable
internal fun StatusDot(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** An icon in a soft tonal circle: the start of a row, or an empty state's picture. */
@Composable
internal fun TonalIcon(
    symbol: MaterialSymbol,
    size: Dp,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(symbol, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** A floating pill of controls, groups split by thin dividers ([ToolbarDivider]). */
@Composable
internal fun ToolbarPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
internal fun ToolbarDivider() {
    VerticalDivider(Modifier.height(18.dp).padding(horizontal = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun ToolbarButton(
    symbol: MaterialSymbol,
    label: String,
    size: Dp = 36.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    HoverTooltip(label) {
        IconButton(onClick = onClick, modifier = Modifier.size(size)) {
            Icon(symbol, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.56f))
        }
    }
}

/** A small green dot with a ring the color of the bar it sits on: a device of this chat is up. */
@Composable
fun DeviceUpBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(9.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(1.5.dp)
            .background(MaterialTheme.colorScheme.allGoodColor, CircleShape),
    )
}
