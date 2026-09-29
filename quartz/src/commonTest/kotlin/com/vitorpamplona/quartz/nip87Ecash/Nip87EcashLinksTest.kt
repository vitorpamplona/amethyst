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
package com.vitorpamplona.quartz.nip87Ecash

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.props.PlatformProps
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip87EcashLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)

    @Test
    fun recommendationNamesTheMintAnnouncementAndItsPlatform() {
        val mint = "38172:$author:mint"
        val federation = "38173:$author:federation"
        val event =
            MintRecommendationEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("d", "mint"),
                    arrayOf("k", "38172"),
                    arrayOf("u", "https://mint.example"),
                    arrayOf("a", mint, "wss://relay.example/"),
                    arrayOf("a", federation),
                    arrayOf("a", "30023:$author:post"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.RECOMMENDED, LinkTarget.Address(mint), "a", PlatformProps("cashu")),
                Link(Relation.RECOMMENDED, LinkTarget.Address(federation), "a", PlatformProps("fedimint")),
                Link(Relation.TAG, LinkTarget.Tag("k", "38172"), "k"),
            ),
            event.links(),
        )
    }
}
