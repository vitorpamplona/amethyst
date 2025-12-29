/**
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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Standard button shape used for action buttons.
 */
val ActionButtonShape = RoundedCornerShape(20.dp)

/**
 * Standard content padding for action buttons.
 */
val ActionButtonPadding = PaddingValues(vertical = 0.dp, horizontal = 16.dp)

/**
 * An "Add" action button with consistent styling.
 *
 * @param onClick Action to perform when clicked
 * @param modifier Modifier for the button
 * @param text Button text (default: "Add")
 * @param enabled Whether the button is enabled
 */
@Composable
fun AddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Add",
    enabled: Boolean = true,
) {
    OutlinedButton(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        shape = ActionButtonShape,
        contentPadding = ActionButtonPadding,
    ) {
        Text(text = text, textAlign = TextAlign.Center)
    }
}

/**
 * A "Remove" action button with consistent styling.
 *
 * @param onClick Action to perform when clicked
 * @param modifier Modifier for the button
 * @param text Button text (default: "Remove")
 * @param enabled Whether the button is enabled
 */
@Composable
fun RemoveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Remove",
    enabled: Boolean = true,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        shape = ActionButtonShape,
        enabled = enabled,
        contentPadding = ActionButtonPadding,
    ) {
        Text(text = text)
    }
}
