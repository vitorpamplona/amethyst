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
package com.vitorpamplona.quartz.buzz.mpProjects

import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectMember
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val owner = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    private fun project(template: com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<ProjectEvent>) = ProjectEvent(id, author, template.createdAt, template.tags, template.content, sig)

    @Test
    fun memberRepositoriesAreLinkedAndHintedWhereARelayIsGiven() {
        val event =
            project(
                ProjectEvent.build(
                    slug = "amethyst",
                    name = "Amethyst",
                    description = "Nostr client for Android",
                    members =
                        listOf(
                            ProjectMember(Address(30617, owner, "amethyst"), relay),
                            ProjectMember(Address(30617, owner, "quartz")),
                        ),
                ),
            )

        assertEquals(listOf("30617:$owner:amethyst", "30617:$owner:quartz"), event.linkedAddressIds())
        assertEquals(listOf("30617:$owner:amethyst"), event.addressHints().map { it.addressId })
        assertEquals(listOf(relay), event.addressHints().map { it.relay.url })
    }

    @Test
    fun projectIsSearchableByNameAndDescription() {
        val event = project(ProjectEvent.build(slug = "amethyst", name = "Amethyst", description = "Nostr client for Android"))
        assertEquals("Amethyst\nNostr client for Android", event.indexableContent())

        val fields = SearchFieldExtractor.extract(event) as IndexableFields.Tiered
        assertEquals(listOf("Amethyst"), fields.primary)
        assertEquals(listOf("Nostr client for Android"), fields.secondary)

        // No name: the slug is what clients display, so it is what search finds.
        assertEquals("quartz-lib", project(ProjectEvent.build(slug = "quartz-lib")).indexableContent())
    }
}
