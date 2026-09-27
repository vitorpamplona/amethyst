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
package com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange
import com.vitorpamplona.quartz.nip01Core.diff.EventDiff
import com.vitorpamplona.quartz.nip01Core.diff.ListDiff
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent

/** `a` pointers to kind:30000 follow sets; pointers to any other kind are not follow sets and are skipped. */
fun TagArray.favoriteFollowSetBookmarks() = mapNotNull { tag -> AddressBookmark.parse(tag)?.takeIf { it.address.kind == FollowSetEvent.KIND } }

fun TagArray.favoriteFollowSetsSet() = mapNotNullTo(mutableSetOf()) { tag -> AddressBookmark.parseAddress(tag)?.takeIf { it.kind == FollowSetEvent.KIND } }

fun TagArrayBuilder<FavoriteFollowSetsListEvent>.favoriteFollowSet(followSet: AddressBookmark) = add(followSet.toTagArray())

fun TagArrayBuilder<FavoriteFollowSetsListEvent>.favoriteFollowSets(followSets: List<AddressBookmark>) = addAll(followSets.map { it.toTagArray() })

/** Changes to the favorite follow sets: public ones as parsed bookmarks, private ones as a whole. */
@Immutable
class FavoriteFollowSetsListDiff(
    val followSets: ListDiff<AddressBookmark>,
    val privateItems: ContentChange,
) : EventDiff {
    override fun removesData() = privateItems.publicRemovalsAreLoss(followSets.hasRemovals()) || privateItems.isRemoval()
}
