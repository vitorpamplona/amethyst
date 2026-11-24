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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode

import com.vitorpamplona.agents.ImageInput
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.ScreenToolServer
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.OnScreenNavs
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.toBufferedImage
import java.awt.Container
import java.awt.Window
import java.io.IOException

/** The desktop's screen tools: what each navigation shows, and a picture of each window. */
fun desktopScreenTools(): ScreenToolServer = ScreenToolServer(onScreen = ::describeOnScreen, capture = ::captureWindows)

/**
 * Each part of the window and what it shows, one route a line as the app saves it ("Note" with its
 * hex id, "Profile" with the person's hex key, "Hashtag", "Search"…): the assistant reads what
 * they name with its other tools. Read on the main thread, where navigation changes.
 */
private suspend fun describeOnScreen(): String =
    withContext(Dispatchers.Main) {
        val parts = OnScreenNavs.all()
        if (parts.isEmpty()) return@withContext "Nothing is registered as on screen."
        parts.joinToString("\n\n") { part ->
            val shownEntries = part.nav.shownEntries()
            val shown = shownEntries.map { it.route }
            val windows =
                part.nav.stacks
                    .windows()
                    .map { it.route }
            // Tabs behind the one in front, and the screens earlier in its history: "this" may be one of them.
            val behind =
                part.nav.stacks.stack
                    .filter { entry -> shownEntries.none { it.id == entry.id } }
                    .map { it.route }
                    .distinct()
                    .takeLast(MAX_BEHIND)
            buildString {
                append(part.label).append(" shows:")
                if (shown.isEmpty()) append(" nothing")
                shown.forEach { append("\n- ").append(describe(it)) }
                windows.forEach { append("\n- in its own window: ").append(describe(it)) }
                if (behind.isNotEmpty()) {
                    append("\nAlso open behind it (other tabs, or earlier in its history), most recent last:")
                    behind.forEach { append("\n- ").append(describe(it)) }
                }
            }
        }
    }

private val routeJson = Json { encodeDefaults = false }

private fun describe(route: Route): String {
    val text = routeJson.encodeToString(Route.serializer(), route).replace(ROUTE_PACKAGE, "")
    // The assistant's own chat is on screen too, however it was opened; say so rather than make it read itself.
    val isAssistant = route is Route.ClaudeCodeSettingsAssistant || (route is Route.ClaudeCodeChat && ClaudeCodeService.shared.isAssistantChat(route.key))
    return if (isAssistant) "$text (this chat)" else text
}

private val ROUTE_PACKAGE = Route::class.qualifiedName + "."

/**
 * Every Amethyst window, drawn by Skia into a picture, as its own layer has it: no OS permission
 * to record the screen, and nothing from other apps. Only the copy runs on the main thread, where
 * Skia draws; shrinking and encoding a window-sized picture would stall the UI there.
 */
private suspend fun captureWindows(): List<ImageInput> {
    val pictures =
        withContext(Dispatchers.Main) {
            Window
                .getWindows()
                .filter { it.isShowing }
                .sortedByDescending { it.isFocused }
                .mapNotNull { window -> skiaLayerOf(window) }
                .take(MAX_WINDOWS)
                .mapNotNull { layer ->
                    try {
                        // Null before the layer's first frame.
                        layer.screenshot()?.toBufferedImage()
                    } catch (e: RuntimeException) {
                        Log.w("AssistantScreenTools", "Could not capture the window", e)
                        null
                    }
                }
        }
    return withContext(Dispatchers.Default) {
        pictures.mapIndexedNotNull { index, picture ->
            try {
                ImageInput.fromImage("amethyst-window-${index + 1}.png", picture)
            } catch (e: IOException) {
                Log.w("AssistantScreenTools", "Could not shrink the window's picture", e)
                null
            }
        }
    }
}

private fun skiaLayerOf(container: Container): SkiaLayer? {
    if (container is SkiaLayer) return container
    for (child in container.components) {
        if (child is Container) skiaLayerOf(child)?.let { return it }
    }
    return null
}

private const val MAX_WINDOWS = 3
private const val MAX_BEHIND = 8
