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

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ScanOutcome
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsCategory
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.ui.navigation.topbars.AndroidAroundMeLocationLabel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.activity.NestActivity
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.AppBottomBar as AppBottomBarImpl
import com.vitorpamplona.amethyst.favorites.rememberManifestIconModel as AppRememberManifestIconModel
import com.vitorpamplona.amethyst.favorites.rememberNappletIconModel as AppRememberNappletIconModel
import com.vitorpamplona.amethyst.favorites.rememberWebAppIconModel as AppRememberWebAppIconModel
import com.vitorpamplona.amethyst.ui.note.creators.location.GeohashLocationPickerContent as AppGeohashLocationPickerContent
import com.vitorpamplona.amethyst.ui.note.creators.location.GeohashLocationPickerDialog as AppGeohashLocationPickerDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.rememberConcordImageModel as AppRememberConcordImageModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner.QrCodeScannerDialog as AppQrCodeScannerDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.legalSettingsCategory as flavorLegalSettingsCategory

/** Android's [AppPlatform]: the app's own shell pieces, camera scanner, app launcher and icon caches. */
object AndroidAppPlatform : AppPlatform {
    override val isCastingAvailable: Boolean get() = BuildConfig.IS_CASTING_AVAILABLE

    // NestActivity.launch adds FLAG_ACTIVITY_NEW_TASK, so the application context can start it.
    override fun openNestRoom(addressValue: String) = NestActivity.launch(Amethyst.instance.appContext, addressValue)

    // Per flavour: Play links the hosted policies, F-Droid surfaces none.
    override fun legalSettingsCategory(uriHandler: UriHandler): SettingsCategory? = flavorLegalSettingsCategory(uriHandler)

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

    @Composable
    override fun QrCodeScannerDialog(
        onDismiss: () -> Unit,
        onScan: (String) -> ScanOutcome,
    ) = AppQrCodeScannerDialog(onDismiss, onScan)

    @Composable
    override fun rememberAppLauncher(): AppLauncher {
        val context = LocalContext.current
        return remember(context) { AndroidAppLauncher(context) }
    }

    @Composable
    override fun rememberConcordImageModel(
        pointer: ImagePointer?,
        accountViewModel: AccountViewModel,
    ): String? = AppRememberConcordImageModel(pointer, accountViewModel)

    @Composable
    override fun rememberNappletIconModel(coordinate: String): String? = AppRememberNappletIconModel(coordinate)

    @Composable
    override fun rememberManifestIconModel(
        author: String,
        identifier: String,
    ): String? = AppRememberManifestIconModel(author, identifier)

    @Composable
    override fun rememberWebAppIconModel(url: String): String? = AppRememberWebAppIconModel(url)
}

private class AndroidAppLauncher(
    private val context: Context,
) : AppLauncher {
    override fun launch(
        app: FavoriteApp,
        stillLoading: String,
    ) = FavoriteAppLauncher.launch(context, app, stillLoading)

    override fun launchUrl(
        url: String,
        preferTor: Boolean,
    ) = FavoriteAppLauncher.launchUrl(context, url, preferTor)
}
