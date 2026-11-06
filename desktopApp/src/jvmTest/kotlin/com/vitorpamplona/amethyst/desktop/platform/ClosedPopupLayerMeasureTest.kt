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
package com.vitorpamplona.amethyst.desktop.platform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compose Multiplatform measures a popup layer that the main content's layout just closed, and
 * throws (see [isClosedLayerMeasured]). This rebuilds that crash headlessly, on the same scene class
 * the desktop window uses, and checks that the window's handler recognises it and that the scene
 * keeps working afterwards, which is why ignoring it is safe.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ClosedPopupLayerMeasureTest {
    private val width = mutableIntStateOf(200)
    private var clicks = 0

    // The popup lives inside BoxWithConstraints, which composes its content while it is measured:
    // shrinking the box closes the popup in the middle of the scene's layout pass.
    private val scene =
        ImageComposeScene(300, 300) {
            Box(Modifier.fillMaxSize().clickable { clicks++ })
            Box(
                Modifier.layout { measurable, _ ->
                    val size = width.intValue
                    val placeable = measurable.measure(Constraints.fixed(size, size))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
            ) {
                BoxWithConstraints {
                    if (maxWidth > 100.dp) Popup { Box(Modifier.size(10.dp)) }
                }
            }
        }

    private fun press(at: Offset) = scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true))

    private fun release(at: Offset) = scene.sendPointerEvent(PointerEventType.Release, at, buttons = PointerButtons())

    private fun closePopupDuringNextLayout(): Throwable? {
        scene.render(0)
        width.intValue = 50
        Snapshot.sendApplyNotifications()
        return runCatching { press(Offset(250f, 250f)) }.exceptionOrNull()
    }

    @Test
    fun handlerRecognisesTheCrashComposeThrows() {
        val thrown = closePopupDuringNextLayout()
        assertNotNull(
            thrown,
            "Compose no longer measures a closed popup layer: drop isClosedLayerMeasured and this test.",
        )
        assertTrue(isClosedLayerMeasured(thrown), thrown.stackTraceToString())
        scene.close()
    }

    @Test
    fun sceneKeepsWorkingAfterTheCrash() {
        assertNotNull(closePopupDuringNextLayout())

        // Only the event that hit the bug is lost: the next frame and the next click go through.
        scene.render(16_000_000)
        val spot = Offset(250f, 250f)
        press(spot)
        release(spot)
        assertEquals(1, clicks)
        scene.close()
    }

    @Test
    fun otherDisposedOwnerErrorsStillCrash() {
        assertFalse(isClosedLayerMeasured(IllegalArgumentException("RootNodeOwner is already disposed")))
        assertFalse(isClosedLayerMeasured(IllegalStateException("ComposeScene is already closed")))
        scene.close()
    }
}
