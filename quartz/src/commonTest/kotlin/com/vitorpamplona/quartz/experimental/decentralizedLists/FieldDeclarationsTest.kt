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
package com.vitorpamplona.quartz.experimental.decentralizedLists

import com.vitorpamplona.quartz.experimental.decentralizedLists.header.FieldRequirement
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.displayHints
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.fieldDeclarations
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.listImage
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.optionalTags
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tagRules
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.DisplayHint
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TagRuleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Field declarations and the provisional display hints, read from headers served by
 * wss://search.brainstorm.world (2026-10), against the rules of Brainstorm's `lib/dlistFields.ts`
 * and `lib/displayHints.ts`.
 */
class FieldDeclarationsTest {
    /** Brainstorm's GitHub Accounts copy: `optional` rule, `field-type`, display hints, list image. */
    private val githubAccounts =
        arrayOf(
            arrayOf("names", "GitHub Account", "GitHub Accounts"),
            arrayOf("description", "A list of github handles/accounts"),
            arrayOf("required", "github-username"),
            arrayOf("field-type", "github-username", "text"),
            arrayOf("optional", "description"),
            arrayOf("display", "title", "description"),
            arrayOf("display", "summary", "github-username"),
            arrayOf("image", "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png"),
            arrayOf("d", "github-accounts"),
        )

    @Test
    fun optionalIsARuleLikeAllowed() {
        assertEquals(TagRuleType.OPTIONAL, githubAccounts.tagRules()[1].type)
        assertEquals(listOf("description"), githubAccounts.optionalTags())
    }

    @Test
    fun declarationsAreOrderedByLevelThenHeaderOrder() {
        val tags =
            arrayOf(
                arrayOf("optional", "website", "Website"),
                arrayOf("required", "name", "Name of the business"),
                arrayOf("recommended", "address", "Street address, one line"),
                arrayOf("allowed", "phone"),
                arrayOf("required", "category"),
                arrayOf("disallowed", "p"),
                // Declared twice: keeps its strongest level, at its first place.
                arrayOf("recommended", "website"),
                arrayOf("field-type", "website", "url"),
                arrayOf("field-type", "website", "text"),
            )

        val fields = tags.fieldDeclarations()

        assertEquals(listOf("name", "category", "website", "address", "phone"), fields.map { it.name })
        assertEquals(FieldRequirement.REQUIRED, fields[0].requirement)
        assertEquals("Name of the business", fields[0].description)
        assertEquals(FieldRequirement.RECOMMENDED, fields[2].requirement)
        assertTrue(fields[2].isUrl())
        assertEquals("text", fields[0].type)
        assertEquals(FieldRequirement.OPTIONAL, fields[4].requirement)
    }

    @Test
    fun displayHintsDecorateDeclaredFieldsOnly() {
        val declared = githubAccounts.fieldDeclarations().map { it.name }
        val tags =
            githubAccounts +
                arrayOf(
                    arrayOf("display", "title", "github-username"),
                    arrayOf("display", "image", "undeclared"),
                    arrayOf("display", "fact", "github-username", "Handle"),
                    arrayOf("display", "fact", "github-username", "Again"),
                    arrayOf("display", "fact", "description"),
                )

        val hints = tags.displayHints(declared)

        assertEquals(
            listOf(
                DisplayHint("title", "description"),
                DisplayHint("summary", "github-username"),
                DisplayHint("fact", "github-username", "Handle"),
                DisplayHint("fact", "description"),
            ),
            hints,
        )
    }

    @Test
    fun readsTheListImage() {
        assertEquals("https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png", githubAccounts.listImage())
        assertNull(arrayOf(arrayOf("optional", "image", "A photo")).listImage())
    }
}
