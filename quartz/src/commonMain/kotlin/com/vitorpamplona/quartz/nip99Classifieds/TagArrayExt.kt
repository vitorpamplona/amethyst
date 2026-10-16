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
package com.vitorpamplona.quartz.nip99Classifieds

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.ProductTypeTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.ShippingOptionTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.SpecTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.StockTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.VisibilityTag

// The Open Markets product tags: the e-commerce extension NIP-99 links to.

fun TagArray.geohash() = firstNotNullOfOrNull(GeoHashTag::parse)

fun TagArray.productType() = firstNotNullOfOrNull(ProductTypeTag::parse)

fun TagArray.visibility() = firstNotNullOfOrNull(VisibilityTag::parseVisibility)

/** True only for an explicit `hidden`: an absent or unknown visibility is on sale. */
fun TagArray.isHidden() = firstNotNullOfOrNull(VisibilityTag::parse) == VisibilityTag.VISIBILITY.HIDDEN.code

fun TagArray.stock() = firstNotNullOfOrNull(StockTag::parse)

fun TagArray.specs() = mapNotNull(SpecTag::parse)

fun TagArray.shippingOptions() = mapNotNull(ShippingOptionTag::parse)

/** The addresses of kind [kind] named by `a` tags, in tag order. */
fun TagArray.addressesOfKind(kind: Int): List<Address> = mapNotNull { tag -> ATag.parseAddress(tag)?.takeIf { it.kind == kind } }
