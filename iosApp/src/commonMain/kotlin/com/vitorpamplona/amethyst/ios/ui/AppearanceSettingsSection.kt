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
package com.vitorpamplona.amethyst.ios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ios.settings.AppSettings
import com.vitorpamplona.amethyst.ios.settings.FontSizePreference
import com.vitorpamplona.amethyst.ios.settings.NoteDensity
import com.vitorpamplona.amethyst.ios.settings.ThemeMode

/**
 * Appearance settings section for the Settings screen.
 * Covers theme mode, font size, and note density.
 */
@Composable
fun AppearanceSettingsSection() {
    val themeMode by AppSettings.themeMode.collectAsState()
    val fontSize by AppSettings.fontSize.collectAsState()
    val noteDensity by AppSettings.noteDensity.collectAsState()

    // Section header
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        Icon(
            Icons.Default.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Appearance",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }

    // ── Theme Mode ──
    Text(
        "Theme",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ThemeChip(
            label = "System",
            icon = Icons.Default.PhoneAndroid,
            selected = themeMode == ThemeMode.SYSTEM,
            onClick = { AppSettings.setTheme(ThemeMode.SYSTEM) },
            modifier = Modifier.weight(1f),
        )
        ThemeChip(
            label = "Light",
            icon = Icons.Default.LightMode,
            selected = themeMode == ThemeMode.LIGHT,
            onClick = { AppSettings.setTheme(ThemeMode.LIGHT) },
            modifier = Modifier.weight(1f),
        )
        ThemeChip(
            label = "Dark",
            icon = Icons.Default.DarkMode,
            selected = themeMode == ThemeMode.DARK,
            onClick = { AppSettings.setTheme(ThemeMode.DARK) },
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(Modifier.height(16.dp))

    // ── Font Size ──
    Text(
        "Font Size",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FontSizePreference.entries.forEach { pref ->
            FilterChip(
                selected = fontSize == pref,
                onClick = { AppSettings.setFontSize(pref) },
                label = {
                    Text(
                        when (pref) {
                            FontSizePreference.SMALL -> "Small"
                            FontSizePreference.MEDIUM -> "Medium"
                            FontSizePreference.LARGE -> "Large"
                        },
                    )
                },
                leadingIcon =
                    if (fontSize == pref) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                modifier = Modifier.weight(1f),
            )
        }
    }

    // Font size preview
    Text(
        "The quick brown fox jumps over the lazy dog.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )

    Spacer(Modifier.height(8.dp))

    // ── Note Density ──
    Text(
        "Note Density",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        NoteDensity.entries.forEach { density ->
            FilterChip(
                selected = noteDensity == density,
                onClick = { AppSettings.setNoteDensity(density) },
                label = {
                    Text(
                        when (density) {
                            NoteDensity.COMPACT -> "Compact"
                            NoteDensity.COMFORTABLE -> "Comfortable"
                        },
                    )
                },
                leadingIcon =
                    if (noteDensity == density) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Text(
        when (noteDensity) {
            NoteDensity.COMPACT -> "Shows more notes per screen with less spacing."
            NoteDensity.COMFORTABLE -> "Default spacing for easy reading."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ThemeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        modifier = modifier,
    )
}
