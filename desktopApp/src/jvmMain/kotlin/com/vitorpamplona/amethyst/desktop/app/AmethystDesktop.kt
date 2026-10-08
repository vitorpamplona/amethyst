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
package com.vitorpamplona.amethyst.desktop.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.vitorpamplona.amethyst.commons.ui.app.AmethystApp
import com.vitorpamplona.amethyst.commons.ui.components.DefaultInlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.components.LocalInlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.components.LocalWindowViewModelStoreOwner
import com.vitorpamplona.amethyst.commons.ui.note.elements.NowProvider
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocalNotePlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.collectDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystMaterialTheme
import com.vitorpamplona.amethyst.commons.ui.theme.isDarkTheme
import com.vitorpamplona.amethyst.desktop.platform.IconResources
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.amethyst.desktop.service.images.DesktopImageLoaderSetup
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.runHeadlessPublish
import com.vitorpamplona.amethyst.desktop.ui.media.LocalAwtWindow
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import java.awt.Taskbar
import java.io.File
import kotlin.system.exitProcess

/**
 * The desktop window of the shared app: the same [AmethystApp] Android renders, on the desktop graph.
 * Run it with `./gradlew :desktopApp:run` (the legacy app: `runLegacy`). Its files live apart from
 * the legacy app's (`~/.amethyst/app`, or `-Damethyst.dataDir=...`); keys come from the same OS
 * keyring, and the legacy logins are imported on the first start.
 */
fun main(args: Array<String>) {
    // The OS timer relaunches the app this way to publish scheduled posts while it is closed: no
    // window, no keyring, just the pre-signed posts that are due.
    if (args.contains("--publish-scheduled")) exitProcess(runHeadlessPublish())

    // macOS: the menu bar goes to the top of the screen, under the app's own name. Both must be set
    // before AWT starts.
    if (PlatformInfo.isMacOS) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.name", "Amethyst")
        System.setProperty("apple.awt.application.appearance", "system")
    }
    setDockIcon()

    val isDebug = System.getProperty("amethyst.debug") == "true"
    Log.minLevel = if (isDebug) LogLevel.DEBUG else LogLevel.INFO

    val filesDir =
        File(System.getProperty("amethyst.dataDir") ?: File(System.getProperty("user.home"), ".amethyst/app").path)
            .apply { mkdirs() }

    val version =
        System.getProperty("amethyst.version")
            ?: DesktopAppModules::class.java.`package`?.implementationVersion
            ?: "dev"

    val modules = DesktopAppModules(filesDir, version, isDebug)

    // Images go through the role builder, so each URL follows the user's Tor choice for images.
    DesktopImageLoaderSetup.setup { url -> modules.roleBasedHttpClientBuilder.okHttpClientForImage(url) }

    modules.initiate()

    Runtime.getRuntime().addShutdownHook(Thread { modules.torManager.stopSync() })

    val services = DesktopAppServices(modules)
    val platform = DesktopAppPlatform(appVersionName = version, isDebugBuild = isDebug, notifications = modules.notifications)
    val root = DesktopAppRoot(modules)

    application {
        val windowState =
            rememberWindowState(
                width = 1200.dp,
                height = 800.dp,
                position = WindowPosition.Aligned(Alignment.Center),
            )

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Amethyst",
            icon = IconResources.adaptedBitmapPainter,
        ) {
            DesktopMenuBar(root.navigator, onQuit = ::exitApplication)

            // The window outlives every destination: screens that share state across destinations
            // (the chess lobby and board, the Cordn group draft) keep it here.
            val windowViewModels =
                remember {
                    object : ViewModelStoreOwner {
                        override val viewModelStore = ViewModelStore()
                    }
                }

            DesktopTheme(modules) {
                NowProvider {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides windowViewModels,
                        LocalWindowViewModelStoreOwner provides windowViewModels,
                        LocalAppServices provides services,
                        LocalAppPlatform provides platform,
                        LocalNotePlatform provides DesktopNotePlatform,
                        LocalRichTextPlatform provides DesktopRichTextPlatform,
                        LocalInlineQuoteRenderer provides DefaultInlineQuoteRenderer,
                        // The lightbox and the video player go full screen through the AWT window.
                        LocalAwtWindow provides window,
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            AmethystApp(modules.sessionManager, root)
                            SnackbarHost(platform.snackbarHostState, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
    }
}

/** The shared Material theme, driven by the UI settings the user picked in the app. */
@Composable
private fun DesktopTheme(
    modules: DesktopAppModules,
    content: @Composable () -> Unit,
) {
    val prefs = modules.uiPrefs
    val theme by prefs.theme.collectAsState()
    val accentColor by prefs.accentColor.collectAsState()
    val fontFamily by prefs.fontFamily.collectAsState()
    val fontSize by prefs.fontSize.collectAsState()
    val displaySettings = collectDisplaySettings(modules.uiState)

    AmethystMaterialTheme(
        darkTheme = isDarkTheme(theme),
        accentColor = accentColor,
        fontFamily = fontFamily,
        fontSize = fontSize,
        displaySettings = displaySettings,
        content = content,
    )
}

/**
 * The dock and app-switcher icon. macOS reads it from the Taskbar API, not from the window's icon,
 * so without this a JVM started from Gradle shows the generic Java icon.
 */
private fun setDockIcon() {
    try {
        val image = IconResources.adaptedBufferedImage ?: return
        if (!Taskbar.isTaskbarSupported()) return
        val taskbar = Taskbar.getTaskbar()
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) taskbar.iconImage = image
    } catch (e: Exception) {
        Log.w("AmethystDesktop", "Could not set the dock icon", e)
    }
}
