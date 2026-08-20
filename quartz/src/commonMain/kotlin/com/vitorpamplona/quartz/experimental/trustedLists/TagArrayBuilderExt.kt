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
import com.vitorpamplona.quartz.experimental.trustedLists.tags.ListStatus
import com.vitorpamplona.quartz.experimental.trustedLists.tags.MetricTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.MinRankTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.ObserverTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.SourceTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.StatusTag
import com.vitorpamplona.quartz.experimental.trustedLists.tags.TruncatedTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag

fun <T : TrustedListEvent> TagArrayBuilder<T>.title(title: String) = addUnique(TitleTag.assemble(title))

fun <T : TrustedListEvent> TagArrayBuilder<T>.metric(metric: String) = addUnique(MetricTag.assemble(metric))

fun <T : TrustedListEvent> TagArrayBuilder<T>.observer(observer: HexKey) = addUnique(ObserverTag.assemble(observer))

fun <T : TrustedListEvent> TagArrayBuilder<T>.sourceTag(source: SourceTag) = addUnique(source.toTagArray())

fun <T : TrustedListEvent> TagArrayBuilder<T>.sourceTag(
    eventId: HexKey,
    author: HexKey? = null,
    slug: String? = null,
) = addUnique(SourceTag.assemble(eventId, author, slug))

fun <T : TrustedListEvent> TagArrayBuilder<T>.cutoff(cutoff: Int) = addUnique(CutoffTag.assemble(cutoff))

fun <T : TrustedListEvent> TagArrayBuilder<T>.minRank(minRank: Int) = addUnique(MinRankTag.assemble(minRank))

/**
 * Marks the membership as *not* exhaustive, carrying the true [total]. Only
 * add it when the list had to be cut: its absence is what tells consumers the
 * list is complete.
 */
fun <T : TrustedListEvent> TagArrayBuilder<T>.truncated(total: Int) = addUnique(TruncatedTag.assemble(total))

fun <T : TrustedListEvent> TagArrayBuilder<T>.status(status: ListStatus) = addUnique(StatusTag.assemble(status))
