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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols

/**
 * The shared chrome for an embedded web tab — the browser/web-app tab and the nsite/napplet tab use the
 * same bar so they read as one consistent surface: a **leading** affordance specific to the surface
 * (the napplet/nsite tab puts its sandbox-access **shield** here; the web-app tab puts the Tor onion),
 * the title, **reload**, and **open-in-own-window**.
 *
 * Tor uses the app's onion (`ic_tor`) rather than the shield, so the shield always means "sandbox
 * access" and is never mistaken for a Tor toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedTabTopBar(
    title: String,
    leading: @Composable () -> Unit,
    onReload: () -> Unit,
    onPopOut: () -> Unit,
) {
    TopAppBar(
        navigationIcon = leading,
        title = {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        actions = {
            IconButton(onClick = onReload) {
                Icon(MaterialSymbols.Refresh, contentDescription = stringResource(R.string.browser_reload))
            }
            IconButton(onClick = onPopOut) {
                Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = stringResource(R.string.favorite_app_open_window))
            }
        },
    )
}
