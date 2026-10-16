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

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.CarrierTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.CountryTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.DurationTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.RegionTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.ServiceTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.ShippingService
import com.vitorpamplona.quartz.nip99Classifieds.tags.LocationTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag

fun TagArrayBuilder<ShippingOptionEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<ShippingOptionEvent>.price(price: PriceTag) = addUnique(price.toTagArray())

fun TagArrayBuilder<ShippingOptionEvent>.countries(codes: List<String>) = addUnique(CountryTag.assemble(codes))

fun TagArrayBuilder<ShippingOptionEvent>.regions(codes: List<String>) = addUnique(RegionTag.assemble(codes))

fun TagArrayBuilder<ShippingOptionEvent>.service(service: ShippingService) = addUnique(ServiceTag.assemble(service))

fun TagArrayBuilder<ShippingOptionEvent>.carrier(carrier: String) = addUnique(CarrierTag.assemble(carrier))

fun TagArrayBuilder<ShippingOptionEvent>.duration(duration: DurationTag) = addUnique(duration.toTagArray())

fun TagArrayBuilder<ShippingOptionEvent>.location(location: String) = addUnique(LocationTag.assemble(location))

fun TagArrayBuilder<ShippingOptionEvent>.geohash(geohash: String) = addUnique(GeoHashTag.assembleSingle(geohash))
