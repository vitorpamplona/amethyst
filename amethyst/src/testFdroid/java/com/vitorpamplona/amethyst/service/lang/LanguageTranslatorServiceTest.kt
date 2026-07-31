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
package com.vitorpamplona.amethyst.service.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageTranslatorServiceTest {
    @Test
    fun `maps common BCP-47 tags to LibreTranslate codes`() {
        assertEquals("en", LanguageTranslatorService.toLibreTranslateCode("en"))
        assertEquals("en", LanguageTranslatorService.toLibreTranslateCode("en-US"))
        assertEquals("zh", LanguageTranslatorService.toLibreTranslateCode("zh-CN"))
        assertEquals("zh", LanguageTranslatorService.toLibreTranslateCode("zh-TW"))
        assertEquals("pt", LanguageTranslatorService.toLibreTranslateCode("pt-BR"))
        assertEquals("de", LanguageTranslatorService.toLibreTranslateCode("de"))
        assertEquals("no", LanguageTranslatorService.toLibreTranslateCode("no"))
    }

    @Test
    fun `returns null for languages LibreTranslate does not support`() {
        assertNull(LanguageTranslatorService.toLibreTranslateCode("tl"))
        assertNull(LanguageTranslatorService.toLibreTranslateCode("xx"))
        assertNull(LanguageTranslatorService.toLibreTranslateCode(""))
    }
}
