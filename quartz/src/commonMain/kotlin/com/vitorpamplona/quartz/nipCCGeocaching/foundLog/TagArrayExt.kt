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
package com.vitorpamplona.quartz.nipCCGeocaching.foundLog

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.tags.EmbeddedVerificationTag
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.tags.GeocacheTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent

/** Whether [kind] is a geocache listing — 37516, or the referenced-but-undefined 37515. */
private fun isGeocacheKind(kind: Int) = kind == GeocacheListingEvent.KIND || kind == GeocacheListingEvent.LEGACY_KIND

/**
 * The geocache this log is about.
 *
 * Restricted to the listing kinds on purpose. The `a` tag is whatever its author wrote, and
 * nothing stops a found log from pointing at an article, a calendar event or a profile badge —
 * so a consumer that resolves it unfiltered files a "Found it!" under an unrelated addressable,
 * or hands a caller a note of the wrong type. A log naming a non-cache is malformed, and reads
 * as absent.
 */
fun TagArray.geocache(): Address? = firstNotNullOfOrNull(GeocacheTag::parseAddress)?.takeIf { isGeocacheKind(it.kind) }

/** The raw `a` value, for the same reason and with the same restriction as [geocache]. */
fun TagArray.geocacheId(): String? = geocache()?.toValue()

fun TagArray.logImages() = mapNotNull(ImageTag::parse)

/**
 * Whether a `verification` tag is present at all.
 *
 * Cheap: it looks at tag names only. [embeddedVerification] has to run the payload through the
 * JSON parser and the event factory, which is not something a composable should do on every
 * recomposition just to decide whether there is anything to check.
 */
fun TagArray.hasEmbeddedVerification() = fastAny(EmbeddedVerificationTag::isTag)

fun TagArray.embeddedVerification() = firstNotNullOfOrNull(EmbeddedVerificationTag::parse)
