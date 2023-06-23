package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.service.firstFullChar
import org.junit.Assert
import org.junit.Test

class CharsetTest {
    @Test
    fun testASCIIChar() {
        Assert.assertEquals("H", "Hi".firstFullChar())
    }

    @Test
    fun testUTF16Char() {
        Assert.assertEquals("\uD83C\uDF48", "\uD83C\uDF48Hi".firstFullChar())
    }

    @Test
    fun testUTF32Char() {
        Assert.assertEquals("\uD83E\uDDD1\uD83C\uDFFE", "\uD83E\uDDD1\uD83C\uDFFEHi".firstFullChar())
    }

    @Test
    fun testAsciiWithUTF32Char() {
        Assert.assertEquals("H", "Hi\uD83E\uDDD1\uD83C\uDFFEHi".firstFullChar())
    }

    @Test
    fun testBlank() {
        Assert.assertEquals("", "".firstFullChar())
    }

    @Test
    fun testSpecialChars() {
        Assert.assertEquals("=", "=x".firstFullChar())
    }
}
