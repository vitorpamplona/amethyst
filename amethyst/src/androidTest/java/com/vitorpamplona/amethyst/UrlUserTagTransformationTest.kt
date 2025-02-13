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
import androidx.compose.ui.text.input.TransformedText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.actions.buildAnnotatedStringWithUrlHighlighting
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.toHexKey
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

    fun debugCursor(
        original: String,
        transformedText: TransformedText,
        offset: Int,
    ): String {
        val offsetTransformed = transformedText.offsetMapping.originalToTransformed(offset)
        val originalWithCursor = original.substring(0, offset) + "|" + original.substring(offset, original.length)
        val transformedWithCursor = transformedText.text.text.substring(0, offsetTransformed) + "|" + transformedText.text.text.substring(offsetTransformed, transformedText.text.text.length)
        return "$originalWithCursor $transformedWithCursor"
    }

    fun debugCursorReverse(
        original: String,
        transformedText: TransformedText,
        offsetTransformed: Int,
    ): String {
        val offset = transformedText.offsetMapping.transformedToOriginal(offsetTransformed)
        val originalWithCursor = original.substring(0, offset) + "|" + original.substring(offset, original.length)
        val transformedWithCursor = transformedText.text.text.substring(0, offsetTransformed) + "|" + transformedText.text.text.substring(offsetTransformed, transformedText.text.text.length)
        return "$originalWithCursor $transformedWithCursor"
    }

    @Test
    fun testKeepTransformedIndexFullyInsideTransformedText() {
        val user =
            LocalCache.getOrCreateUser(
                decodePublicKey("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z")
                    .toHexKey(),
            )
        user.info = UserMetadata()
        user.info?.displayName = "Vitor Pamplona"

        val original = "@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"

        val transformedText =
            buildAnnotatedStringWithUrlHighlighting(
                AnnotatedString(original),
                Color.Red,
            )

        val expected = "@Vitor Pamplona"
        assertEquals(expected, transformedText.text.text)

        assertEquals("|@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z |@Vitor Pamplona", debugCursor(original, transformedText, 0))
        assertEquals("@|npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z |@Vitor Pamplona", debugCursor(original, transformedText, 1))
        assertEquals("@n|pub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z |@Vitor Pamplona", debugCursor(original, transformedText, 2))
        assertEquals("@np|ub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z |@Vitor Pamplona", debugCursor(original, transformedText, 3))
        assertEquals("@npu|b1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z |@Vitor Pamplona", debugCursor(original, transformedText, 4))
        assertEquals("@npub|1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @|Vitor Pamplona", debugCursor(original, transformedText, 5))
        assertEquals("@npub1|gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @|Vitor Pamplona", debugCursor(original, transformedText, 6))
        assertEquals("@npub1g|cxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @|Vitor Pamplona", debugCursor(original, transformedText, 7))
        assertEquals("@npub1gc|xzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @|Vitor Pamplona", debugCursor(original, transformedText, 8))
        assertEquals("@npub1gcx|zte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @V|itor Pamplona", debugCursor(original, transformedText, 9))
        assertEquals("@npub1gcxz|te5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @V|itor Pamplona", debugCursor(original, transformedText, 10))
        assertEquals("@npub1gcxzt|e5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @V|itor Pamplona", debugCursor(original, transformedText, 11))
        assertEquals("@npub1gcxzte|5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @V|itor Pamplona", debugCursor(original, transformedText, 12))
        assertEquals("@npub1gcxzte5|zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vi|tor Pamplona", debugCursor(original, transformedText, 13))
        assertEquals("@npub1gcxzte5z|lkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vi|tor Pamplona", debugCursor(original, transformedText, 14))
        assertEquals("@npub1gcxzte5zl|kncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vi|tor Pamplona", debugCursor(original, transformedText, 15))
        assertEquals("@npub1gcxzte5zlk|ncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vi|tor Pamplona", debugCursor(original, transformedText, 16))
        assertEquals("@npub1gcxzte5zlkn|cx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vi|tor Pamplona", debugCursor(original, transformedText, 17))
        assertEquals("@npub1gcxzte5zlknc|x26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vit|or Pamplona", debugCursor(original, transformedText, 18))
        assertEquals("@npub1gcxzte5zlkncx|26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vit|or Pamplona", debugCursor(original, transformedText, 19))
        assertEquals("@npub1gcxzte5zlkncx2|6j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vit|or Pamplona", debugCursor(original, transformedText, 20))
        assertEquals("@npub1gcxzte5zlkncx26|j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vit|or Pamplona", debugCursor(original, transformedText, 21))
        assertEquals("@npub1gcxzte5zlkncx26j|68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vito|r Pamplona", debugCursor(original, transformedText, 22))
        assertEquals("@npub1gcxzte5zlkncx26j6|8ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vito|r Pamplona", debugCursor(original, transformedText, 23))
        assertEquals("@npub1gcxzte5zlkncx26j68|ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vito|r Pamplona", debugCursor(original, transformedText, 24))
        assertEquals("@npub1gcxzte5zlkncx26j68e|z60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vito|r Pamplona", debugCursor(original, transformedText, 25))
        assertEquals("@npub1gcxzte5zlkncx26j68ez|60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor| Pamplona", debugCursor(original, transformedText, 26))
        assertEquals("@npub1gcxzte5zlkncx26j68ez6|0fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor| Pamplona", debugCursor(original, transformedText, 27))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60|fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor| Pamplona", debugCursor(original, transformedText, 28))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60f|zkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor| Pamplona", debugCursor(original, transformedText, 29))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fz|kvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor |Pamplona", debugCursor(original, transformedText, 30))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzk|vtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor |Pamplona", debugCursor(original, transformedText, 31))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkv|tkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor |Pamplona", debugCursor(original, transformedText, 32))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvt|km9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor |Pamplona", debugCursor(original, transformedText, 33))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtk|m9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor |Pamplona", debugCursor(original, transformedText, 34))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm|9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor P|amplona", debugCursor(original, transformedText, 35))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9|e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor P|amplona", debugCursor(original, transformedText, 36))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e|0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor P|amplona", debugCursor(original, transformedText, 37))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0|vrwdcvsjakxf9mu9qewqlfnj5z @Vitor P|amplona", debugCursor(original, transformedText, 38))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0v|rwdcvsjakxf9mu9qewqlfnj5z @Vitor Pa|mplona", debugCursor(original, transformedText, 39))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vr|wdcvsjakxf9mu9qewqlfnj5z @Vitor Pa|mplona", debugCursor(original, transformedText, 40))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrw|dcvsjakxf9mu9qewqlfnj5z @Vitor Pa|mplona", debugCursor(original, transformedText, 41))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwd|cvsjakxf9mu9qewqlfnj5z @Vitor Pa|mplona", debugCursor(original, transformedText, 42))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdc|vsjakxf9mu9qewqlfnj5z @Vitor Pam|plona", debugCursor(original, transformedText, 43))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcv|sjakxf9mu9qewqlfnj5z @Vitor Pam|plona", debugCursor(original, transformedText, 44))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvs|jakxf9mu9qewqlfnj5z @Vitor Pam|plona", debugCursor(original, transformedText, 45))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsj|akxf9mu9qewqlfnj5z @Vitor Pam|plona", debugCursor(original, transformedText, 46))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsja|kxf9mu9qewqlfnj5z @Vitor Pamp|lona", debugCursor(original, transformedText, 47))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjak|xf9mu9qewqlfnj5z @Vitor Pamp|lona", debugCursor(original, transformedText, 48))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakx|f9mu9qewqlfnj5z @Vitor Pamp|lona", debugCursor(original, transformedText, 49))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf|9mu9qewqlfnj5z @Vitor Pamp|lona", debugCursor(original, transformedText, 50))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9|mu9qewqlfnj5z @Vitor Pamp|lona", debugCursor(original, transformedText, 51))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9m|u9qewqlfnj5z @Vitor Pampl|ona", debugCursor(original, transformedText, 52))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu|9qewqlfnj5z @Vitor Pampl|ona", debugCursor(original, transformedText, 53))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9|qewqlfnj5z @Vitor Pampl|ona", debugCursor(original, transformedText, 54))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9q|ewqlfnj5z @Vitor Pampl|ona", debugCursor(original, transformedText, 55))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qe|wqlfnj5z @Vitor Pamplo|na", debugCursor(original, transformedText, 56))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qew|qlfnj5z @Vitor Pamplo|na", debugCursor(original, transformedText, 57))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewq|lfnj5z @Vitor Pamplo|na", debugCursor(original, transformedText, 58))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewql|fnj5z @Vitor Pamplo|na", debugCursor(original, transformedText, 59))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlf|nj5z @Vitor Pamplon|a", debugCursor(original, transformedText, 60))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfn|j5z @Vitor Pamplon|a", debugCursor(original, transformedText, 61))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj|5z @Vitor Pamplon|a", debugCursor(original, transformedText, 62))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5|z @Vitor Pamplon|a", debugCursor(original, transformedText, 63))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z| @Vitor Pamplona|", debugCursor(original, transformedText, 64))

        assertEquals("|@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z |@Vitor Pamplona", debugCursorReverse(original, transformedText, 0))
        assertEquals("@npu|b1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @|Vitor Pamplona", debugCursorReverse(original, transformedText, 1))
        assertEquals("@npub1gc|xzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @V|itor Pamplona", debugCursorReverse(original, transformedText, 2))
        assertEquals("@npub1gcxzte|5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vi|tor Pamplona", debugCursorReverse(original, transformedText, 3))
        assertEquals("@npub1gcxzte5zlkn|cx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vit|or Pamplona", debugCursorReverse(original, transformedText, 4))
        assertEquals("@npub1gcxzte5zlkncx26|j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vito|r Pamplona", debugCursorReverse(original, transformedText, 5))
        assertEquals("@npub1gcxzte5zlkncx26j68e|z60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor| Pamplona", debugCursorReverse(original, transformedText, 6))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60f|zkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor |Pamplona", debugCursorReverse(original, transformedText, 7))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtk|m9e0vrwdcvsjakxf9mu9qewqlfnj5z @Vitor P|amplona", debugCursorReverse(original, transformedText, 8))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0|vrwdcvsjakxf9mu9qewqlfnj5z @Vitor Pa|mplona", debugCursorReverse(original, transformedText, 9))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwd|cvsjakxf9mu9qewqlfnj5z @Vitor Pam|plona", debugCursorReverse(original, transformedText, 10))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsj|akxf9mu9qewqlfnj5z @Vitor Pamp|lona", debugCursorReverse(original, transformedText, 11))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9|mu9qewqlfnj5z @Vitor Pampl|ona", debugCursorReverse(original, transformedText, 12))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9q|ewqlfnj5z @Vitor Pamplo|na", debugCursorReverse(original, transformedText, 13))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewql|fnj5z @Vitor Pamplon|a", debugCursorReverse(original, transformedText, 14))
        assertEquals("@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z| @Vitor Pamplona|", debugCursorReverse(original, transformedText, 15))

        assertEquals(0, transformedText.offsetMapping.originalToTransformed(0))
        assertEquals(15, transformedText.offsetMapping.originalToTransformed(64))
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
        assertEquals(8, transformedText.offsetMapping.originalToTransformed(11)) // Before u
        assertEquals(8, transformedText.offsetMapping.originalToTransformed(12)) // Before b
        assertEquals(9, transformedText.offsetMapping.originalToTransformed(13)) // Before 1

        assertEquals(22, transformedText.offsetMapping.originalToTransformed(71))
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

        assertEquals(8, transformedText.offsetMapping.originalToTransformed(11))
        assertEquals(8, transformedText.offsetMapping.originalToTransformed(12))
        assertEquals(9, transformedText.offsetMapping.originalToTransformed(13))

        assertEquals(22, transformedText.offsetMapping.originalToTransformed(70)) // Before 5
        assertEquals(22, transformedText.offsetMapping.originalToTransformed(71)) // Before z
        assertEquals(23, transformedText.offsetMapping.originalToTransformed(72)) // Before <space>
        assertEquals(24, transformedText.offsetMapping.originalToTransformed(73)) // Before a
        assertEquals(25, transformedText.offsetMapping.originalToTransformed(74)) // Before n
        assertEquals(26, transformedText.offsetMapping.originalToTransformed(75)) // Before d
        assertEquals(27, transformedText.offsetMapping.originalToTransformed(76)) // Before <space>
        assertEquals(28, transformedText.offsetMapping.originalToTransformed(77)) // Before @
        assertEquals(28, transformedText.offsetMapping.originalToTransformed(78)) // Before n

        assertEquals(67, transformedText.offsetMapping.transformedToOriginal(22)) // Before a
        assertEquals(72, transformedText.offsetMapping.transformedToOriginal(23)) // Before <space>
        assertEquals(73, transformedText.offsetMapping.transformedToOriginal(24)) // Before a
        assertEquals(74, transformedText.offsetMapping.transformedToOriginal(25)) // Before n
        assertEquals(75, transformedText.offsetMapping.transformedToOriginal(26)) // Before d
        assertEquals(76, transformedText.offsetMapping.transformedToOriginal(27)) // Before <space>
        assertEquals(77, transformedText.offsetMapping.transformedToOriginal(28)) // Before @
    }
}
