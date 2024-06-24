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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import kotlinx.collections.immutable.ImmutableList

@Composable
fun TextSpinner(
    label: String?,
    placeholder: String,
    options: ImmutableList<TitleExplainer>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextSpinner(
        placeholder,
        options,
        onSelect,
        modifier,
    ) { currentOption, modifier ->
        OutlinedTextField(
            value = currentOption,
            onValueChange = {},
            readOnly = true,
            label = { label?.let { Text(it) } },
            modifier = modifier,
            singleLine = true,
        )
    }
}

@Composable
fun TextSpinner(
    placeholder: String,
    options: ImmutableList<TitleExplainer>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    mainElement: @Composable (currentOption: String, modifier: Modifier) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    var optionsShowing by remember { mutableStateOf(false) }
    var currentText by remember { mutableStateOf(placeholder) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        mainElement(
            currentText,
            remember { Modifier.fillMaxWidth().focusRequester(focusRequester) },
        )
        Box(
            modifier =
                Modifier.matchParentSize().clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    optionsShowing = true
                    focusRequester.requestFocus()
                },
        )
    }

    if (optionsShowing) {
        options.isNotEmpty().also {
            SpinnerSelectionDialog(options = options, onDismiss = { optionsShowing = false }) {
                currentText = options[it].title
                optionsShowing = false
                onSelect(it)
            }
        }
    }
}

@Composable
fun SpinnerSelectionDialog(
    title: String? = null,
    options: ImmutableList<TitleExplainer>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    SpinnerSelectionDialog(
        title = title,
        options = options,
        onSelect = onSelect,
        onDismiss = onDismiss,
    ) { item ->
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = item.title, color = MaterialTheme.colorScheme.onSurface)
        }
        item.explainer?.let {
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = it, color = Color.Gray, fontSize = Font14SP)
            }
        }
    }
}

@Composable
fun <T> SpinnerSelectionDialog(
    title: String? = null,
    options: ImmutableList<T>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRenderItem: @Composable (T) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            border = BorderStroke(0.25.dp, Color.LightGray),
            shape = RoundedCornerShape(5.dp),
        ) {
            LazyColumn {
                title?.let {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        HorizontalDivider(color = Color.LightGray, thickness = DividerThickness)
                    }
                }
                itemsIndexed(options) { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(16.dp, 16.dp),
                    ) {
                        Column { onRenderItem(item) }
                    }
                    if (index < options.lastIndex) {
                        HorizontalDivider(color = Color.LightGray, thickness = DividerThickness)
                    }
                }
            }
        }
    }
}

@Immutable data class TitleExplainer(
    val title: String,
    val explainer: String? = null,
)
