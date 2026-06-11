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
package com.vitorpamplona.quartz.nip89AppHandlers.recommendation

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.tags.RecommendationTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppRecommendationEventTest {
    val appAddress =
        Address(
            AppDefinitionEvent.KIND,
            "1743058db7078661b94aaf4286429d97ee5257d14a86d6bfa54cb0482b876fb0",
            "abcd",
        )
    val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    @Test
    fun roundTripPreservesRelayAndPlatform() {
        val original = RecommendationTag(appAddress, relay, "android")
        val parsed = RecommendationTag.parse(original.toTagArray())

        assertNotNull(parsed)
        assertEquals(appAddress, parsed.address)
        assertEquals(relay, parsed.relay)
        assertEquals("android", parsed.platform)
    }

    @Test
    fun platformSurvivesNullRelayHint() {
        // assemble keeps a placeholder for the null relay, so the platform
        // stays in its own slot instead of shifting into the relay position.
        val tagArray = RecommendationTag(appAddress, null, "android").toTagArray()
        val parsed = RecommendationTag.parse(tagArray)

        assertNotNull(parsed)
        assertNull(parsed.relay)
        assertEquals("android", parsed.platform)
    }

    @Test
    fun buildFromTagsKeepsExistingRecommendations() {
        val otherApp =
            Address(
                AppDefinitionEvent.KIND,
                "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                "efgh",
            )
        val existing = RecommendationTag(appAddress, relay, "web")
        val added = RecommendationTag(otherApp, relay, "android")

        val template = AppRecommendationEvent.buildFromTags("31337", listOf(existing, added))

        assertEquals(AppRecommendationEvent.KIND, template.kind)
        assertEquals(listOf("d", "31337"), template.tags.first { it[0] == "d" }.toList())

        val recommendations = template.tags.mapNotNull(RecommendationTag::parse)
        assertEquals(2, recommendations.size)
        assertEquals(appAddress, recommendations[0].address)
        assertEquals("web", recommendations[0].platform)
        assertEquals(otherApp, recommendations[1].address)
        assertEquals("android", recommendations[1].platform)
    }
}
