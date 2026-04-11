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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog as CommonsM3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow as CommonsM3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection as CommonsM3ActionSection

// Backward-compat wrappers: M3ActionDialog, M3ActionSection, M3ActionRow moved to commons

@Composable
fun M3ActionDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) = CommonsM3ActionDialog(title, onDismiss, content)

@Composable
fun M3ActionSection(content: @Composable ColumnScope.() -> Unit) = CommonsM3ActionSection(content)

@Composable
fun M3ActionRow(
    icon: ImageVector,
    text: String,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = CommonsM3ActionRow(icon, text, isDestructive, enabled, onClick)
