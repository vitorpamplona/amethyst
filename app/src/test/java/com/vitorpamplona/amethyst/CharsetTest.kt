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
    fun testUTF16JoinChar() {
        Assert.assertEquals("\uD83C\uDF48", "\uD83C\uDF48Hi".firstFullChar())
    }

    @Test
    fun testUTF32JoinChar() {
        Assert.assertEquals("\uD83E\uDDD1\uD83C\uDFFE", "\uD83E\uDDD1\uD83C\uDFFEHi".firstFullChar())
    }

    @Test
    fun testUTF32JoinChar2() {
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

    @Test
    fun test5CharEmoji() {
        Assert.assertEquals("\uD83D\uDC68\u200D\uD83D\uDCBB", "\uD83D\uDC68\u200D\uD83D\uDCBBadsfasdf".firstFullChar())
    }

    @Test
    fun testFamily() {
        Assert.assertEquals("\uD83D\uDC68\u200d\uD83D\uDC69\u200d\uD83D\uDC67\u200d\uD83D\uDC67", "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC67adsfasdf".firstFullChar())
    }

    @Test
    fun testTeacher() {
        Assert.assertEquals("\uD83E\uDDD1\uD83C\uDFFF\u200D\uD83C\uDFEB", "\uD83E\uDDD1\uD83C\uDFFF\u200D\uD83C\uDFEBasdf".firstFullChar())
    }

    @Test
    fun testVariation() {
        Assert.assertEquals("\uD83D\uDC68\u200D\u2764\uFE0F\u200D\uD83D\uDC8B\u200D\uD83D\uDC68", "\uD83D\uDC68\u200D\u2764\uFE0F\u200D\uD83D\uDC8B\u200D\uD83D\uDC68ddd".firstFullChar())
    }

    @Test
    fun testMultipleEmoji() {
        Assert.assertEquals("\uD83E\uDEC2\uD83E\uDEC2", "\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2\uD83E\uDEC2".firstFullChar())
    }
}
