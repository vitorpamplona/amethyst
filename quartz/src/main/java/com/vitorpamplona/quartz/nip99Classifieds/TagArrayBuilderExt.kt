/**
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

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.ConditionTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.LocationTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.StatusTag

fun TagArrayBuilder<ClassifiedsEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<ClassifiedsEvent>.summary(summary: String) = addUnique(SummaryTag.assemble(summary))

fun TagArrayBuilder<ClassifiedsEvent>.location(location: String) = addUnique(LocationTag.assemble(location))

fun TagArrayBuilder<ClassifiedsEvent>.image(imageUrl: String) = add(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<ClassifiedsEvent>.images(imageUrls: List<String>) = addAll(imageUrls.map { ImageTag.assemble(it) })

fun TagArrayBuilder<ClassifiedsEvent>.condition(condition: ConditionTag.CONDITION) = addUnique(condition.toTagArray())

fun TagArrayBuilder<ClassifiedsEvent>.status(status: StatusTag.STATUS) = addUnique(status.toTagArray())

fun TagArrayBuilder<ClassifiedsEvent>.price(price: PriceTag) = addUnique(price.toTagArray())

fun TagArrayBuilder<ClassifiedsEvent>.publishedAt(publishedAt: Long) = addUnique(PublishedAtTag.assemble(publishedAt))
