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
package com.vitorpamplona.quartz.experimental.roadstr

import com.vitorpamplona.quartz.experimental.roadstr.confirmation.RoadEventConfirmationEvent
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.tags.RoadEventStatus
import com.vitorpamplona.quartz.experimental.roadstr.report.RoadEventReportEvent
import com.vitorpamplona.quartz.experimental.roadstr.report.tags.RoadEventType
import com.vitorpamplona.quartz.experimental.roadstr.tags.RoadCoordinate
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoadEventTest {
    private val reportId = "fcbfc866b657a2fe514b579bcf7ff7700a195731eb6f306efb01529a683f86db"

    private fun sampleReport(): Event =
        EventFactory.create(
            id = "a099d4db563041bb289d3704f983fc148fc805860303a4f479a8264dc6a2d7cc",
            pubKey = "932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d",
            createdAt = 1_700_000_000L,
            kind = RoadEventReportEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("t", "police"),
                    arrayOf("g", "u2ed"),
                    arrayOf("g", "u2edc"),
                    arrayOf("g", "u2edcg"),
                    arrayOf("lat", "48.8566140"),
                    arrayOf("lon", "2.3522219"),
                    arrayOf("expiration", "1701209600"),
                    arrayOf("alt", "Roadstr: police report"),
                ),
            content = "",
            sig = "00".repeat(64),
        )

    private fun sampleConfirmation(): Event =
        EventFactory.create(
            id = "b199d4db563041bb289d3704f983fc148fc805860303a4f479a8264dc6a2d7cc",
            pubKey = "932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d",
            createdAt = 1_700_003_600L,
            kind = RoadEventConfirmationEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("e", reportId),
                    arrayOf("status", "no_longer_there"),
                    arrayOf("expiration", "1701213200"),
                    arrayOf("alt", "Roadstr: event denied"),
                ),
            content = "",
            sig = "00".repeat(64),
        )

    @Test
    fun factoryParsesReportAndConfirmation() {
        assertIs<RoadEventReportEvent>(sampleReport())
        assertIs<RoadEventConfirmationEvent>(sampleConfirmation())
    }

    @Test
    fun kindsAreKnown() {
        assertTrue(EventFactory.isKnownKind(RoadEventReportEvent.KIND))
        assertTrue(EventFactory.isKnownKind(RoadEventConfirmationEvent.KIND))
    }

    @Test
    fun parsesReportFields() {
        val event = sampleReport()
        assertIs<RoadEventReportEvent>(event)

        assertEquals(RoadEventType.POLICE, event.roadEventType())
        assertEquals(listOf("u2ed", "u2edc", "u2edcg"), event.geohashes())
        assertEquals(48.8566140, event.latitude())
        assertEquals(2.3522219, event.longitude())
        assertEquals("Roadstr: police report", event.alt())
        assertEquals(1701209600L, event.relayExpiration())
        // police TTL is 2h → created_at + 7200
        assertEquals(1_700_000_000L + 7_200L, event.effectiveExpirationAt())
    }

    @Test
    fun unknownTypeYieldsNullType() {
        val event =
            EventFactory.create<Event>(
                id = "00".repeat(32),
                pubKey = "00".repeat(32),
                createdAt = 1_700_000_000L,
                kind = RoadEventReportEvent.KIND,
                tags = arrayOf(arrayOf("t", "spaceship")),
                content = "",
                sig = "00".repeat(64),
            )
        assertIs<RoadEventReportEvent>(event)
        assertEquals("spaceship", event.roadEventTypeCode())
        assertNull(event.roadEventType())
        assertNull(event.effectiveExpirationAt())
    }

    @Test
    fun parsesConfirmationFields() {
        val event = sampleConfirmation()
        assertIs<RoadEventConfirmationEvent>(event)

        assertEquals(reportId, event.reportId())
        assertEquals(RoadEventStatus.NO_LONGER_THERE, event.status())
        assertTrue(event.isDenial())
        assertTrue(!event.isConfirmation())
        assertEquals("Roadstr: event denied", event.alt())
    }

    @Test
    fun buildReportProducesCanonicalTags() {
        val lat = 48.8566140
        val lon = 2.3522219
        val createdAt = 1_700_000_000L
        val template = RoadEventReportEvent.build(RoadEventType.SPEED_CAMERA, lat, lon, createdAt = createdAt)

        assertEquals(RoadEventReportEvent.KIND, template.kind)

        val expectedG6 = GeoHash.encode(lat, lon, 6).toString()
        val geohashes = template.tags.filter { it[0] == "g" }.map { it[1] }
        assertEquals(
            listOf(expectedG6.substring(0, 4), expectedG6.substring(0, 5), expectedG6),
            geohashes,
        )

        assertEquals("speed_camera", template.tags.first { it[0] == "t" }[1])
        assertEquals("48.8566140", template.tags.first { it[0] == "lat" }[1])
        assertEquals("2.3522219", template.tags.first { it[0] == "lon" }[1])
        assertEquals((createdAt + RoadEventReportEvent.RELAY_TTL_SECONDS).toString(), template.tags.first { it[0] == "expiration" }[1])
        assertEquals("Roadstr: speed_camera report", template.tags.first { it[0] == "alt" }[1])
    }

    @Test
    fun buildConfirmationOmitsLocationWhenAbsent() {
        val template = RoadEventConfirmationEvent.build(reportId, RoadEventStatus.STILL_THERE, createdAt = 1_700_000_000L)

        assertEquals(reportId, template.tags.first { it[0] == "e" }[1])
        assertEquals("still_there", template.tags.first { it[0] == "status" }[1])
        assertEquals("Roadstr: event confirmed", template.tags.first { it[0] == "alt" }[1])
        assertTrue(template.tags.none { it[0] == "g" || it[0] == "lat" || it[0] == "lon" })
    }

    @Test
    fun buildConfirmationIncludesLocationWhenProvided() {
        val lat = 48.8566140
        val lon = 2.3522219
        val template = RoadEventConfirmationEvent.build(reportId, RoadEventStatus.STILL_THERE, lat, lon)

        val expectedG6 = GeoHash.encode(lat, lon, 6).toString()
        assertEquals(expectedG6, template.tags.filter { it[0] == "g" }.last()[1])
        assertEquals("48.8566140", template.tags.first { it[0] == "lat" }[1])
    }

    @Test
    fun roadEventTypeTtls() {
        assertEquals(7_200L, RoadEventType.POLICE.effectiveTtlSeconds)
        assertEquals(2_592_000L, RoadEventType.SPEED_CAMERA.effectiveTtlSeconds)
        assertEquals(3_600L, RoadEventType.TRAFFIC_JAM.effectiveTtlSeconds)
        assertEquals(604_800L, RoadEventType.POTHOLE.effectiveTtlSeconds)
        assertEquals(RoadEventType.TRAFFIC_JAM, RoadEventType.fromCode("traffic_jam"))
        assertNull(RoadEventType.fromCode("nope"))
    }

    @Test
    fun coordinateFormattingHasSevenDecimals() {
        assertEquals("48.8566140", RoadCoordinate.format(48.856614))
        assertEquals("2.3522219", RoadCoordinate.format(2.3522219))
        assertEquals("-0.0000010", RoadCoordinate.format(-0.000001))
        assertEquals("0.0000000", RoadCoordinate.format(0.0))
        assertEquals("-33.8688000", RoadCoordinate.format(-33.8688))
    }

    @Test
    fun geohashEncodeProducesPrefixHierarchy() {
        val g6 = GeoHash.encode(48.8566140, 2.3522219, 6).toString()
        val g5 = GeoHash.encode(48.8566140, 2.3522219, 5).toString()
        assertEquals(6, g6.length)
        assertEquals(g6.substring(0, 5), g5)
        // decode round-trips back to a hash with the same prefix
        assertEquals(g6, GeoHash.decode(g6).toString())
    }
}
