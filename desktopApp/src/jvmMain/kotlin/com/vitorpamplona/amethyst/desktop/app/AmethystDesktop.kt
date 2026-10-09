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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.vitorpamplona.amethyst.commons.ui.app.AmethystApp
import com.vitorpamplona.amethyst.commons.ui.components.DefaultInlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.components.LocalInlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.components.LocalWindowViewModelStoreOwner
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.LocalDrawerMetrics
import com.vitorpamplona.amethyst.commons.ui.note.elements.NowProvider
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocalNotePlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.collectDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystMaterialTheme
import com.vitorpamplona.amethyst.commons.ui.theme.isDarkTheme
import com.vitorpamplona.amethyst.desktop.platform.AmethystWindowExceptionHandlerFactory
import com.vitorpamplona.amethyst.desktop.platform.DesktopDrawerMetrics
import com.vitorpamplona.amethyst.desktop.platform.DesktopTypography
import com.vitorpamplona.amethyst.desktop.platform.IconResources
import com.vitorpamplona.amethyst.desktop.platform.PlatformAppearance
import com.vitorpamplona.amethyst.desktop.platform.PlatformFonts
import com.vitorpamplona.amethyst.desktop.platform.PlatformIconWeight
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.amethyst.desktop.platform.ProvideTitleBarInsets
import com.vitorpamplona.amethyst.desktop.platform.SavedWindowBounds
import com.vitorpamplona.amethyst.desktop.platform.applyNativeWindowChrome
import com.vitorpamplona.amethyst.desktop.platform.boundsToSave
import com.vitorpamplona.amethyst.desktop.platform.clickToActivate
import com.vitorpamplona.amethyst.desktop.platform.defaultWindowSize
import com.vitorpamplona.amethyst.desktop.platform.rememberSystemDark
import com.vitorpamplona.amethyst.desktop.platform.titleBarInsetTop
import com.vitorpamplona.amethyst.desktop.platform.trackWheelScrolling
import com.vitorpamplona.amethyst.desktop.service.images.DesktopImageLoaderSetup
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.media.MediaHttp
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.runHeadlessPublish
import com.vitorpamplona.amethyst.desktop.ui.media.GlobalFullscreenOverlay
import com.vitorpamplona.amethyst.desktop.ui.media.LocalAwtWindow
import com.vitorpamplona.amethyst.desktop.ui.media.LocalIsImmersiveFullscreen
import com.vitorpamplona.amethyst.desktop.ui.media.NowPlayingBar
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import java.awt.Taskbar
import java.awt.Toolkit
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * The desktop window of the shared app: the same [AmethystApp] Android renders, on the desktop graph.
 * Run it with `./gradlew :desktopApp:run` (the legacy app: `runLegacy`). Its files live apart from
 * the legacy app's (`~/.amethyst/app`, or `-Damethyst.dataDir=...`); keys come from the same OS
 * keyring, and the legacy logins are imported on the first start.
 */
@OptIn(ExperimentalComposeUiApi::class, FlowPreview::class)
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
    setX11WindowClass()
    // Read ahead of the first frame, off the UI thread: the OS fonts (an enumeration of every
    // installed family) alongside the OS theme (a shell-out), which the first frame needs.
    thread(isDaemon = true, name = "font-warmup") { PlatformFonts.ui }
    PlatformAppearance.startupDark

    val isDebug = System.getProperty("amethyst.debug") == "true"
    Log.minLevel = if (isDebug) LogLevel.DEBUG else LogLevel.INFO

    val filesDir =
        File(System.getProperty("amethyst.dataDir") ?: File(System.getProperty("user.home"), ".amethyst/app").path)
            .apply { mkdirs() }

    val version =
        System.getProperty("amethyst.version")
            ?: DesktopAppModules::class.java.`package`?.implementationVersion
            ?: "dev"

    installDesktopCrashReporter(filesDir, version)
    val modules = DesktopAppModules(filesDir, version, isDebug)

    // Images go through the role builder, so each URL follows the user's Tor choice for images.
    DesktopImageLoaderSetup.setup { url -> modules.roleBasedHttpClientBuilder.okHttpClientForImage(url) }
    // So do video thumbnails, saves and encrypted media, which those clients also decrypt, and
    // the videos Android would send over Tor: those whose video client has a proxy port now.
    MediaHttp.install(
        clientFor = modules.roleBasedHttpClientBuilder::okHttpClientForVideo,
        keyCache = modules.keyCache,
        viaTor = { url -> modules.roleBasedHttpClientBuilder.proxyPortForVideo(url) != null },
    )

    modules.initiate()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            // Releases the native video and audio engines before the JVM goes.
            GlobalMediaPlayer.shutdown()
            runCatching { modules.flushUiPrefs() }
            modules.torManager.stopSync()
        },
    )

    val services = DesktopAppServices(modules)
    val platform = DesktopAppPlatform(appVersionName = version, isDebugBuild = isDebug, notifications = modules.notifications)
    val root = DesktopAppRoot(modules)

    application {
        // Opens where it was left; the first time, or when that spot is off every screen now, at the
        // default size in the middle of the screen.
        val savedBounds = remember { SavedWindowBounds(File(filesDir, "window-bounds.properties")) }
        val windowState =
            remember {
                val saved = savedBounds.load()
                if (saved != null) {
                    WindowState(placement = saved.placement, position = saved.position, size = saved.size)
                } else {
                    WindowState(size = defaultWindowSize(), position = WindowPosition.Aligned(Alignment.Center))
                }
            }

        // Saved as it changes, once the user stops dragging or resizing, so even a crash keeps it.
        LaunchedEffect(windowState) {
            var lastFloating = savedBounds.load()?.copy(maximized = false)
            snapshotFlow { windowState.boundsToSave(lastFloating) }
                .filterNotNull()
                .distinctUntilChanged()
                .debounce(500)
                .collect { bounds ->
                    if (!bounds.maximized) lastFloating = bounds
                    withContext(Dispatchers.IO) { savedBounds.save(bounds) }
                }
        }

        // Bumped to rebuild the window after a crash the user reports from the crash window: the
        // crashed composition can't be reused, but the session, account and size carry over.
        var windowGeneration by remember { mutableIntStateOf(0) }
        val exceptionHandlerFactory =
            remember {
                AmethystWindowExceptionHandlerFactory { report ->
                    services.sendCrashReport(report)
                    windowGeneration++
                }
            }

        // A crash in the window's UI shows our crash window, not Compose's bare system "Error" box.
        CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides exceptionHandlerFactory) {
            key(windowGeneration) {
                Window(
                    onCloseRequest = ::exitApplication,
                    state = windowState,
                    title = "Amethyst",
                    icon = IconResources.adaptedBitmapPainter,
                    onPreviewKeyEvent = { root.navigator.handleShortcut(it, onQuit = ::exitApplication) },
                ) {
                    // macOS: the title bar turns transparent over the app, which draws its own strip there.
                    applyNativeWindowChrome()

                    DesktopMenuBar(root.navigator, onQuit = ::exitApplication)

                    // The window outlives every destination: screens that share state across destinations
                    // (the chess lobby and board, the Cordn group draft) keep it here.
                    val windowViewModels =
                        remember {
                            object : ViewModelStoreOwner {
                                override val viewModelStore = ViewModelStore()
                            }
                        }

                    // Set while a video plays in the OS full-screen overlay.
                    val immersiveFullscreen = remember { mutableStateOf(false) }

                    val systemDark by rememberSystemDark(window)

                    // Set while the wheel scrolls: hover hides then, so the feed doesn't blink under the pointer.
                    val wheelScrolling = remember { mutableStateOf(false) }

                    // Hover shows only while the window is in front, as in native apps, and not mid-scroll.
                    DesktopTheme(modules, systemDark, showHover = LocalWindowInfo.current.isWindowFocused && !wheelScrolling.value) {
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
                                LocalIsImmersiveFullscreen provides immersiveFullscreen,
                                LocalDrawerMetrics provides DesktopDrawerMetrics,
                            ) {
                                // A click on the window in the background only brings it forward.
                                Box(Modifier.fillMaxSize().clickToActivate(window, LocalWindowInfo.current).trackWheelScrolling(wheelScrolling)) {
                                    Column(Modifier.fillMaxSize()) {
                                        // Under macOS's transparent title bar the screens draw their own top
                                        // bars, padded past the traffic lights by the caption-bar inset, as on
                                        // Android under the status bar. Full screen has no title bar.
                                        val titleBar = if (windowState.placement == WindowPlacement.Fullscreen) 0.dp else titleBarInsetTop
                                        ProvideTitleBarInsets(titleBar) {
                                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                                AmethystApp(modules.sessionManager, root)
                                                SnackbarHost(platform.snackbarHostState, Modifier.align(Alignment.BottomCenter))
                                            }
                                        }
                                        // Whatever plays keeps playing across screens and after its card
                                        // scrolls away; this bar is where it is paused, seeked or stopped.
                                        NowPlayingBar()
                                    }
                                    // The video player's full-screen button: the playing video over the
                                    // whole window, in OS full screen.
                                    GlobalFullscreenOverlay()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The shared Material theme, driven by the UI settings the user picked in the app, in the OS's own
 * look: "system" theme follows the OS's dark/light setting, "system" font is the OS's UI font, and
 * icons take the stroke weight of the OS's own icons. The colors stay the app's.
 */
@Composable
private fun DesktopTheme(
    modules: DesktopAppModules,
    systemDark: Boolean,
    showHover: Boolean,
    content: @Composable () -> Unit,
) {
    val prefs = modules.uiPrefs
    val theme by prefs.theme.collectAsState()
    val accentColor by prefs.accentColor.collectAsState()
    val fontFamily by prefs.fontFamily.collectAsState()
    val fontSize by prefs.fontSize.collectAsState()
    val displaySettings = collectDisplaySettings(modules.uiState)

    AmethystMaterialTheme(
        darkTheme = isDarkTheme(theme, systemDark),
        accentColor = accentColor,
        fontFamily = fontFamily,
        fontSize = fontSize,
        displaySettings = displaySettings,
        systemFontFamily = PlatformFonts.ui,
        iconWeight = PlatformIconWeight.current,
        typography = DesktopTypography,
        showHover = showHover,
        content = content,
    )
}

/**
 * X11 (and XWayland) docks and app switchers find a window's app by its WM_CLASS, matched against the
 * `StartupWMClass` of the app's desktop entry. AWT names it after the main class
 * (`com-vitorpamplona-amethyst-desktop-app-AmethystDesktopKt`), which no entry declares, so the
 * switcher showed a generic icon and no app name. AWT reads the name when it creates each window, so
 * this runs before the first one. Needs `--add-opens java.desktop/sun.awt.X11` (set in the build).
 */
private fun setX11WindowClass() {
    try {
        val toolkit = Toolkit.getDefaultToolkit()
        if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return
        toolkit.javaClass
            .getDeclaredField("awtAppClassName")
            .apply { isAccessible = true }
            .set(null, "Amethyst")
    } catch (e: Exception) {
        Log.w("AmethystDesktop", "Could not set the window class", e)
    }
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
