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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The shared visual language for the Buzz surface — one energetic diagonal gradient derived from
 * the active Material theme (so it stays coherent in light and dark, and follows any custom theme)
 * rather than hard-coded brand colors. Used on the workspace hero, the console/DM action cards and
 * the invite screen so every Buzz screen reads as one exciting, cohesive product.
 */
object BuzzBrand {
    /** The signature diagonal wash: primary → tertiary → secondary, top-start to bottom-end. */
    @Composable
    @ReadOnlyComposable
    fun heroBrush(): Brush {
        val scheme = MaterialTheme.colorScheme
        return Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary))
    }

    /** A softer container wash for cards that sit on the surface, not the hero. */
    @Composable
    @ReadOnlyComposable
    fun cardBrush(): Brush {
        val scheme = MaterialTheme.colorScheme
        return Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer))
    }

    /** Content color that stays legible on top of [heroBrush]. */
    @Composable
    @ReadOnlyComposable
    fun onHero(): Color = MaterialTheme.colorScheme.onPrimary
}
