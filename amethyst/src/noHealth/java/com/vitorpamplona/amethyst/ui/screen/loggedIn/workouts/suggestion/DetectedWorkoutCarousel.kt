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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.suggestion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * Health Connect is not compiled into this channel, so the New Workout composer has nothing to
 * pre-load from and renders no carousel: no permission check, no reader, no Connect card.
 *
 * The real implementation lives in `src/health` (the complete and fdroid channels). This stub
 * keeps the call site in NewWorkoutScreen identical across channels instead of wrapping it in a
 * BuildConfig branch that would still have to resolve the composable at compile time.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun DetectedWorkoutCarousel(
    accountViewModel: AccountViewModel,
    onPick: (Route.NewWorkout) -> Unit,
    modifier: Modifier = Modifier,
) {
}
