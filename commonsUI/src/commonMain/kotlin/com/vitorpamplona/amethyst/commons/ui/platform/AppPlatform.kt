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
package com.vitorpamplona.amethyst.commons.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The app shell's pieces that shared screens embed but only the front end can draw yet: the
 * bottom navigation bar (its pinned favorites and groups read app stores) and the "around me"
 * location label (it asks for the location permission). Installed at the composition root
 * through [LocalAppPlatform]; each member draws nothing by default.
 */
@Stable
interface AppPlatform {
    @Composable
    fun AppBottomBar(
        selectedRoute: Route?,
        nav: INav,
        accountViewModel: AccountViewModel,
        onClick: (Route) -> Unit,
    ) {}

    @Composable
    fun AroundMeLocationLabel() {}

    /** A full-screen dialog to pick a place (map, search, "use my location"), as a geohash. */
    @Composable
    fun GeohashLocationPickerDialog(
        initialGeohash: String?,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit,
    ) {}

    /** The picker's body, for screens that host it in their own scaffold. */
    @Composable
    fun GeohashLocationPickerContent(
        initialGeohash: String?,
        confirmLabel: String,
        onConfirm: (String) -> Unit,
        modifier: Modifier,
    ) {}

    /** Draws nothing: previews, and front ends still wiring their pieces. */
    object None : AppPlatform
}

/** The front end's [AppPlatform]. Static: it is set once at the root and never changes. */
val LocalAppPlatform = staticCompositionLocalOf<AppPlatform> { AppPlatform.None }

@Composable
fun AppBottomBar(
    selectedRoute: Route?,
    nav: INav,
    accountViewModel: AccountViewModel,
    onClick: (Route) -> Unit,
) = LocalAppPlatform.current.AppBottomBar(selectedRoute, nav, accountViewModel, onClick)

@Composable
fun AroundMeLocationLabel() = LocalAppPlatform.current.AroundMeLocationLabel()

@Composable
fun GeohashLocationPickerDialog(
    initialGeohash: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) = LocalAppPlatform.current.GeohashLocationPickerDialog(initialGeohash, onDismiss, onConfirm)

@Composable
fun GeohashLocationPickerContent(
    initialGeohash: String?,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) = LocalAppPlatform.current.GeohashLocationPickerContent(initialGeohash, confirmLabel, onConfirm, modifier)
