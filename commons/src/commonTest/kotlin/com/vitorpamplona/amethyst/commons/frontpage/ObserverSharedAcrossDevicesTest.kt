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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One account's Observer on every device (`commons/plans/2026-11-20-observer-synced-settings-and-editions.md`):
 * the publishing choice is the schedule's `publish` param, and every paper a device prints is
 * recorded in the schedule's private tags, with an encrypted copy, for the others.
 */
class ObserverSharedAcrossDevicesTest {
    /** 2026-11-20T12:00:00Z. */
    private val noon = 1_795_176_000L
    private val day = 86_400L

    private val schedule = ObserverSchedule.new("UTC", noon - 30 * day).withEnabled(true, noon - 30 * day)

    private fun record(
        printedAt: Long,
        sha: Char,
        by: String? = "Desktop (Linux)",
    ) = EditionRecord(
        day = ObserverSite.utcDay(printedAt),
        printedAt = printedAt,
        sha256 = sha.toString().repeat(64),
        code = "C$sha",
        by = by,
        headline = "H$sha",
    )

    /** A record with an encrypted copy to open. */
    private fun withCopy(record: EditionRecord) = record.copy(blob = "b".repeat(64), key = "c".repeat(64), nonce = "d".repeat(24))

    // --- publish ------------------------------------------------------------------------------

    @Test
    fun noParamIsOnAndNotYetConfirmed() {
        assertEquals(ObserverPrefs(publish = true, noticeSeen = false), FrontPageParams.publishing(schedule))
        assertEquals(ObserverPrefs(publish = true, noticeSeen = false), FrontPageParams.publishing(null))
        assertFalse(FrontPageParams.publishing(schedule).publishes)
    }

    @Test
    fun publishingRoundTripsThroughTheParam() {
        val yes = FrontPageParams.withPublishing(schedule, ObserverPrefs(publish = true, noticeSeen = true))
        assertEquals(listOf("yes"), yes.paramValues(FrontPageParams.PUBLISH))
        assertTrue(FrontPageParams.publishing(yes).publishes)

        val no = FrontPageParams.withPublishing(yes, ObserverPrefs(publish = false))
        assertEquals(listOf("no"), no.paramValues(FrontPageParams.PUBLISH))
        assertFalse(FrontPageParams.publishing(no).publish)

        // Back on: the notice is asked again, so the param goes.
        val again = FrontPageParams.withPublishing(no, ObserverPrefs(publish = true))
        assertNull(again.param(FrontPageParams.PUBLISH))
        assertEquals(ObserverPrefs(), FrontPageParams.publishing(again))
    }

    @Test
    fun anUnknownPublishValueReadsAsOff() {
        val odd = schedule.withParam(FrontPageParams.PUBLISH, "maybe")
        assertFalse(FrontPageParams.publishing(odd).publish)
        assertTrue(FrontPageParams.understands(odd))
    }

    @Test
    fun publishIsUnderstoodAndKeepsTheOtherSettings() {
        val deep = FrontPageParams.withEffort(schedule, WritingEffort.DEEP)
        val off = FrontPageParams.withPublishing(deep, ObserverPrefs(publish = false))
        assertTrue(FrontPageParams.understands(off))
        assertTrue(devicesPrint(off))
        assertEquals(WritingEffort.DEEP, FrontPageParams.effort(off))
        assertEquals(schedule.cron, off.cron)
        // A choice made with no schedule yet lives in a settings-only event, which never runs.
        val settings = FrontPageParams.withPublishing(observerSettingsOnly(), ObserverPrefs(publish = false))
        assertTrue(isSettingsOnly(settings))
        assertFalse(runsHereAtAll(settings))
    }

    // --- the record's tag ---------------------------------------------------------------------

    @Test
    fun aRecordRoundTripsThroughItsTag() {
        val full = record(noon, 'a').copy(blob = "b".repeat(64), key = "c".repeat(64), nonce = "d".repeat(24))
        assertEquals(full, EditionRecord.parse(full.toTag()))
        assertTrue(full.hasCopy)

        val bare = record(noon, 'e', by = null)
        val parsed = assertNotNull(EditionRecord.parse(bare.toTag()))
        assertEquals(bare, parsed)
        assertFalse(parsed.hasCopy)
    }

    @Test
    fun aRecordThatCantBeReadIsNoRecord() {
        val good = record(noon, 'a').toTag()
        assertNull(EditionRecord.parse(arrayOf("edition")))
        assertNull(EditionRecord.parse(good.copyOf().also { it[1] = "yesterday" }))
        assertNull(EditionRecord.parse(good.copyOf().also { it[2] = "soon" }))
        assertNull(EditionRecord.parse(good.copyOf().also { it[3] = "not a hash" }))
        assertNull(EditionRecord.parse(arrayOf("param", "publish", "no")))
    }

    @Test
    fun aHalfCopyIsNoCopy() {
        // A blob without its key can't be opened: the record still says the day is printed.
        val tag = record(noon, 'a').copy(blob = "b".repeat(64), key = "c".repeat(64), nonce = "d".repeat(24)).toTag()
        tag[7] = ""
        val parsed = assertNotNull(EditionRecord.parse(tag))
        assertFalse(parsed.hasCopy)
        assertNull(parsed.blob)
        assertNull(parsed.nonce)
    }

    @Test
    fun aLongHeadlineIsCut() {
        val long = record(noon, 'a').copy(headline = "x".repeat(500))
        assertEquals(EditionRecord.HEADLINE_MAX, long.toTag()[9].length)
    }

    // --- records in the schedule --------------------------------------------------------------

    @Test
    fun aRecordGoesPrivateAndKeepsEveryOtherTag() {
        val deep = FrontPageParams.withEffort(schedule, WritingEffort.DEEP)
        val next = SharedEditions.withRecord(deep, record(noon, 'a'))
        assertEquals(listOf(record(noon, 'a')), SharedEditions.records(next))
        assertTrue(next.privateTags.any(EditionRecord::isTag))
        assertFalse(next.publicTags.any(EditionRecord::isTag))
        assertEquals(WritingEffort.DEEP, FrontPageParams.effort(next))
        assertEquals(deep.cron, next.cron)
        assertEquals(deep.task, next.task)
        // Not a param: a printer that doesn't know it still runs the schedule.
        assertTrue(FrontPageParams.understands(next))
    }

    @Test
    fun oneRecordPerDayTheNewestWins() {
        val morning = SharedEditions.withRecord(schedule, record(noon - 3_600, 'a'))
        val evening = SharedEditions.withRecord(morning, record(noon + 3_600, 'b'))
        assertEquals(listOf(record(noon + 3_600, 'b')), SharedEditions.records(evening))
        // An older print of the same day doesn't take the place of the newer one.
        val late = SharedEditions.withRecord(evening, record(noon - 7_200, 'c'))
        assertEquals(listOf(record(noon + 3_600, 'b')), SharedEditions.records(late))
    }

    @Test
    fun onlyTheNewestWeekStays() {
        var task = schedule
        for (i in 0 until 10) task = SharedEditions.withRecord(task, record(noon - i * day, "0123456789"[i]))
        val kept = SharedEditions.records(task)
        assertEquals(EditionRecord.KEEP, kept.size)
        assertEquals(noon, kept.first().printedAt)
        assertEquals(noon - (EditionRecord.KEEP - 1) * day, kept.last().printedAt)
        // A paper older than every kept day changes nothing.
        assertEquals(task, SharedEditions.withRecord(task, record(noon - 20 * day, 'f')))
    }

    @Test
    fun aRecordAnotherWriterLeftPublicIsReplacedPrivately() {
        val public = ScheduledTask(schedule.publicTags + listOf(record(noon, 'a').toTag()), schedule.privateTags)
        assertEquals(listOf(record(noon, 'a')), SharedEditions.records(public))
        val next = SharedEditions.withRecord(public, record(noon + 60, 'b'))
        assertFalse(next.publicTags.any(EditionRecord::isTag))
        assertEquals(listOf(record(noon + 60, 'b')), SharedEditions.records(next))
    }

    // --- the encrypted copy -------------------------------------------------------------------

    @Test
    fun aSealedPageOpensOnlyAsItself() {
        val page = "<!doctype html><title>The Nostr Observer</title><p>Hello</p>".encodeToByteArray()
        val sealed = SharedEditions.seal(page)
        assertFalse(sealed.bytes.contentEquals(page))
        val rec = record(noon, 'a').copy(sha256 = ObserverPublisher.sha256Hex(page), blob = sealed.blob, key = sealed.key, nonce = sealed.nonce)
        assertNotNull(EditionRecord.parse(rec.toTag())?.takeIf { it.hasCopy })

        assertContentEquals(page, assertNotNull(SharedEditions.open(rec, sealed.bytes)).encodeToByteArray())
        // Bytes that aren't the blob, a wrong key, or a page that isn't the recorded one: nothing.
        assertNull(SharedEditions.open(rec, sealed.bytes.copyOf().also { it[0] = (it[0] + 1).toByte() }))
        assertNull(SharedEditions.open(rec.copy(key = "0".repeat(64)), sealed.bytes))
        assertNull(SharedEditions.open(rec.copy(sha256 = "f".repeat(64)), sealed.bytes))
    }

    @Test
    fun everySealUsesItsOwnKey() {
        val page = "same".encodeToByteArray()
        val a = SharedEditions.seal(page)
        val b = SharedEditions.seal(page)
        assertFalse(a.key == b.key)
        assertFalse(a.blob == b.blob)
    }

    // --- what the other devices see -----------------------------------------------------------

    @Test
    fun theHistoryShowsWhatAnotherDevicePrinted() {
        val here = SavedEdition(day = ObserverSite.utcDay(noon - day), code = "L", printedAt = noon - day, model = "m", sha256 = "1".repeat(64))
        val history = SharedEditions.mergeRecords(listOf(here), listOf(withCopy(record(noon, 'a')), withCopy(record(noon - day, 'b'))))
        assertEquals(2, history.size)
        val shared = history.first()
        assertEquals("Desktop (Linux)", shared.printedBy)
        assertEquals("Ha", shared.headline)
        assertFalse(shared.published)
        // A day this device has (or the same bytes, under any day) stays this device's.
        assertEquals(here, history[1])
        val sameBytes = SharedEditions.mergeRecords(listOf(here), listOf(withCopy(record(noon, '1'))))
        assertEquals(listOf(here), sameBytes)
    }

    @Test
    fun aRecordWithNoCopyCountsAsPrintedButIsNeverShownAsToday() {
        // The desktop had no Blossom server: the phone can't open it, so it isn't today's paper there...
        val noCopy = record(noon - 3_600, 'a')
        assertEquals(emptyList(), SharedEditions.mergeRecords(emptyList(), listOf(noCopy)))
        val utc = ObserverCalendar.of(CronZone.fixed(0))
        assertNull(observerToday(SharedEditions.mergeRecords(emptyList(), listOf(noCopy)), noon, utc).paper)
        // ...but the morning is still printed, for the claim and the status line.
        val sites = observerSitesOf(emptyList(), emptyList(), emptyList(), listOf(noCopy))
        assertTrue(ObserverSite.alreadyPrinted(sites.reader, emptyList(), noon - 4 * 3_600, CronZone.fixed(0)))
    }

    @Test
    fun anEditionTagThisCodeCantReadIsKept() {
        val later = arrayOf("edition", "2026-11-20", "1795176000", "a".repeat(64), "C", "Pixel", "", "", "", "", "", "a field from a later version")
        val unreadable = arrayOf("edition", "a day in a format to come")
        val task = ScheduledTask(schedule.publicTags + listOf(unreadable), schedule.privateTags)
        val next = SharedEditions.withRecord(task, record(noon, 'b'))
        assertTrue(next.publicTags.any { it.contentEquals(unreadable) })
        assertEquals(listOf(record(noon, 'b')), SharedEditions.records(next))
        // A readable one with more fields than this code knows is read, and rewritten in its shape.
        assertNotNull(EditionRecord.parse(later))
    }

    @Test
    fun todayIsTheOtherDevicesPaper() {
        val utc = ObserverCalendar.of(CronZone.fixed(0))
        val today = observerToday(SharedEditions.mergeRecords(emptyList(), listOf(withCopy(record(noon - 3_600, 'a')))), noon, utc)
        assertEquals("Ha", assertNotNull(today.paper).headline)
    }

    @Test
    fun anotherDevicesPaperCountsAsPrintedForTheMorning() {
        val zone = CronZone.fixed(0)
        val occurrence = noon - 4 * 3_600
        val sites = observerSitesOf(emptyList(), emptyList(), emptyList(), listOf(record(noon - 5 * 3_600, 'a')))
        assertEquals(1, sites.reader.size)
        assertTrue(ObserverSite.alreadyPrinted(sites.reader, emptyList(), occurrence, zone))
        // Yesterday's paper doesn't cover today's morning.
        val old = observerSitesOf(emptyList(), emptyList(), emptyList(), listOf(record(noon - day - 5 * 3_600, 'a')))
        assertFalse(ObserverSite.alreadyPrinted(old.reader, emptyList(), occurrence, zone))
    }
}
