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
package com.vitorpamplona.quartz.experimental.trustedLists

import com.vitorpamplona.quartz.experimental.trustedLists.tags.CutoffTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.MetricTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.MinRankTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.ObserverTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.SourceTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.StatusTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.TruncatedTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag

fun TagArray.title() = fastFirstNotNullOfOrNull(TitleTag::parse)

fun TagArray.metric() = fastFirstNotNullOfOrNull(MetricTag::parse)

fun TagArray.observer() = fastFirstNotNullOfOrNull(ObserverTag::parse)

fun TagArray.sourceTag() = fastFirstNotNullOfOrNull(SourceTag::parse)

fun TagArray.cutoff() = fastFirstNotNullOfOrNull(CutoffTag::parse)

fun TagArray.minRank() = fastFirstNotNullOfOrNull(MinRankTag::parse)

fun TagArray.truncatedTotal() = fastFirstNotNullOfOrNull(TruncatedTag::parse)

fun TagArray.isTruncated() = fastAny(TruncatedTag::isTag)

fun TagArray.status() = fastFirstNotNullOfOrNull(StatusTag::parse)
