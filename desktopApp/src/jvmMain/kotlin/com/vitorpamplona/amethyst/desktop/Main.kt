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
package com.vitorpamplona.amethyst.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

enum class Screen {
    Feed,
    Search,
    Messages,
    Notifications,
    Profile,
    Settings
}

fun main() = application {
    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 800.dp,
        position = WindowPosition.Aligned(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Amethyst"
    ) {
        MenuBar {
            Menu("File") {
                Item(
                    "New Note",
                    shortcut = KeyShortcut(Key.N, ctrl = true),
                    onClick = { /* TODO: Open new note dialog */ }
                )
                Separator()
                Item(
                    "Settings",
                    shortcut = KeyShortcut(Key.Comma, ctrl = true),
                    onClick = { /* TODO: Open settings */ }
                )
                Separator()
                Item(
                    "Quit",
                    shortcut = KeyShortcut(Key.Q, ctrl = true),
                    onClick = ::exitApplication
                )
            }
            Menu("Edit") {
                Item("Copy", shortcut = KeyShortcut(Key.C, ctrl = true), onClick = { })
                Item("Paste", shortcut = KeyShortcut(Key.V, ctrl = true), onClick = { })
            }
            Menu("View") {
                Item("Feed", onClick = { })
                Item("Messages", onClick = { })
                Item("Notifications", onClick = { })
            }
            Menu("Help") {
                Item("About Amethyst", onClick = { })
                Item("Keyboard Shortcuts", onClick = { })
            }
        }

        App()
    }
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.Feed) }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(Modifier.fillMaxSize()) {
                // Sidebar Navigation
                NavigationRail(
                    modifier = Modifier.width(80.dp).fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Spacer(Modifier.height(16.dp))

                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Feed") },
                        label = { Text("Feed") },
                        selected = currentScreen == Screen.Feed,
                        onClick = { currentScreen = Screen.Feed }
                    )

                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                        selected = currentScreen == Screen.Search,
                        onClick = { currentScreen = Screen.Search }
                    )

                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Email, contentDescription = "Messages") },
                        label = { Text("DMs") },
                        selected = currentScreen == Screen.Messages,
                        onClick = { currentScreen = Screen.Messages }
                    )

                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Notifications, contentDescription = "Notifications") },
                        label = { Text("Alerts") },
                        selected = currentScreen == Screen.Notifications,
                        onClick = { currentScreen = Screen.Notifications }
                    )

                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        selected = currentScreen == Screen.Profile,
                        onClick = { currentScreen = Screen.Profile }
                    )

                    Spacer(Modifier.weight(1f))

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    NavigationRailItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentScreen == Screen.Settings,
                        onClick = { currentScreen = Screen.Settings }
                    )

                    Spacer(Modifier.height(16.dp))
                }

                VerticalDivider()

                // Main Content
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)
                ) {
                    when (currentScreen) {
                        Screen.Feed -> FeedPlaceholder()
                        Screen.Search -> SearchPlaceholder()
                        Screen.Messages -> MessagesPlaceholder()
                        Screen.Notifications -> NotificationsPlaceholder()
                        Screen.Profile -> ProfilePlaceholder()
                        Screen.Settings -> SettingsPlaceholder()
                    }
                }
            }
        }
    }
}

@Composable
fun FeedPlaceholder() {
    Column {
        Text(
            "Feed",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Your Nostr feed will appear here.\n\nConnect to relays and follow users to see notes.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SearchPlaceholder() {
    Column {
        Text(
            "Search",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Search for users, notes, and hashtags.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MessagesPlaceholder() {
    Column {
        Text(
            "Messages",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Your encrypted direct messages will appear here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun NotificationsPlaceholder() {
    Column {
        Text(
            "Notifications",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Mentions, replies, and reactions will appear here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ProfilePlaceholder() {
    Column {
        Text(
            "Profile",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Login with your Nostr keys to see your profile.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsPlaceholder() {
    Column {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Configure relays, appearance, and account settings.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
