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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.components.AddButton as CommonsAddButton
import com.vitorpamplona.amethyst.commons.ui.components.RemoveButton as CommonsRemoveButton

@Composable
@Preview
fun AddButtonPreview() {
    ThemeComparisonColumn {
        Row {
            Column {
                AddButton(isActive = true) {}
                AddButton(isActive = false) {}
            }

            Column {
                RemoveButton(isActive = true) {}
                RemoveButton(isActive = false) {}
            }
        }
    }
}

@Composable
fun AddButton(
    modifier: Modifier = Modifier,
    text: Int = R.string.add,
    isActive: Boolean = true,
    onClick: () -> Unit,
) {
    CommonsAddButton(
        onClick = onClick,
        modifier = modifier,
        text = stringRes(text),
        enabled = isActive,
    )
}

@Composable
fun RemoveButton(
    modifier: Modifier = Modifier,
    text: Int = R.string.remove,
    isActive: Boolean = true,
    onClick: () -> Unit,
) {
    CommonsRemoveButton(
        onClick = onClick,
        modifier = modifier,
        text = stringRes(text),
        enabled = isActive,
    )
}
