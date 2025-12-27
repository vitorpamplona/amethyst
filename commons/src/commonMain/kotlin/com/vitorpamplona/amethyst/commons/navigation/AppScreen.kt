/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.commons.navigation

/**
 * Main application screens shared between Desktop and Android.
 * Each platform implements its own navigation using these identifiers.
 */
enum class AppScreen(
    val label: String,
    val route: String,
) {
    Feed("Feed", "feed"),
    Search("Search", "search"),
    Messages("Messages", "messages"),
    Notifications("Notifications", "notifications"),
    Profile("Profile", "profile"),
    Settings("Settings", "settings"),
}

/**
 * Primary navigation destinations (shown in bottom bar on mobile, sidebar on desktop).
 */
val primaryScreens = listOf(
    AppScreen.Feed,
    AppScreen.Search,
    AppScreen.Messages,
    AppScreen.Notifications,
    AppScreen.Profile,
)

/**
 * Secondary navigation destinations (settings, etc.)
 */
val secondaryScreens = listOf(
    AppScreen.Settings,
)
