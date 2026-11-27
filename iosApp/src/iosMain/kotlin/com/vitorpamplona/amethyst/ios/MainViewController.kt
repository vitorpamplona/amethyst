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
package com.vitorpamplona.amethyst.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.ui.app.AmethystApp
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocalNotePlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.playback.player.LocalPlaybackPlatform
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.collectDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystMaterialTheme
import com.vitorpamplona.amethyst.commons.ui.theme.collectAccentColor
import com.vitorpamplona.amethyst.commons.ui.theme.isDarkTheme
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController

/**
 * Built once per process: the graph outlives every view controller the system asks for. The crash
 * reporter goes in first, so a crash while building the graph is kept too.
 */
private val modules by lazy {
    IosCrashReports.install(IosBuildInfo.VERSION_NAME)
    IosAppModules(appVersion = IosBuildInfo.VERSION_NAME)
}

/**
 * The whole app as a view controller: what the Swift `@main` in `iosApp/xcode` hosts. It renders the
 * shared [AmethystApp], the same app Android and the desktop run.
 */
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MainViewController(): UIViewController {
    val root = IosAppRoot(modules)
    val services = IosAppServices(modules)
    val platform = IosAppPlatform(modules)
    modules.sessionManager.loginWithDefaultAccountIfLoggedOff()
    return ComposeUIViewController {
        val viewModels =
            remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
        IosTheme(modules) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides viewModels,
                LocalAppServices provides services,
                LocalAppPlatform provides platform,
                LocalNotePlatform provides IosNotePlatform,
                LocalPlaybackPlatform provides modules.playbackPlatform,
                LocalRichTextPlatform provides IosRichTextPlatform,
            ) {
                Box(Modifier.fillMaxSize()) {
                    AmethystApp(modules.sessionManager, root)
                    // Toasts, above the home indicator and the keyboard.
                    SnackbarHost(
                        platform.snackbarHostState,
                        Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 64.dp),
                    )
                }
            }
        }
    }
}

/** The shared Material theme with the user's choices, following the system's dark mode. */
@Composable
private fun IosTheme(
    modules: IosAppModules,
    content: @Composable () -> Unit,
) {
    val prefs = modules.uiPrefs
    val theme by prefs.theme.collectAsState()
    val fontFamily by prefs.fontFamily.collectAsState()
    val fontSize by prefs.fontSize.collectAsState()

    // Native pieces (status bar, share sheet, pickers, alerts, the AirPlay menu) follow the window's
    // interface style, which follows the phone unless set: match it to the app's own choice, as
    // Android sets its status bar to the app theme.
    LaunchedEffect(theme) {
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.keyWindow?.overrideUserInterfaceStyle =
            when (theme) {
                ThemeType.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
                ThemeType.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
                else -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            }
    }

    AmethystMaterialTheme(
        darkTheme = isDarkTheme(theme),
        accentColor = collectAccentColor(modules.sessionManager, prefs.accentColor),
        fontFamily = fontFamily,
        fontSize = fontSize,
        displaySettings = collectDisplaySettings(modules.uiState),
        content = content,
    )
}
