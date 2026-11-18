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
package com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.navigation.host.ComposerCard
import com.vitorpamplona.amethyst.commons.ui.navigation.host.ComposerSize
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composersOpenAsDialogs

/**
 * The window the media Studio and the picture crop open in. Where composers open as dialogs (a
 * wide window driven by a mouse) it is the composers' resizable card, [ComposerSize.MEDIA], over
 * the screen that opened it; on a touch screen it fills the screen. Esc or back calls
 * [onDismissRequest] unless [canDismiss] is false (while it saves or uploads); a click outside
 * does nothing, so a stray click never drops the edits.
 */
@Composable
internal fun StudioDialog(
    onDismissRequest: () -> Unit,
    canDismiss: Boolean,
    content: @Composable () -> Unit,
) {
    if (composersOpenAsDialogs()) {
        Dialog(
            onDismissRequest = { if (canDismiss) onDismissRequest() },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = canDismiss, dismissOnClickOutside = false),
        ) {
            ComposerCard(ComposerSize.MEDIA, popOut = null, content)
        }
    } else {
        Dialog(
            onDismissRequest = { if (canDismiss) onDismissRequest() },
            properties = edgeToEdgeDialogProperties(dismissOnBackPress = canDismiss, dismissOnClickOutside = false),
        ) {
            content()
        }
    }
}
