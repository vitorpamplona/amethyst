package com.vitorpamplona.amethyst

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.amethyst.model.*
import com.vitorpamplona.amethyst.ui.actions.buildAnnotatedStringWithUrlHighlighting
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
        val user = LocalCache.getOrCreateUser(decodePublicKey("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z").toHexKey())
        (user as User).info = UserMetadata()
        user.info?.displayName = "Vitor Pamplona"

        val transformedText = buildAnnotatedStringWithUrlHighlighting(
            AnnotatedString("New Hey @npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"),
            Color.Red
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
        val user = LocalCache.getOrCreateUser(decodePublicKey("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z").toHexKey())
        (user as User).info = UserMetadata()
        user.info?.displayName = "Vitor Pamplona"

        val transformedText = buildAnnotatedStringWithUrlHighlighting(
            AnnotatedString("New Hey @npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z and @npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"),
            Color.Red
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
