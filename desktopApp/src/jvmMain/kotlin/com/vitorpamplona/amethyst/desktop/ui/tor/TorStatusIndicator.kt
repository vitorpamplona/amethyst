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
package com.vitorpamplona.amethyst.desktop.ui.tor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus

/**
 * Small shield icon showing Tor connection status in the sidebar footer.
 * Tooltip shows status text (no port number for security).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TorStatusIndicator(
    status: TorServiceStatus,
    modifier: Modifier = Modifier,
) {
    val (icon, tint, tooltip) =
        when (status) {
            is TorServiceStatus.Off -> {
                Triple(Icons.Outlined.Shield, Color.Gray, "Tor: Off")
            }

            is TorServiceStatus.Connecting -> {
                Triple(Icons.Filled.Shield, Color(0xFFFFB300), "Tor: Connecting...")
            }

            is TorServiceStatus.Active -> {
                Triple(Icons.Filled.Shield, Color(0xFF4CAF50), "Tor: Connected")
            }

            is TorServiceStatus.Error -> {
                Triple(Icons.Outlined.Shield, Color(0xFFF44336), "Tor: ${status.message}")
            }
        }

    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
            ) {
                Text(
                    text = tooltip,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        tooltipPlacement = TooltipPlacement.CursorPoint(alignment = Alignment.BottomEnd, offset = DpOffset(0.dp, 16.dp)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = tint,
            modifier = modifier.size(20.dp),
        )
    }
}
