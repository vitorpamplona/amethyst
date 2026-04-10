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
package com.vitorpamplona.amethyst.ios.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Status indicator dot shown on user avatars.
 * Displays a small colored circle when the user has an active status (NIP-38).
 */
@Composable
fun UserStatusDot(
    hasStatus: Boolean,
    isMusic: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!hasStatus) return

    Box(
        modifier =
            modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (isMusic) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary)
                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
    )
}

/**
 * Inline user status badge showing the status text and optional music icon.
 * Displayed on profile pages beneath the user name.
 */
@Composable
fun UserStatusBadge(
    statusText: String,
    statusType: String = "general",
    statusUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    if (statusText.isBlank()) return

    val isMusic = statusType == "music"
    val icon = if (isMusic) "🎵" else "💬"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.width(4.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
