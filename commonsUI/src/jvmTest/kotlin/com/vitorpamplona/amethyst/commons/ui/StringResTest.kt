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
package com.vitorpamplona.amethyst.commons.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_search_body
import com.vitorpamplona.amethyst.commons.resources.chats_history_subtitle
import com.vitorpamplona.amethyst.commons.resources.geocache_difficulty_short
import com.vitorpamplona.amethyst.commons.resources.show_more
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.ResourceReader
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** stringRes caches and formats by itself; what it returns must stay what compose-resources returns. */
@OptIn(ExperimentalResourceApi::class)
class StringResTest {
    @Test
    fun stringResMatchesTheLibraryPlainAndFormatted() {
        val mine = mutableListOf<String>()
        val library = mutableListOf<String>()
        val scene =
            ImageComposeScene(10, 10, Density(1f)) {
                // Through the caching reader, as AmethystApp provides it, and twice, so the second
                // read comes from the cache.
                CompositionLocalProvider(LocalResourceReader provides CachingStringsReader.over(LocalResourceReader.current)) {
                    repeat(2) {
                        mine += stringRes(Res.string.show_more)
                        mine += stringRes(Res.string.chats_history_subtitle, "Alice", null, 42)
                        mine += stringRes(Res.string.chats_history_gate_search_body, "May 3", "Bob")
                        mine += stringRes(Res.string.geocache_difficulty_short, 3)
                    }
                }
                library += stringResource(Res.string.show_more)
                library += stringResource(Res.string.chats_history_subtitle, "Alice", "", 42)
                library += stringResource(Res.string.chats_history_gate_search_body, "May 3", "Bob")
                library += stringResource(Res.string.geocache_difficulty_short, 3)
            }
        try {
            scene.render()
        } finally {
            scene.close()
        }
        assertEquals(library + library, mine)
        assertEquals("Alice ·  · loaded since 42", mine[1])
    }

    @Test
    fun theCachingReaderSlicesWhatTheDefaultReaderReads() {
        lateinit var default: ResourceReader
        val scene = ImageComposeScene(10, 10, Density(1f)) { default = LocalResourceReader.current }
        scene.render()
        scene.close()

        val path = "composeResources/com.vitorpamplona.amethyst.commons.resources/values/strings.commonMain.cvr"
        val cached = CachingStringsReader.over(default)
        runBlocking {
            val size = default.read(path).size.toLong()
            for ((offset, length) in listOf(0L to 10L, 1234L to 57L, size / 2 to 100L, size - 30 to 30L)) {
                assertContentEquals(default.readPart(path, offset, length), cached.readPart(path, offset, length), "range $offset+$length")
            }
        }
    }
}
