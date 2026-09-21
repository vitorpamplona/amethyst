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
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.tags.EmbeddedVerificationTag
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.tags.GeocacheTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent

fun TagArrayBuilder<GeocacheFoundLogEvent>.geocache(cache: EventHintBundle<GeocacheListingEvent>) = addUnique(GeocacheTag.assemble(cache))

fun TagArrayBuilder<GeocacheFoundLogEvent>.geocache(
    cache: Address,
    relayHint: NormalizedRelayUrl? = null,
) = addUnique(GeocacheTag.assemble(cache, relayHint))

fun TagArrayBuilder<GeocacheFoundLogEvent>.embeddedVerification(verification: GeocacheVerificationEvent) = addUnique(EmbeddedVerificationTag.assemble(verification))

fun TagArrayBuilder<GeocacheFoundLogEvent>.logImages(urls: List<String>) = addAllUniqueValueIfNew(urls.map(ImageTag::assemble))
