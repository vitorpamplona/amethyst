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
package com.vitorpamplona.amethyst.ui.note.creators.pow

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font14SP

val POW_PRESETS = listOf(16, 20, 24, 28)

/**
 * Composer chip showing the NIP-13 difficulty this post will be mined at.
 * Tapping opens a menu to raise/lower/disable mining for this post only —
 * the account setting is untouched.
 *
 * [effectiveDifficulty] is what will actually be used at send time (override
 * or account default); null/0 means the post publishes without PoW.
 * [defaultDifficulty] is what the account settings alone would produce, shown
 * in the "default" menu entry. [onSelect] receives null to follow the account
 * default, 0 to disable for this post, or a positive difficulty.
 */
@Composable
fun PowOverrideButton(
    effectiveDifficulty: Int?,
    defaultDifficulty: Int?,
    isOverridden: Boolean,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text =
                    if (effectiveDifficulty != null && effectiveDifficulty > 0) {
                        stringRes(R.string.pow_chip_active, effectiveDifficulty)
                    } else {
                        stringRes(R.string.pow_chip_off)
                    },
                fontSize = Font14SP,
                fontWeight = FontWeight.Bold,
                color =
                    if (isOverridden || (effectiveDifficulty != null && effectiveDifficulty > 0)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (defaultDifficulty != null && defaultDifficulty > 0) {
                            stringRes(R.string.pow_option_default_on, defaultDifficulty)
                        } else {
                            stringRes(R.string.pow_option_default_off)
                        },
                    )
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(R.string.pow_option_off)) },
                onClick = {
                    onSelect(0)
                    expanded = false
                },
            )
            POW_PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.pow_option_bits, preset)) },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}
