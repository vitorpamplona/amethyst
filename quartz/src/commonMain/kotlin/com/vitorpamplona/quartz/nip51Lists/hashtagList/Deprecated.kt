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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.quartz.nip51Lists.hashtagList

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListDiff
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.followHashTag as movedFollowHashTag
import com.vitorpamplona.quartz.nip51Lists.interestList.hashtagList as movedHashtagList
import com.vitorpamplona.quartz.nip51Lists.interestList.hashtagSet as movedHashtagSet
import com.vitorpamplona.quartz.nip51Lists.interestList.hashtags as movedHashtags

// This package was renamed to com.vitorpamplona.quartz.nip51Lists.interestList (NIP-51 kind 10015 is the interests list).
// Everything below forwards to it and will be removed in a future release.

private const val MOVED = "Moved to com.vitorpamplona.quartz.nip51Lists.interestList"

@Deprecated(
    "Renamed to InterestListEvent and moved to com.vitorpamplona.quartz.nip51Lists.interestList. NIP-51 kind 10015 is the interests list.",
    ReplaceWith("InterestListEvent", "com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent"),
)
typealias HashtagListEvent = InterestListEvent

@Deprecated(
    "Renamed to InterestListDiff and moved to com.vitorpamplona.quartz.nip51Lists.interestList. NIP-51 kind 10015 is the interests list.",
    ReplaceWith("InterestListDiff", "com.vitorpamplona.quartz.nip51Lists.interestList.InterestListDiff"),
)
typealias HashtagListDiff = InterestListDiff

@Deprecated(MOVED, ReplaceWith("followHashTag(hashtag)", "com.vitorpamplona.quartz.nip51Lists.interestList.followHashTag"))
fun TagArrayBuilder<InterestListEvent>.followHashTag(hashtag: String) = movedFollowHashTag(hashtag)

@Deprecated(MOVED, ReplaceWith("hashtags(hashtags)", "com.vitorpamplona.quartz.nip51Lists.interestList.hashtags"))
fun TagArrayBuilder<InterestListEvent>.hashtags(hashtags: List<String>) = movedHashtags(hashtags)

@Deprecated(MOVED, ReplaceWith("hashtagList()", "com.vitorpamplona.quartz.nip51Lists.interestList.hashtagList"))
fun TagArray.hashtagList() = movedHashtagList()

@Deprecated(MOVED, ReplaceWith("hashtagSet()", "com.vitorpamplona.quartz.nip51Lists.interestList.hashtagSet"))
fun TagArray.hashtagSet() = movedHashtagSet()
