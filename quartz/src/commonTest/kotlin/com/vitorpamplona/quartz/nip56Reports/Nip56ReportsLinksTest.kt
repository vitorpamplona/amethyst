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
package com.vitorpamplona.quartz.nip56Reports

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip56ReportsLinksTest {
    private val person = "b1".repeat(32)
    private val note = "e1".repeat(32)
    private val blob = "a1".repeat(32)

    private fun report(tags: TagArray) = ReportEvent("0".repeat(64), "f".repeat(64), 1L, tags, "", "0".repeat(128))

    private fun props(
        report: String,
        raw: String,
    ) = mapOf("report" to report, "report_raw" to raw)

    @Test
    fun aReportThatNamesNoContentIsAboutThePerson() {
        assertEquals(
            listOf(Link(Relation.REPORTED_USER, LinkTarget.User(person), "p", props("impersonation", "impersonation"))),
            report(arrayOf(arrayOf("p", person, "impersonation"))).links(),
        )
    }

    @Test
    fun anInventedTypeFoldsIntoOtherButKeepsItsText() {
        assertEquals(
            listOf(Link(Relation.REPORTED_USER, LinkTarget.User(person), "p", props("other", "swearing"))),
            report(arrayOf(arrayOf("p", person, "wss://relay.example/", " Swearing "))).links(),
        )
    }

    @Test
    fun aReportAboutContentNamesItsAuthorEvenWhenThePWritesItsOwnType() {
        // Quartz's own build() writes the type on both the e and the p: the p is still the
        // reported content's author, not a complaint about the person.
        val event =
            report(
                arrayOf(
                    arrayOf("e", note, "wss://relay.example/", "Spam 📣"),
                    arrayOf("p", person, "nudity"),
                    arrayOf("a", "30023:$person:post"),
                    arrayOf("x", blob, "malware"),
                    arrayOf("server", "https://blossom.example/"),
                    arrayOf("L", "social.nos.ontology"),
                    arrayOf("l", "NS-spam", "social.nos.ontology"),
                ),
            )

        assertEquals(
            listOf(
                Link(Relation.REPORTED, LinkTarget.Event(note), "e", props("spam", "spam 📣")),
                Link(Relation.REPORTED_AUTHOR, LinkTarget.User(person), "p", props("nudity", "nudity")),
                // no type of its own: the report's default (the first one written)
                Link(Relation.REPORTED, LinkTarget.Address("30023:$person:post"), "a", props("spam", "spam 📣")),
                Link(Relation.REPORTED, LinkTarget.Tag("x", blob), "x", props("malware", "malware")),
                Link(Relation.TAG, LinkTarget.Tag("L", "social.nos.ontology"), "L"),
                Link(Relation.TAG, LinkTarget.Tag("l", "NS-spam"), "l"),
            ),
            event.links(),
        )
    }

    @Test
    fun theLegacyReportTagIsTheDefaultType() {
        val event =
            report(
                arrayOf(
                    arrayOf("report", "Illegal"),
                    arrayOf("e", note),
                    arrayOf("p", person),
                ),
            )

        assertEquals(
            listOf(
                Link(Relation.REPORTED, LinkTarget.Event(note), "e", props("illegal", "illegal")),
                Link(Relation.REPORTED_AUTHOR, LinkTarget.User(person), "p", props("illegal", "illegal")),
            ),
            event.links(),
        )
    }
}
