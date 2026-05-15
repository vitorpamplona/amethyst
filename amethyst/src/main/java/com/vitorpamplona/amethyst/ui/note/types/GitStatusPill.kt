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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.GitStatusIndex
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent

private val PillShape = RoundedCornerShape(8.dp)

enum class StatusKind {
    OPEN,
    APPLIED,
    CLOSED,
    DRAFT,
}

private fun GitStatusEvent.statusKind(): StatusKind =
    when (this) {
        is GitStatusAppliedEvent -> StatusKind.APPLIED
        is GitStatusClosedEvent -> StatusKind.CLOSED
        is GitStatusDraftEvent -> StatusKind.DRAFT
        is GitStatusOpenEvent -> StatusKind.OPEN
        else -> StatusKind.OPEN
    }

@Composable
fun rememberLatestStatus(targetIdHex: String): GitStatusEvent? {
    LaunchedEffect(Unit) { GitStatusIndex.startIfNeeded() }
    val index by GitStatusIndex.latestByTarget.collectAsStateWithLifecycle()
    val latest by remember(targetIdHex) { derivedStateOf { index[targetIdHex] } }
    return latest
}

@Composable
fun GitStatusPill(
    targetIdHex: String,
    modifier: Modifier = Modifier,
    defaultIfMissing: StatusKind? = null,
) {
    val latest = rememberLatestStatus(targetIdHex)
    val kind =
        latest?.statusKind() ?: defaultIfMissing ?: return

    GitStatusPill(kind, modifier)
}

@Composable
fun GitStatusPill(
    kind: StatusKind,
    modifier: Modifier = Modifier,
) {
    val (label, symbol, container, content) =
        when (kind) {
            StatusKind.OPEN -> {
                StatusStyle(
                    label = stringRes(id = R.string.git_status_open),
                    symbol = MaterialSymbols.RadioButtonChecked,
                    container = Color(0xFF1F883D),
                    content = Color.White,
                )
            }

            StatusKind.APPLIED -> {
                StatusStyle(
                    label = stringRes(id = R.string.git_status_merged),
                    symbol = MaterialSymbols.Check,
                    container = Color(0xFF8250DF),
                    content = Color.White,
                )
            }

            StatusKind.CLOSED -> {
                StatusStyle(
                    label = stringRes(id = R.string.git_status_closed),
                    symbol = MaterialSymbols.Cancel,
                    container = Color(0xFFCF222E),
                    content = Color.White,
                )
            }

            StatusKind.DRAFT -> {
                StatusStyle(
                    label = stringRes(id = R.string.git_status_draft),
                    symbol = MaterialSymbols.EditNote,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

    Row(
        modifier =
            modifier
                .clip(PillShape)
                .background(container)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = content,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}

private data class StatusStyle(
    val label: String,
    val symbol: MaterialSymbol,
    val container: Color,
    val content: Color,
)
