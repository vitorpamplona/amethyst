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
package com.vitorpamplona.quartz.nip99Classifieds.shipping

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.CarrierTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.CountryTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.DurationTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.RegionTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.ServiceTag

/** Every country code across all `country` tags: one tag may list many, and clients repeat it. */
fun TagArray.countries(): List<String> = collectCodes(CountryTag::parse)

fun TagArray.regions(): List<String> = collectCodes(RegionTag::parse)

fun TagArray.service() = firstNotNullOfOrNull(ServiceTag::parse)

fun TagArray.serviceType() = firstNotNullOfOrNull(ServiceTag::parseService)

fun TagArray.carrier() = firstNotNullOfOrNull(CarrierTag::parse)

fun TagArray.duration() = firstNotNullOfOrNull(DurationTag::parse)

private inline fun TagArray.collectCodes(parse: (Array<String>) -> List<String>?): List<String> {
    var codes: MutableList<String>? = null
    fastForEach { tag ->
        val found = parse(tag) ?: return@fastForEach
        (codes ?: ArrayList<String>(found.size).also { codes = it }).addAll(found)
    }
    return codes ?: emptyList()
}
