/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

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
    text: Int = R.string.add,
    isActive: Boolean = true,
    modifier: Modifier = Modifier.padding(start = 3.dp),
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onClick()
            }
        },
        shape = ButtonBorder,
        enabled = isActive,
        contentPadding = PaddingValues(vertical = 0.dp, horizontal = 16.dp),
    ) {
        Text(text = stringRes(text), color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
fun RemoveButton(
    isActive: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.padding(start = 3.dp),
        onClick = {
            if (isActive) {
                onClick()
            }
        },
        shape = ButtonBorder,
        enabled = isActive,
        contentPadding = PaddingValues(vertical = 0.dp, horizontal = 16.dp),
    ) {
        Text(text = stringRes(R.string.remove), color = Color.White)
    }
}
