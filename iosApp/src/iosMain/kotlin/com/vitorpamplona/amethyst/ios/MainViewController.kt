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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
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
import platform.UIKit.UIViewController

/** The per-role HTTP clients arrive with A2; the iOS interface has no members yet. */
internal object IosHttpClientBuilder : IRoleBasedHttpClientBuilder

/** Built once per process: the graph outlives every view controller the system asks for. */
private val modules by lazy { IosAppModules(appVersion = bundleVersion()) }

/**
 * The whole app as a view controller: what the Swift `@main` in `iosApp/xcode` hosts. It renders the
 * shared [AmethystApp], the same app Android and the desktop run.
 */
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun MainViewController(): UIViewController {
    val root = IosAppRoot(modules)
    val services = IosAppServices(modules)
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
                LocalAppPlatform provides IosAppPlatform,
                LocalNotePlatform provides IosNotePlatform,
                LocalPlaybackPlatform provides modules.playbackPlatform,
                LocalRichTextPlatform provides IosRichTextPlatform,
            ) {
                AmethystApp(modules.sessionManager, root)
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

    AmethystMaterialTheme(
        darkTheme = isDarkTheme(theme),
        accentColor = collectAccentColor(modules.sessionManager, prefs.accentColor),
        fontFamily = fontFamily,
        fontSize = fontSize,
        displaySettings = collectDisplaySettings(modules.uiState),
        content = content,
    )
}
