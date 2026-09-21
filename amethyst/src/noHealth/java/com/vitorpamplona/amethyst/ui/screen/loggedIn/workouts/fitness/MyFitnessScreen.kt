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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.fitness

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * My Fitness summarises Health Connect data, which this channel does not ship, so the destination
 * is dropped from the drawer and the bottom-bar catalog via
 * `BuildConfig.IS_HEALTH_CONNECT_AVAILABLE`.
 *
 * Nothing can navigate here, but the route stays registered so AppNavigation is channel-agnostic;
 * should a stale synced setting still point at it, this bounces straight back rather than parking
 * the user on a blank screen.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun MyFitnessScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LaunchedEffect(Unit) { nav.popBack() }
}
