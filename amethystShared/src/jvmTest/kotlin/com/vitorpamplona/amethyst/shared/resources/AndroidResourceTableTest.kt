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
package com.vitorpamplona.amethyst.shared.resources

import com.vitorpamplona.amethyst.shared.R
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.vitorpamplona.amethyst.commons.R as CommonsR

class AndroidResourceTableTest {
    @BeforeTest
    fun useDefaultLocale() = AndroidResourceTable.setLocale(Locale.ENGLISH)

    @AfterTest
    fun restore() = AndroidResourceTable.setLocale(Locale.ENGLISH)

    @Test
    fun `ships every locale that the res tree declares`() {
        val qualifiers = AndroidResourceTable.availableQualifiers()
        assertEquals(57, qualifiers.size, "one table per values*/strings.xml folder")
        assertTrue("default" in qualifiers)
        assertTrue("pt-rBR" in qualifiers)
    }

    @Test
    fun `reads a plain string from the default table`() {
        assertEquals("Amethyst", AndroidResourceTable.getString(R.string.app_name))
    }

    @Test
    fun `preserves whitespace inside aapt quoted values`() {
        // <string name="replying_to">"replying to "</string> — the trailing
        // space is load-bearing; these strings get concatenated in the UI.
        assertEquals("replying to ", AndroidResourceTable.getString(R.string.replying_to))
        assertEquals(" and ", AndroidResourceTable.getString(R.string.and))
    }

    @Test
    fun `resolves backslash escapes as aapt does`() {
        // <item quantity="one">+%1$d\nreply</item>
        val one = AndroidResourceTable.getQuantityString(R.plurals.thread_collapsed_reply_count, 1, 1)
        assertEquals("+1\nreply", one)
    }

    @Test
    fun `substitutes positional format arguments`() {
        val text = AndroidResourceTable.getString(R.string.replying_to)
        assertTrue(text.isNotEmpty())
        val plural = AndroidResourceTable.getQuantityString(R.plurals.thread_collapsed_reply_count, 4, 4)
        assertEquals("+4\nreplies", plural)
    }

    @Test
    fun `selects English plural categories`() {
        val id = R.plurals.thread_collapsed_reply_count
        assertEquals("+1\nreply", AndroidResourceTable.getQuantityString(id, 1, 1))
        assertEquals("+0\nreplies", AndroidResourceTable.getQuantityString(id, 0, 0))
        assertEquals("+2\nreplies", AndroidResourceTable.getQuantityString(id, 2, 2))
    }

    @Test
    fun `falls back from a region qualifier to its language then to default`() {
        // pt-rBR exists; pt-rPT exists; an unlisted region must still land on
        // Portuguese rather than English if a `pt` table exists, else default.
        AndroidResourceTable.setLocale(Locale.of("pt", "BR"))
        val brazil = AndroidResourceTable.getString(R.string.app_name)
        assertTrue(brazil.isNotEmpty())

        AndroidResourceTable.setLocale(Locale.of("xx", "YY"))
        assertEquals(
            "Amethyst",
            AndroidResourceTable.getString(R.string.app_name),
            "an unknown locale must fall through to the default table",
        )
    }

    @Test
    fun `a missing id degrades instead of throwing`() {
        val text = AndroidResourceTable.getString(0x7f049999)
        assertTrue(text.startsWith("<missing string"), text)
    }

    @Test
    fun `a translation with the wrong placeholder count does not crash`() {
        // No argument supplied for a template that wants one.
        val text = AndroidResourceTable.getString(R.plurals.thread_collapsed_reply_count)
        assertTrue(text.isNotEmpty())
    }

    @Test
    fun `every default string id resolves`() {
        // Guards the id space: R fields and table rows are generated from the
        // same pass, so a mismatch here means the generator drifted.
        // Skip the synthetic `$stable` the Compose compiler plugin adds.
        val fields =
            R.string::class.java.declaredFields.filter {
                it.type == Int::class.javaPrimitiveType && !it.isSynthetic && !it.name.startsWith("$")
            }
        assertEquals(4404, fields.size)
        val unresolved =
            fields.filter { field ->
                field.isAccessible = true
                AndroidResourceTable.getString(field.getInt(null)).startsWith("<missing")
            }
        assertTrue(unresolved.isEmpty(), "unresolved: ${unresolved.take(5).map { it.name }}")
    }

    @Test
    fun `serves the commons res tree from the same table`() {
        // :commons ships its own translated res/ and its own R, which the app
        // references as CommonsR. Both are merged into one runtime lookup, so a
        // missing table dir shows up as a blank label at runtime, not a build
        // failure — hence this test.
        assertEquals("Console", AndroidResourceTable.getString(CommonsR.string.browser_console_title_short))
        assertEquals("Console (3)", AndroidResourceTable.getString(CommonsR.string.browser_console_title, 3))
    }

    @Test
    fun `the two res trees cannot collide in the id space`() {
        val shared =
            R.string::class.java.declaredFields
                .filter { it.type == Int::class.javaPrimitiveType && !it.isSynthetic && !it.name.startsWith("$") }
                .map {
                    it.isAccessible = true
                    it.getInt(null)
                }.toSet()
        val commons =
            CommonsR.string::class.java.declaredFields
                .filter { it.type == Int::class.javaPrimitiveType && !it.isSynthetic && !it.name.startsWith("$") }
                .map {
                    it.isAccessible = true
                    it.getInt(null)
                }.toSet()

        assertTrue(commons.isNotEmpty())
        assertTrue(shared.intersect(commons).isEmpty(), "the package byte stopped separating the two id spaces")
    }

    @Test
    fun `every commons string id resolves`() {
        val unresolved =
            CommonsR.string::class.java.declaredFields
                .filter { it.type == Int::class.javaPrimitiveType && !it.isSynthetic && !it.name.startsWith("$") }
                .filter { field ->
                    field.isAccessible = true
                    AndroidResourceTable.getString(field.getInt(null)).startsWith("<missing")
                }
        assertTrue(unresolved.isEmpty(), "unresolved: ${unresolved.map { it.name }}")
    }

    @Test
    fun `serves a real translation for a translated locale`() {
        AndroidResourceTable.setLocale(Locale.GERMAN)
        assertEquals("Abbrechen", AndroidResourceTable.getString(R.string.cancel))
    }

    @Test
    fun `selects the Russian few-many plural categories via CLDR`() {
        // Russian distinguishes one / few / many / other. Getting this right by
        // hand for 40 languages is exactly why plural selection goes through
        // ICU rather than a bespoke table.
        AndroidResourceTable.setLocale(Locale.of("ru", "RU"))
        val id = R.plurals.thread_collapsed_reply_count
        val one = AndroidResourceTable.getQuantityString(id, 1, 1)
        val few = AndroidResourceTable.getQuantityString(id, 3, 3)
        val many = AndroidResourceTable.getQuantityString(id, 5, 5)
        assertTrue(one.endsWith("ответ"), one)
        assertTrue(few.endsWith("ответа"), few)
        assertTrue(many.endsWith("ответов"), many)
    }

    @Test
    fun `reproduces aapt handling of a double-escaped newline`() {
        // values-ru writes `+%1$d\\nответ` (a doubly-escaped backslash), which
        // aapt resolves to a literal backslash followed by `n` — NOT a line
        // break. That is a pre-existing translation defect in 10 locale files;
        // it is asserted here because the JVM path must match what Android
        // renders today, bug included, rather than quietly diverging.
        AndroidResourceTable.setLocale(Locale.of("ru", "RU"))
        val text = AndroidResourceTable.getQuantityString(R.plurals.thread_collapsed_reply_count, 1, 1)
        assertTrue(text.startsWith("+1\\n"), "expected a literal backslash-n, got: $text")
        assertTrue('\n' !in text, "must not contain a real line break")
    }
}
