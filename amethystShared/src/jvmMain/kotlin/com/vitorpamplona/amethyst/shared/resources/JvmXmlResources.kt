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

import java.io.InputStream

/**
 * The JVM counterpart to `Resources.getXml(R.xml.x)`.
 *
 * Files under `res/xml/` are copied next to the string and drawable tables by
 * the resource generators, one index per res tree, and looked up by the same
 * aapt2-style id the generated `R` hands out.
 *
 * This matters more than it looks: `locales_config.xml` is the list of
 * languages the app ships, and the settings screen builds its picker by parsing
 * it. A lookup that came back empty would leave the desktop with no languages
 * to choose from and nothing in the log to say why.
 */
object JvmXmlResources {
    private val TABLE_DIRS = listOf("amethyst-app-res", "amethyst-res", "amethyst-commons-res")

    private val index: Map<Int, String> by lazy { loadIndex() }

    /** The raw document, or null when no res tree declares that id. */
    fun open(id: Int): InputStream? {
        val path = index[id] ?: return null
        return JvmXmlResources::class.java.classLoader?.getResourceAsStream(path)
    }

    fun contains(id: Int): Boolean = index.containsKey(id)

    private fun loadIndex(): Map<Int, String> {
        val loader = JvmXmlResources::class.java.classLoader ?: return emptyMap()
        val entries = LinkedHashMap<Int, String>()
        TABLE_DIRS.forEach { dir ->
            val text =
                loader
                    .getResourceAsStream("$dir/xmls.tsv")
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: return@forEach
            text.lineSequence().forEach { line ->
                if (line.isEmpty()) return@forEach
                val parts = line.split('\t')
                if (parts.size < 2) return@forEach
                val id = parts[0].toIntOrNull() ?: return@forEach
                entries[id] = "$dir/xml/${parts[1]}"
            }
        }
        return entries
    }
}
