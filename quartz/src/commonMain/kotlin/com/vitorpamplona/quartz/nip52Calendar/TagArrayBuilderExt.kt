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
package com.vitorpamplona.quartz.nip52Calendar

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip52Calendar.tags.FreeBusyTag
import com.vitorpamplona.quartz.nip52Calendar.tags.LocationTag
import com.vitorpamplona.quartz.nip52Calendar.tags.RSVPStatusTag

// CalendarDateSlotEvent builder extensions

fun TagArrayBuilder<CalendarDateSlotEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<CalendarDateSlotEvent>.startDate(date: String) = addUnique(arrayOf("start", date))

fun TagArrayBuilder<CalendarDateSlotEvent>.endDate(date: String) = addUnique(arrayOf("end", date))

fun TagArrayBuilder<CalendarDateSlotEvent>.location(location: String) = add(LocationTag.assemble(location))

fun TagArrayBuilder<CalendarDateSlotEvent>.locations(locations: List<String>) = addAll(locations.map { LocationTag.assemble(it) })

fun TagArrayBuilder<CalendarDateSlotEvent>.summary(summary: String) = addUnique(SummaryTag.assemble(summary))

fun TagArrayBuilder<CalendarDateSlotEvent>.image(imageUrl: String) = addUnique(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<CalendarDateSlotEvent>.geohash(geohash: String) = addAll(GeoHashTag.assemble(geohash).toList())

fun TagArrayBuilder<CalendarDateSlotEvent>.participant(p: PTag) = add(p.toTagArray())

fun TagArrayBuilder<CalendarDateSlotEvent>.participants(ps: List<PTag>) = addAll(ps.map { it.toTagArray() })

// CalendarTimeSlotEvent builder extensions

fun TagArrayBuilder<CalendarTimeSlotEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<CalendarTimeSlotEvent>.startTimestamp(timestamp: Long) = addUnique(arrayOf("start", timestamp.toString()))

fun TagArrayBuilder<CalendarTimeSlotEvent>.endTimestamp(timestamp: Long) = addUnique(arrayOf("end", timestamp.toString()))

fun TagArrayBuilder<CalendarTimeSlotEvent>.startTzId(tzId: String) = addUnique(arrayOf("start_tzid", tzId))

fun TagArrayBuilder<CalendarTimeSlotEvent>.endTzId(tzId: String) = addUnique(arrayOf("end_tzid", tzId))

fun TagArrayBuilder<CalendarTimeSlotEvent>.location(location: String) = add(LocationTag.assemble(location))

fun TagArrayBuilder<CalendarTimeSlotEvent>.locations(locations: List<String>) = addAll(locations.map { LocationTag.assemble(it) })

fun TagArrayBuilder<CalendarTimeSlotEvent>.summary(summary: String) = addUnique(SummaryTag.assemble(summary))

fun TagArrayBuilder<CalendarTimeSlotEvent>.image(imageUrl: String) = addUnique(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<CalendarTimeSlotEvent>.geohash(geohash: String) = addAll(GeoHashTag.assemble(geohash).toList())

fun TagArrayBuilder<CalendarTimeSlotEvent>.participant(p: PTag) = add(p.toTagArray())

fun TagArrayBuilder<CalendarTimeSlotEvent>.participants(ps: List<PTag>) = addAll(ps.map { it.toTagArray() })

// CalendarEvent builder extensions

fun TagArrayBuilder<CalendarEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

// CalendarRSVPEvent builder extensions

fun TagArrayBuilder<CalendarRSVPEvent>.status(status: RSVPStatusTag.STATUS) = addUnique(status.toTagArray())

fun TagArrayBuilder<CalendarRSVPEvent>.freebusy(fb: FreeBusyTag.STATUS) = addUnique(fb.toTagArray())
