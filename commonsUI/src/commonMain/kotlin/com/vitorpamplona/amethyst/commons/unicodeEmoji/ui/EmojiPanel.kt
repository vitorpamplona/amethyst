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
package com.vitorpamplona.amethyst.commons.unicodeEmoji.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.insets.KeyboardState
import com.vitorpamplona.amethyst.commons.ui.insets.keyboardAsState

/** Which composer the docked panel is writing into, and how. */
@Stable
internal class EmojiPanelRequest(
    val owner: Any,
    val emojiPacks: EmojiPackState?,
    val onPickUnicode: (String) -> Unit,
    val onPickCustom: (EmojiMedia) -> Unit,
)

/** The one docked emoji panel of a window; at most one composer has it open at a time. */
@Stable
class EmojiPanelState {
    internal var request by mutableStateOf<EmojiPanelRequest?>(null)

    fun close() {
        request = null
    }
}

internal val LocalEmojiPanel = staticCompositionLocalOf<EmojiPanelState?> { null }

/**
 * Docks the emoji picker at the bottom of [content], full width, where an on-screen keyboard
 * would be, and lets the composers' emoji buttons open it.
 *
 * It is the fallback for when there is no on-screen keyboard to pick emoji from: Desktop, or a
 * phone with a hardware keyboard or with the keyboard put away. So it closes as soon as the
 * keyboard comes up (a tap in the text), and the buttons hide while the keyboard is up. Unlike a
 * menu it stays open between picks, so several emoji go in one after another. Back closes it.
 */
@Composable
fun EmojiPanelHost(content: @Composable () -> Unit) {
    val state = remember { EmojiPanelState() }
    val keyboard by keyboardAsState()

    LaunchedEffect(keyboard) {
        if (keyboard == KeyboardState.Opened) state.close()
    }
    CompositionLocalProvider(LocalEmojiPanel provides state) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }

            state.request?.let { request ->
                // Registered only while open, and after the screens, so it is the newest handler
                // and Back closes the panel before it leaves the screen.
                PlatformBackHandler { state.close() }

                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider()
                        EmojiPicker(
                            emojiPacks = request.emojiPacks,
                            onPickUnicode = request.onPickUnicode,
                            onPickCustom = request.onPickCustom,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** The composer's button for the docked panel: toggles it, and hides while the keyboard is up. */
@Composable
internal fun EmojiPanelButton(
    host: EmojiPanelState,
    emojiPacks: EmojiPackState?,
    onPickUnicode: (String) -> Unit,
    onPickCustom: (EmojiMedia) -> Unit,
    tint: Color,
    modifier: Modifier,
) {
    val owner = remember { Any() }
    val pickUnicode by rememberUpdatedState(onPickUnicode)
    val pickCustom by rememberUpdatedState(onPickCustom)
    val keyboard by keyboardAsState()

    // A composer that goes away takes its panel with it: as soon as its screen stops being the
    // one in front, since a screen being left stays composed while it animates out.
    DisposableEffect(host) {
        onDispose { if (host.request?.owner === owner) host.close() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (host.request?.owner === owner) host.close()
    }

    // The on-screen keyboard has an emoji key of its own.
    if (keyboard == KeyboardState.Opened) return

    val open = host.request?.owner === owner
    Box(modifier) {
        EmojiPickerIcon(if (open) MaterialTheme.colorScheme.primary else tint) {
            host.request =
                if (open) {
                    null
                } else {
                    EmojiPanelRequest(owner, emojiPacks, { pickUnicode(it) }, { pickCustom(it) })
                }
        }
    }
}
