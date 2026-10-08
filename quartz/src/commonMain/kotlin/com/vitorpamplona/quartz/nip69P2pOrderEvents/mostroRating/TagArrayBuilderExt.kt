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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.tags.DaysTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.tags.LastRatingTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.tags.MaxRateTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.tags.MinRateTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.tags.SinceTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.tags.TotalRatingTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.tags.TotalReviewsTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.DocumentTypeTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.PlatformTag

fun TagArrayBuilder<MostroUserRatingEvent>.platform(instanceName: String?) = addUnique(PlatformTag.assemble(PlatformTag.MOSTRO, instanceName))

fun TagArrayBuilder<MostroUserRatingEvent>.documentType() = addUnique(DocumentTypeTag.assemble(DocumentTypeTag.RATING))

fun TagArrayBuilder<MostroUserRatingEvent>.totalReviews(count: Long) = addUnique(TotalReviewsTag.assemble(count))

fun TagArrayBuilder<MostroUserRatingEvent>.totalRating(average: Double) = addUnique(TotalRatingTag.assemble(average))

fun TagArrayBuilder<MostroUserRatingEvent>.lastRating(rating: Long) = addUnique(LastRatingTag.assemble(rating))

fun TagArrayBuilder<MostroUserRatingEvent>.maxRate(rating: Long) = addUnique(MaxRateTag.assemble(rating))

fun TagArrayBuilder<MostroUserRatingEvent>.minRate(rating: Long) = addUnique(MinRateTag.assemble(rating))

fun TagArrayBuilder<MostroUserRatingEvent>.since(dayStart: Long) = addUnique(SinceTag.assemble(dayStart))

fun TagArrayBuilder<MostroUserRatingEvent>.days(days: Long) = addUnique(DaysTag.assemble(days))
