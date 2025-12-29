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
package com.vitorpamplona.amethyst.commons.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Generic placeholder screen with title and description.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Search screen placeholder.
 */
@Composable
fun SearchPlaceholder(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Search",
        description = "Search for users, notes, and hashtags.",
        modifier = modifier,
    )
}

/**
 * Messages/DMs screen placeholder.
 */
@Composable
fun MessagesPlaceholder(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Messages",
        description = "Your encrypted direct messages will appear here.",
        modifier = modifier,
    )
}

/**
 * Notifications screen placeholder.
 */
@Composable
fun NotificationsPlaceholder(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Notifications",
        description = "Mentions, replies, and reactions will appear here.",
        modifier = modifier,
    )
}
