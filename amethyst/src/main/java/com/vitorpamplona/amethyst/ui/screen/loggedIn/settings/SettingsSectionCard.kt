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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.stringRes
import androidx.compose.material3.Icon as Material3Icon

@Composable
internal fun SettingsSection(
    title: Int,
    isDanger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringRes(title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color =
                if (isDanger) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(content = content)
        }
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Navigation row: icon + title + optional `trailing` (count badge, etc.) + chevron. */
@Composable
internal fun SettingsItem(
    title: Int,
    icon: MaterialSymbol,
    isDanger: Boolean = false,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    SettingsItemRow(
        title = title,
        isDanger = isDanger,
        trailing = trailing,
        onClick = onClick,
    ) { tint ->
        Icon(
            symbol = icon,
            contentDescription = stringRes(title),
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

/** Navigation row variant taking a drawable painter as the leading icon. */
@Composable
internal fun SettingsItem(
    title: Int,
    iconPainter: Int,
    iconPainterRef: Int,
    isDanger: Boolean = false,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val painter: Painter = painterRes(iconPainter, iconPainterRef)
    SettingsItemRow(
        title = title,
        isDanger = isDanger,
        trailing = trailing,
        onClick = onClick,
    ) { tint ->
        Material3Icon(
            painter = painter,
            contentDescription = stringRes(title),
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

@Composable
private fun SettingsItemRow(
    title: Int,
    isDanger: Boolean,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
    leadingIcon: @Composable (tint: Color) -> Unit,
) {
    val containerColor =
        if (isDanger) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    val iconTint =
        if (isDanger) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
    val textColor =
        if (isDanger) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBox(containerColor) { leadingIcon(iconTint) }
        Text(
            text = stringRes(title),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
        )
        trailing()
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp).size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Control row: icon + title + description, with an inline `trailing` control (switch, stepper).
 * Clicking anywhere on the row invokes [onClick] — pass `null` to disable row clicks
 * (useful when the control alone should toggle state).
 */
@Composable
internal fun SettingsControlRow(
    icon: MaterialSymbol,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null && enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    val alpha = if (enabled) 1f else 0.5f

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBox(MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = alpha),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        trailing()
    }
}

/**
 * Sub-row variant of [SettingsControlRow]: indented in place of a leading icon,
 * used for controls hierarchically grouped under the row above (e.g. a threshold
 * that only matters when the parent toggle is on).
 */
@Composable
internal fun SettingsSubControlRow(
    title: String,
    description: String,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 68.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        trailing()
    }
}

/**
 * Tile where the control sits below the title + description (e.g. a full-width
 * SegmentedButtonRow). Use when the control would crowd a trailing slot.
 */
@Composable
internal fun SettingsBlockTile(
    icon: MaterialSymbol,
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SettingsIconBox(MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun SettingsIconBox(
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(containerColor),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
