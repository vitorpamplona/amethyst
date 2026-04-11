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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import com.vitorpamplona.amethyst.commons.ui.components.SpinnerSelectionDialog as CommonsSpinnerSelectionDialog
import com.vitorpamplona.amethyst.commons.ui.components.TextSpinner as CommonsTextSpinner

// Backward-compat re-exports: TextSpinner, TitleExplainer, SpinnerSelectionDialog moved to commons

typealias TitleExplainer = com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer

@Composable
fun TextSpinner(
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String,
    options: ImmutableList<TitleExplainer>,
    onSelect: (Int) -> Unit,
) = CommonsTextSpinner(modifier, label, placeholder, options, onSelect)

@Composable
fun TextSpinner(
    placeholder: String,
    options: ImmutableList<TitleExplainer>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    mainElement: @Composable (currentOption: String, modifier: Modifier) -> Unit,
) = CommonsTextSpinner(placeholder, options, onSelect, modifier, mainElement)

@Composable
fun SpinnerSelectionDialog(
    title: String? = null,
    options: ImmutableList<TitleExplainer>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) = CommonsSpinnerSelectionDialog(title, options, onDismiss, onSelect)

@Composable
fun <T> SpinnerSelectionDialog(
    title: String? = null,
    options: ImmutableList<T>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRenderItem: @Composable (T) -> Unit,
) = CommonsSpinnerSelectionDialog(title, options, onSelect, onDismiss, onRenderItem)
