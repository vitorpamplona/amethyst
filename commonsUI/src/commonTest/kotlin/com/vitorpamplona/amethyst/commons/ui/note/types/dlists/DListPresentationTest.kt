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
package com.vitorpamplona.amethyst.commons.ui.note.types.dlists

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Headers and items as served by wss://search.brainstorm.world (2026-10). */
class DListPresentationTest {
    private val foodHeader =
        arrayOf(
            arrayOf("d", "food-and-drink-places"),
            arrayOf("names", "Food and Drink Place", "Food and Drink Places"),
            arrayOf("description", "Restaurants, cafes, bars, bakeries and food shops, anywhere in the world."),
            arrayOf("required", "name", "Name of the business"),
            arrayOf("required", "category", "Kind of place"),
            arrayOf("recommended", "address", "Street address, one line"),
            arrayOf("recommended", "cuisine", "Primary cuisine"),
            arrayOf("recommended", "g", "Geohash of the location"),
            arrayOf("optional", "website", "Website"),
            arrayOf("optional", "phone", "Phone number, international format"),
            arrayOf("optional", "opening-hours", "Opening hours, in OpenStreetMap syntax"),
            arrayOf("field-type", "website", "url"),
            arrayOf("display", "title", "name"),
            arrayOf("display", "summary", "address"),
            arrayOf("display", "image", "image"),
            arrayOf("display", "link", "website"),
        )

    private val choconat =
        arrayOf(
            arrayOf("d", "osm-node-10600960420"),
            arrayOf("z", "39998:b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450:food-and-drink-places"),
            arrayOf("name", "Choconat"),
            arrayOf("category", "pastry"),
            arrayOf("address", "R1-L7 Guará 71020-218"),
            arrayOf("website", "http://instagram.com/choconat.confeitariadf"),
            arrayOf("phone", "+55 61995218884"),
            arrayOf("opening-hours", "Di-Sa 8:00-18:00"),
            arrayOf("btcmap-id", "2819"),
            arrayOf("g", "6vjtx"),
            arrayOf("t", "pastry"),
            arrayOf("alt", "Food and drink place: Choconat"),
        )

    @Test
    fun headerNamesAndFields() {
        val header = dListHeaderView(foodHeader, "food-and-drink-places")

        assertEquals("Food and Drink Place", header.singular)
        assertEquals("Food and Drink Places", header.plural)
        assertEquals(listOf("name", "category", "address", "cuisine", "g", "website", "phone", "opening-hours"), header.fields.map { it.name })
        // `image` is not a declared field here, so its display hint is dropped.
        assertNull(header.hint("image"))
        assertEquals("website", header.hint("link"))
        assertNull(header.image)
    }

    @Test
    fun headerWithoutNamesFallsBackToItsDTag() {
        val withdrawn = arrayOf(arrayOf("d", "github-accounts"), arrayOf("b", "b-tag-deferred"))

        val header = dListHeaderView(withdrawn, "github-accounts")

        assertEquals("github-accounts", header.singular)
        assertEquals("github-accounts", header.plural)
        assertTrue(header.unaffiliated)
        assertTrue(header.fields.isEmpty())
    }

    @Test
    fun itemReadsThroughTheHeadersHints() {
        val item = dListItemView(choconat, dListHeaderView(foodHeader, null))

        assertEquals("Choconat", item.title)
        assertEquals("R1-L7 Guará 71020-218", item.summary)
        assertEquals("http://instagram.com/choconat.confeitariadf", item.link)
        // Declared fields first (title, summary and link are not repeated), then undeclared tags;
        // protocol tags and alt are never facts.
        assertEquals(listOf("category", "phone", "opening-hours", "btcmap-id"), item.facts.map { it.field })
        assertTrue(item.missing.isEmpty())
    }

    @Test
    fun repeatedFieldsCountTheirExtraValues() {
        val item =
            choconat +
                arrayOf(
                    arrayOf("phone", "+55 61 0000-0000"),
                    arrayOf("phone", " "),
                    arrayOf("btcmap-id", "2820"),
                    arrayOf("btcmap-id", "2821"),
                )

        val facts = dListItemView(item, dListHeaderView(foodHeader, null)).facts.associateBy { it.field }

        // Blank values are not values; declared and undeclared fields both count their repeats.
        assertEquals("+55 61995218884", facts.getValue("phone").value)
        assertEquals(1, facts.getValue("phone").extra)
        assertEquals("2819", facts.getValue("btcmap-id").value)
        assertEquals(2, facts.getValue("btcmap-id").extra)
    }

    @Test
    fun missingRequiredFieldsAreReported() {
        val item = dListItemView(arrayOf(arrayOf("name", "El Sapo")), dListHeaderView(foodHeader, null))

        assertEquals(listOf("category"), item.missing)
    }

    @Test
    fun withoutHintsTheFirstRequiredFieldIsTheTitle() {
        val github =
            arrayOf(
                arrayOf("names", "GitHub Account", "GitHub Accounts"),
                arrayOf("required", "github-username"),
                arrayOf("optional", "description"),
            )
        val item = arrayOf(arrayOf("github-username", "beep-boop"), arrayOf("description", "Some evil bot"))

        val view = dListItemView(item, dListHeaderView(github, null))

        assertEquals("beep-boop", view.title)
        assertEquals("Some evil bot", view.summary)
        assertTrue(view.facts.isEmpty())
    }

    @Test
    fun withoutAHeaderTheItemsOwnTagsSpeak() {
        val duty =
            arrayOf(
                arrayOf("d", "duty-menu"),
                arrayOf("z", "39998:15f7dafc4624b1e6b00ab7f863de1a53b71967528070ec7d1837c7a40c1c7270:duty-menu"),
                arrayOf("duty", "30382", "wss://tapestry.brainstorm.world/relay"),
                arrayOf("duty", "30392", "wss://tapestry.brainstorm.world/relay"),
            )

        val view = dListItemView(duty, null)

        assertNull(view.title)
        assertEquals(1, view.facts.size)
        assertEquals("duty", view.facts.single().field)
        assertEquals(1, view.facts.single().extra)
    }

    @Test
    fun aTagElementIsNamedByItsContent() {
        val podcaster =
            arrayOf(
                arrayOf("d", "podcaster"),
                arrayOf("z", "39998:82b75e474dda005e912bcbb910391c60c2b89cc7faf5d3c30b7c59a324973833:tag"),
            )
        val content = "{\"tag\":{\"slug\":\"podcaster\",\"name\":\"Podcaster\",\"description\":\"Someone who hosts podcasts.\"}}"
        val tagHeader = dListHeaderView(arrayOf(arrayOf("d", "tag"), arrayOf("names", "tag", "tags")), "tag")

        val view = dListItemView(podcaster, content, tagHeader)

        assertEquals("Podcaster", view.title)
        assertEquals("Someone who hosts podcasts.", view.summary)
        // Content that is not a tag element changes nothing.
        assertNull(dListItemView(podcaster, "not json", tagHeader).title)
    }

    @Test
    fun wordsAndUrls() {
        assertEquals("Opening hours", fieldWords("opening-hours"))
        assertEquals("https://btcmap.org", httpUrl(" https://btcmap.org "))
        assertNull(httpUrl("btcmap.org"))
        assertNull(httpUrl("https://"))
    }
}
