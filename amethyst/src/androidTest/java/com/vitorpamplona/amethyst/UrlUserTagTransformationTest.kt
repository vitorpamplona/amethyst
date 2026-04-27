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
package com.vitorpamplona.amethyst

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.actions.buildAnnotatedStringWithUrlHighlighting
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class UrlUserTagTransformationTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.vitorpamplona.amethyst", appContext.packageName.removeSuffix(".debug"))
    }

    @Test
    fun testKeepTransformedIndexFullyInsideTransformedText() {
        val user =
            LocalCache.getOrCreateUser(
                decodePublicKey("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z")
                    .toHexKey(),
            )
        user.metadata().newMetadata(
            UserMetadata().also {
                it.displayName = "Vitor Pamplona"
            },
            MetadataEvent(
                id = "",
                pubKey = "",
                createdAt = 0,
                tags = emptyArray(),
                content = "",
                sig = "",
            ),
        )

        val original = "@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"

        val transformedText =
            buildAnnotatedStringWithUrlHighlighting(
                AnnotatedString(original),
                Color.Red,
            )

        val expected = "@Vitor Pamplona"
        assertEquals(expected, transformedText.text.text)

        // The mention is treated as an atomic wedge: any cursor strictly inside the
        // underlying npub snaps to the trailing edge of the displayed "@Vitor Pamplona"
        // (and vice versa). This prevents an IME from placing the cursor in the middle
        // of the bech32 and corrupting it on backspace.
        assertEquals(0, transformedText.offsetMapping.originalToTransformed(0))
        for (i in 1..63) {
            assertEquals("originalToTransformed($i)", 15, transformedText.offsetMapping.originalToTransformed(i))
        }
        assertEquals(15, transformedText.offsetMapping.originalToTransformed(64))

        assertEquals(0, transformedText.offsetMapping.transformedToOriginal(0))
        for (i in 1..14) {
            assertEquals("transformedToOriginal($i)", 64, transformedText.offsetMapping.transformedToOriginal(i))
        }
        assertEquals(64, transformedText.offsetMapping.transformedToOriginal(15))
    }

    @Test
    fun transformationText() {
        val user =
            LocalCache.getOrCreateUser(
                decodePublicKey("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z")
                    .toHexKey(),
            )
        user.metadata().newMetadata(
            UserMetadata().also {
                it.displayName = "Vitor Pamplona"
            },
            MetadataEvent(
                id = "",
                pubKey = "",
                createdAt = 0,
                tags = emptyArray(),
                content = "",
                sig = "",
            ),
        )

        val transformedText =
            buildAnnotatedStringWithUrlHighlighting(
                AnnotatedString("New Hey @npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"),
                Color.Red,
            )

        assertEquals("New Hey @Vitor Pamplona", transformedText.text.text)

        // Outside the wedge: identity mapping.
        assertEquals(0, transformedText.offsetMapping.originalToTransformed(0)) // Before N
        assertEquals(4, transformedText.offsetMapping.originalToTransformed(4)) // Before H
        assertEquals(8, transformedText.offsetMapping.originalToTransformed(8)) // Before @ (boundary)

        // Strictly inside the underlying npub: snaps to the end of "@Vitor Pamplona" (offset 23).
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(9)) // Before n
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(12)) // Before b
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(13)) // Before 1
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(71)) // Before z

        // End-of-wedge boundary maps to end of displayed mention.
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(72))

        // Outside the wedge in displayed: identity.
        assertEquals(0, transformedText.offsetMapping.transformedToOriginal(0))
        assertEquals(4, transformedText.offsetMapping.transformedToOriginal(4))
        assertEquals(8, transformedText.offsetMapping.transformedToOriginal(8)) // Before @ (boundary)

        // Strictly inside displayed "@Vitor Pamplona": snaps to end of underlying npub (offset 72).
        assertEquals(72, transformedText.offsetMapping.transformedToOriginal(9))
        assertEquals(72, transformedText.offsetMapping.transformedToOriginal(22))

        // End-of-wedge boundary maps to end of underlying mention; past it shifts by deltas.
        assertEquals(72, transformedText.offsetMapping.transformedToOriginal(23))
        assertEquals(73, transformedText.offsetMapping.transformedToOriginal(24))
    }

    @Test
    fun transformationTextTwoKeys() {
        val user =
            LocalCache.getOrCreateUser(
                decodePublicKey("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z")
                    .toHexKey(),
            )

        user.metadata().newMetadata(
            UserMetadata().also {
                it.displayName = "Vitor Pamplona"
            },
            MetadataEvent(
                id = "",
                pubKey = "",
                createdAt = 0,
                tags = emptyArray(),
                content = "",
                sig = "",
            ),
        )

        val transformedText =
            buildAnnotatedStringWithUrlHighlighting(
                AnnotatedString(
                    "New Hey @npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z and @npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z",
                ),
                Color.Red,
            )

        assertEquals("New Hey @Vitor Pamplona and @Vitor Pamplona", transformedText.text.text)

        // Strictly inside the first underlying npub [8, 72): snap to end of first
        // displayed "@Vitor Pamplona" (offset 23).
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(11))
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(12))
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(13))
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(70))
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(71))

        // Boundary at end of first wedge: end of first displayed mention.
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(72)) // Before <space>
        assertEquals(24, transformedText.offsetMapping.originalToTransformed(73)) // Before a
        assertEquals(25, transformedText.offsetMapping.originalToTransformed(74)) // Before n
        assertEquals(26, transformedText.offsetMapping.originalToTransformed(75)) // Before d
        assertEquals(27, transformedText.offsetMapping.originalToTransformed(76)) // Before <space>
        assertEquals(28, transformedText.offsetMapping.originalToTransformed(77)) // Before @ (boundary, second wedge)

        // Strictly inside the second underlying npub [77, 141): snap to end of second
        // displayed "@Vitor Pamplona" (offset 43).
        assertEquals(43, transformedText.offsetMapping.originalToTransformed(78)) // Before n
        assertEquals(43, transformedText.offsetMapping.originalToTransformed(140))

        // Strictly inside first displayed "@Vitor Pamplona" [8, 23): snap to end of
        // first underlying npub (offset 72).
        assertEquals(72, transformedText.offsetMapping.transformedToOriginal(22)) // Before a (display)
        assertEquals(72, transformedText.offsetMapping.transformedToOriginal(23)) // Before <space>
        assertEquals(73, transformedText.offsetMapping.transformedToOriginal(24)) // Before a
        assertEquals(74, transformedText.offsetMapping.transformedToOriginal(25)) // Before n
        assertEquals(75, transformedText.offsetMapping.transformedToOriginal(26)) // Before d
        assertEquals(76, transformedText.offsetMapping.transformedToOriginal(27)) // Before <space>
        assertEquals(77, transformedText.offsetMapping.transformedToOriginal(28)) // Before @ (boundary, second wedge)

        // Strictly inside second displayed "@Vitor Pamplona" [28, 43): snap to end of
        // second underlying npub (offset 141).
        assertEquals(141, transformedText.offsetMapping.transformedToOriginal(29))
        assertEquals(141, transformedText.offsetMapping.transformedToOriginal(42))
    }
}
