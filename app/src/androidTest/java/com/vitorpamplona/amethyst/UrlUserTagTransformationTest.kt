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
package com.vitorpamplona.amethyst

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.actions.buildAnnotatedStringWithUrlHighlighting
import com.vitorpamplona.quartz.encoders.decodePublicKey
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.UserMetadata
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
    fun transformationText() {
        val user =
            LocalCache.getOrCreateUser(
                decodePublicKey("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z")
                    .toHexKey(),
            )
        user.info = UserMetadata()
        user.info?.displayName = "Vitor Pamplona"

        val transformedText =
            buildAnnotatedStringWithUrlHighlighting(
                AnnotatedString("New Hey @npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"),
                Color.Red,
            )

        assertEquals("New Hey @Vitor Pamplona", transformedText.text.text)

        assertEquals(0, transformedText.offsetMapping.originalToTransformed(0)) // Before N
        assertEquals(4, transformedText.offsetMapping.originalToTransformed(4)) // Before H
        assertEquals(8, transformedText.offsetMapping.originalToTransformed(8)) // Before @
        assertEquals(8, transformedText.offsetMapping.originalToTransformed(9)) // Before n
        assertEquals(8, transformedText.offsetMapping.originalToTransformed(10)) // Before p
        assertEquals(9, transformedText.offsetMapping.originalToTransformed(11)) // Before u
        assertEquals(9, transformedText.offsetMapping.originalToTransformed(12)) // Before b
        assertEquals(9, transformedText.offsetMapping.originalToTransformed(13)) // Before 1

        assertEquals(23, transformedText.offsetMapping.originalToTransformed(71))
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(72))

        assertEquals(0, transformedText.offsetMapping.transformedToOriginal(0))
        assertEquals(4, transformedText.offsetMapping.transformedToOriginal(4))
        assertEquals(8, transformedText.offsetMapping.transformedToOriginal(8))
        assertEquals(12, transformedText.offsetMapping.transformedToOriginal(9))

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
        user.info = UserMetadata()
        user.info?.displayName = "Vitor Pamplona"

        val transformedText =
            buildAnnotatedStringWithUrlHighlighting(
                AnnotatedString(
                    "New Hey @npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z and @npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z",
                ),
                Color.Red,
            )

        assertEquals("New Hey @Vitor Pamplona and @Vitor Pamplona", transformedText.text.text)

        assertEquals(9, transformedText.offsetMapping.originalToTransformed(11))
        assertEquals(9, transformedText.offsetMapping.originalToTransformed(12))
        assertEquals(9, transformedText.offsetMapping.originalToTransformed(13))

        assertEquals(23, transformedText.offsetMapping.originalToTransformed(70)) // Before 5
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(71)) // Before z
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(72)) // Before <space>
        assertEquals(24, transformedText.offsetMapping.originalToTransformed(73)) // Before a
        assertEquals(25, transformedText.offsetMapping.originalToTransformed(74)) // Before n
        assertEquals(26, transformedText.offsetMapping.originalToTransformed(75)) // Before d
        assertEquals(27, transformedText.offsetMapping.originalToTransformed(76)) // Before <space>
        assertEquals(28, transformedText.offsetMapping.originalToTransformed(77)) // Before @
        assertEquals(28, transformedText.offsetMapping.originalToTransformed(78)) // Before n

        assertEquals(68, transformedText.offsetMapping.transformedToOriginal(22)) // Before a
        assertEquals(72, transformedText.offsetMapping.transformedToOriginal(23)) // Before <space>
        assertEquals(73, transformedText.offsetMapping.transformedToOriginal(24)) // Before a
        assertEquals(74, transformedText.offsetMapping.transformedToOriginal(25)) // Before n
        assertEquals(75, transformedText.offsetMapping.transformedToOriginal(26)) // Before d
        assertEquals(76, transformedText.offsetMapping.transformedToOriginal(27)) // Before <space>
        assertEquals(77, transformedText.offsetMapping.transformedToOriginal(28)) // Before @
    }
}
