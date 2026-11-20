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
package com.vitorpamplona.amethyst.commons.i18n

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_discovery_accepted_kinds
import com.vitorpamplona.amethyst.commons.resources.relay_members_count
import com.vitorpamplona.amethyst.commons.resources.settings
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which catalog folder a locale reads. Compose resources match on `Locale.getLanguage()`, so these
 * pin the cases where that differs from the folder name or where a region falls back to its language.
 */
class LocaleResolutionTest {
    private fun <T> withLocale(
        languageTag: String,
        block: suspend () -> T,
    ): T {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag))
            return runBlocking { block() }
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun resolve(
        languageTag: String,
        res: StringResource,
    ): String = withLocale(languageTag) { getString(res) }

    /**
     * Java 17+ (desktop) and Android 15+ for apps targeting it report Indonesian as `id`; older
     * Android reports `in`. The translation lives in `values-in`, and the build adds a `values-id`
     * copy so both resolve (commonsUI/build.gradle.kts, `aliasLegacyLanguageFolders`).
     */
    @Test
    fun indonesianResolvesUnderTheNewIsoCode() {
        assertEquals("id", Locale.forLanguageTag("id-ID").language)
        assertEquals("Pengaturan", resolve("id-ID", Res.string.settings))
        assertEquals("Pengaturan", resolve("id", Res.string.settings))
    }

    /** A regional folder holds only its differences; everything else comes from the language's base folder. */
    @Test
    fun regionsFallBackToTheirLanguage() {
        assertEquals("Kinds aceptados", resolve("es-MX", Res.string.relay_discovery_accepted_kinds))
        assertEquals("Tipos aceptados", resolve("es-ES", Res.string.relay_discovery_accepted_kinds))
        assertEquals("Kinds aceptados", resolve("es-AR", Res.string.relay_discovery_accepted_kinds))
        assertEquals("設置", resolve("zh-HK", Res.string.settings))
        assertEquals("设置", resolve("zh-SG", Res.string.settings))
    }

    /** Counted nouns are `<plurals>`, so Czech gets its one/few/other forms instead of an English-shaped string. */
    @Test
    fun countsUseTheLanguagesPluralForms() {
        withLocale("cs") {
            assertEquals("1 člen", getPluralString(Res.plurals.relay_members_count, 1, 1))
            assertEquals("3 členové", getPluralString(Res.plurals.relay_members_count, 3, 3))
            assertEquals("5 členů", getPluralString(Res.plurals.relay_members_count, 5, 5))
        }
        withLocale("en") {
            assertEquals("1 member", getPluralString(Res.plurals.relay_members_count, 1, 1))
            assertEquals("2 members", getPluralString(Res.plurals.relay_members_count, 2, 2))
        }
    }
}
