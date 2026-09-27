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

package com.vitorpamplona.quartz.nip51Lists.peopleList

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.followSet.description as movedDescription
import com.vitorpamplona.quartz.nip51Lists.followSet.image as movedImage
import com.vitorpamplona.quartz.nip51Lists.followSet.name as movedName
import com.vitorpamplona.quartz.nip51Lists.followSet.peoples as movedPeoples
import com.vitorpamplona.quartz.nip51Lists.followSet.title as movedTitle
import com.vitorpamplona.quartz.nip51Lists.followSet.userIdSet as movedUserIdSet
import com.vitorpamplona.quartz.nip51Lists.followSet.userIds as movedUserIds
import com.vitorpamplona.quartz.nip51Lists.followSet.users as movedUsers
import com.vitorpamplona.quartz.nip51Lists.followSet.usersAndWords as movedUsersAndWords
import com.vitorpamplona.quartz.nip51Lists.followSet.wordSet as movedWordSet
import com.vitorpamplona.quartz.nip51Lists.followSet.words as movedWords

// This package was renamed to com.vitorpamplona.quartz.nip51Lists.followSet (NIP-51 kind 30000 is a follow set).
// Everything below forwards to it and will be removed in a future release.

private const val MOVED = "Moved to com.vitorpamplona.quartz.nip51Lists.followSet"

@Deprecated(
    "Renamed to FollowSetEvent and moved to com.vitorpamplona.quartz.nip51Lists.followSet. NIP-51 kind 30000 is a follow set.",
    ReplaceWith("FollowSetEvent", "com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent"),
)
typealias PeopleListEvent = FollowSetEvent

@Deprecated(MOVED, ReplaceWith("name(name)", "com.vitorpamplona.quartz.nip51Lists.followSet.name"))
fun TagArrayBuilder<FollowSetEvent>.name(name: String) = movedName(name)

@Deprecated(MOVED, ReplaceWith("title(name)", "com.vitorpamplona.quartz.nip51Lists.followSet.title"))
fun TagArrayBuilder<FollowSetEvent>.title(name: String) = movedTitle(name)

@Deprecated(MOVED, ReplaceWith("description(desc)", "com.vitorpamplona.quartz.nip51Lists.followSet.description"))
fun TagArrayBuilder<FollowSetEvent>.description(desc: String) = movedDescription(desc)

@Deprecated(MOVED, ReplaceWith("image(url)", "com.vitorpamplona.quartz.nip51Lists.followSet.image"))
fun TagArrayBuilder<FollowSetEvent>.image(url: String) = movedImage(url)

@Deprecated(MOVED, ReplaceWith("peoples(peoples)", "com.vitorpamplona.quartz.nip51Lists.followSet.peoples"))
fun TagArrayBuilder<FollowSetEvent>.peoples(peoples: List<UserTag>) = movedPeoples(peoples)

@Deprecated(MOVED, ReplaceWith("usersAndWords()", "com.vitorpamplona.quartz.nip51Lists.followSet.usersAndWords"))
fun TagArray.usersAndWords() = movedUsersAndWords()

@Deprecated(MOVED, ReplaceWith("users()", "com.vitorpamplona.quartz.nip51Lists.followSet.users"))
fun TagArray.users() = movedUsers()

@Deprecated(MOVED, ReplaceWith("userIds()", "com.vitorpamplona.quartz.nip51Lists.followSet.userIds"))
fun TagArray.userIds() = movedUserIds()

@Deprecated(MOVED, ReplaceWith("userIdSet()", "com.vitorpamplona.quartz.nip51Lists.followSet.userIdSet"))
fun TagArray.userIdSet() = movedUserIdSet()

@Deprecated(MOVED, ReplaceWith("words()", "com.vitorpamplona.quartz.nip51Lists.followSet.words"))
fun TagArray.words() = movedWords()

@Deprecated(MOVED, ReplaceWith("wordSet()", "com.vitorpamplona.quartz.nip51Lists.followSet.wordSet"))
fun TagArray.wordSet() = movedWordSet()
