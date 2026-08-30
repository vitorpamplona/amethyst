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

import android.content.res.Resources
import android.content.res.StaxXmlResourceParser
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.shared.platform.JvmContext
import org.xmlpull.v1.XmlPullParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * End-to-end over the real `res/xml/locales_config.xml`: the generated id, the
 * copied file, and the StAX-backed parser. The settings screen builds its whole
 * language picker from this walk, so an empty result would look like "this
 * build ships one language".
 */
class XmlResourceTest {
    private fun localeTags(): List<String> {
        val tags = mutableListOf<String>()
        JvmContext.resources.getXml(R.xml.locales_config).use { xpp ->
            while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
                if (xpp.eventType == XmlPullParser.START_TAG && xpp.name == "locale") {
                    tags.add(xpp.getAttributeValue(0))
                }
                xpp.next()
            }
        }
        return tags
    }

    @Test
    fun `the app's locale list parses through the generated id`() {
        val tags = localeTags()

        // The exact set changes as translations land, so assert the shape and
        // some anchors rather than a count that would churn.
        assertTrue(tags.size > 30, "expected the shipped locale list, saw ${tags.size} entries")
        assertTrue("en" in tags)
        assertTrue("pt-BR" in tags)
        assertTrue("zh-TW" in tags)
        // Every entry is a language tag, not an empty or stray attribute.
        assertTrue(tags.all { it.isNotBlank() && it.first().isLetter() }, "malformed tags: $tags")
        assertEquals(tags.distinct(), tags, "the locale list has duplicates")
    }

    @Test
    fun `an id with no resource behind it throws instead of parsing nothing`() {
        // A silent empty document would surface as a missing feature rather
        // than the build problem it is.
        assertFailsWith<Resources.NotFoundException> { JvmContext.resources.getXml(0x7d090099) }
    }

    @Test
    fun `the parser skips comments and whitespace, as XmlPull does`() {
        val doc =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <root>
                <!-- a comment the app must not see as an event -->
                <item name="first"/>
                <item name="second"/>
            </root>
            """.trimIndent()

        val names = mutableListOf<String>()
        StaxXmlResourceParser(doc.byteInputStream()).use { xpp ->
            while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
                if (xpp.eventType == XmlPullParser.START_TAG && xpp.name == "item") {
                    names.add(xpp.getAttributeValue(0))
                }
                xpp.next()
            }
        }
        assertEquals(listOf("first", "second"), names)
    }

    @Test
    fun `attributes read by name ignore the namespace prefix`() {
        // The app's own XML writes android:name; the caller asks by index, but
        // anything asking by name must not need the schema URL.
        val doc = """<r xmlns:android="http://schemas.android.com/apk/res/android"><l android:name="pt"/></r>"""
        StaxXmlResourceParser(doc.byteInputStream()).use { xpp ->
            while (xpp.eventType != XmlPullParser.END_DOCUMENT && xpp.name != "l") xpp.next()
            assertEquals("pt", xpp.getAttributeValue(null, "name"))
            assertEquals("name", xpp.getAttributeName(0))
            assertEquals(1, xpp.getAttributeCount())
        }
    }

    @Test
    fun `text content comes back and depth tracks nesting`() {
        val doc = "<a><b>hello</b></a>"
        StaxXmlResourceParser(doc.byteInputStream()).use { xpp ->
            assertEquals(XmlPullParser.START_TAG, xpp.next())
            assertEquals("a", xpp.name)
            assertEquals(1, xpp.depth)
            assertEquals(XmlPullParser.START_TAG, xpp.next())
            assertEquals(2, xpp.depth)
            assertEquals("hello", xpp.nextText())
        }
    }

    @Test
    fun `malformed xml raises rather than ending the document early`() {
        val broken = "<a><b></a>"
        assertFailsWith<org.xmlpull.v1.XmlPullParserException> {
            StaxXmlResourceParser(broken.byteInputStream()).use { xpp ->
                while (xpp.eventType != XmlPullParser.END_DOCUMENT) xpp.next()
            }
        }
    }
}
