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
package com.vitorpamplona.amethyst.commons.androiddevices.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** The coding chat in front, as the device panel needs it. */
@Immutable
class ChatFocus(
    val chatKey: String,
    /** Whose device and emulator copies: the chat's workstream, else the chat. */
    val owner: String,
    val ownerLabel: String?,
    /** Where Run finds the app. */
    val folder: File,
    val agentName: String,
    /** Puts a screenshot into the chat's next message. */
    val attach: (File) -> Unit,
)

/**
 * Which coding chat the dock's device panel follows: the one a chat screen published last
 * ([publish]). Several can be shown (a deck column, a pinned chat, another window): when the latest
 * leaves, the one before it is followed again. When the last one leaves (a tab switch to the
 * feed), the panel keeps following it, so its device stays connected and live until another chat
 * comes to the front or [forget] drops it. With none yet, the panel is the Android devices screen's
 * (any device, AVDs started as themselves).
 */
object DeviceChatFocus {
    private val shown = mutableListOf<ChatFocus>()

    var current by mutableStateOf<ChatFocus?>(null)
        private set

    /** [focus] is in front; call what it returns when its screen leaves. */
    fun publish(focus: ChatFocus): () -> Unit {
        synchronized(shown) {
            shown.remove(focus)
            shown.add(focus)
            current = focus
        }
        return {
            synchronized(shown) {
                shown.remove(focus)
                // The panel stays on the chat that left unless another is still shown.
                shown.lastOrNull()?.let { current = it }
            }
        }
    }

    /** The chat [chatKey] is gone (deleted): the panel stops following it. */
    fun forget(chatKey: String) {
        synchronized(shown) {
            shown.removeAll { it.chatKey == chatKey }
            if (current?.chatKey == chatKey) current = shown.lastOrNull()
        }
    }
}
