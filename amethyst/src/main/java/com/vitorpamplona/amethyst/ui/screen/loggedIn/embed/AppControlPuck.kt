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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol

/**
 * Vertical space a collapsed [AppControlPuck] occupies (the 48dp touch target + its 8dp inset). Embedded
 * tabs inset their reserved surface bounds by this at the top so the warm surface — which the tab layer
 * draws *over* those bounds, above the whole nav tree — doesn't cover the puck. Full-screen activities
 * don't need it: there the puck is a sibling drawn on top of the surface.
 */
val AppControlPuckReserve: Dp = 56.dp

/**
 * A floating, collapsible control chip for a running app surface (web client / nsite / napplet) — the
 * PWA-inspired replacement for a full-width top bar. Apps already title themselves, so instead of a bar
 * that repeats the name we float a single small puck in a corner that:
 *
 *  - **always shows the trusted marker** (the sandbox shield for napplets/nsites, a lock for the plain
 *    browser) — this is the anti-phishing affordance the sandboxed page can never draw over, so it must
 *    stay visible even collapsed, and
 *  - **expands on tap** to reveal the surface's actions (reload, Tor, pop-out, access sheet, close).
 *
 * Stateless apart from its own expanded/collapsed toggle: the caller supplies the trusted icon and the
 * action buttons, so the same puck backs every surface (embedded tab + full-screen activity).
 */
@Composable
fun AppControlPuck(
    trustedIcon: MaterialSymbol,
    trustedTint: Color,
    trustedDescription: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The trusted marker doubles as the expand/collapse toggle. It stays put; the actions slide
            // out from behind it so the marker is always anchored in the corner.
            IconButton(onClick = { expanded = !expanded }) {
                Icon(trustedIcon, contentDescription = trustedDescription, tint = trustedTint)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}
