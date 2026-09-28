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
package com.vitorpamplona.amethyst.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ui.navigation.topbars.AndroidAroundMeLocationLabel
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar as AppBottomBarImpl
import com.vitorpamplona.amethyst.ui.note.creators.location.GeohashLocationPickerContent as AppGeohashLocationPickerContent
import com.vitorpamplona.amethyst.ui.note.creators.location.GeohashLocationPickerDialog as AppGeohashLocationPickerDialog

/** Android's [AppPlatform]: the app's own bottom bar and location label. */
object AndroidAppPlatform : AppPlatform {
    @Composable
    override fun AppBottomBar(
        selectedRoute: Route?,
        nav: INav,
        accountViewModel: AccountViewModel,
        onClick: (Route) -> Unit,
    ) = AppBottomBarImpl(selectedRoute, nav, accountViewModel, onClick)

    @Composable
    override fun AroundMeLocationLabel() = AndroidAroundMeLocationLabel()

    @Composable
    override fun GeohashLocationPickerDialog(
        initialGeohash: String?,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit,
    ) = AppGeohashLocationPickerDialog(initialGeohash, onDismiss, onConfirm)

    @Composable
    override fun GeohashLocationPickerContent(
        initialGeohash: String?,
        confirmLabel: String,
        onConfirm: (String) -> Unit,
        modifier: Modifier,
    ) = AppGeohashLocationPickerContent(initialGeohash, confirmLabel, onConfirm, modifier)
}
