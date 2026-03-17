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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.domain.nip46.SignerConnectionState
import com.vitorpamplona.quartz.utils.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BunkerHeartbeatIndicator(
    signerConnectionState: SignerConnectionState,
    lastPingTimeSec: Long?,
    modifier: Modifier = Modifier,
) {
    if (signerConnectionState is SignerConnectionState.NotRemote) return

    val tooltipState = rememberTooltipState()
    val tooltipText =
        when (signerConnectionState) {
            is SignerConnectionState.Connected -> {
                if (lastPingTimeSec != null) {
                    val agoSeconds = TimeUtils.now() - lastPingTimeSec
                    "Bunker connected \u2014 last pinged ${agoSeconds}s ago"
                } else {
                    "Bunker connected"
                }
            }

            is SignerConnectionState.Disconnected -> {
                "Bunker disconnected"
            }
        }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltipText) } },
        state = tooltipState,
        modifier = modifier,
    ) {
        when (signerConnectionState) {
            is SignerConnectionState.Connected -> {
                val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.85f,
                    targetValue = 1.15f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "heartbeatScale",
                )
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Bunker connected",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp).scale(scale),
                )
            }

            is SignerConnectionState.Disconnected -> {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = "Bunker disconnected",
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
