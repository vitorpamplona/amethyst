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

package com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.bookmarks as movedBookmarks
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.description as movedDescription
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.image as movedImage
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.name as movedName
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.title as movedTitle

// This package was renamed to com.vitorpamplona.quartz.nip51Lists.bookmarkSet (NIP-51 kind 30003 is a bookmark set).
// Everything below forwards to it and will be removed in a future release.

private const val MOVED = "Moved to com.vitorpamplona.quartz.nip51Lists.bookmarkSet"

@Deprecated(
    "Renamed to BookmarkSetEvent and moved to com.vitorpamplona.quartz.nip51Lists.bookmarkSet. NIP-51 kind 30003 is a bookmark set.",
    ReplaceWith("BookmarkSetEvent", "com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent"),
)
typealias LabeledBookmarkListEvent = BookmarkSetEvent

@Deprecated(MOVED, ReplaceWith("name(name)", "com.vitorpamplona.quartz.nip51Lists.bookmarkSet.name"))
fun TagArrayBuilder<BookmarkSetEvent>.name(name: String) = movedName(name)

@Deprecated(MOVED, ReplaceWith("title(title)", "com.vitorpamplona.quartz.nip51Lists.bookmarkSet.title"))
fun TagArrayBuilder<BookmarkSetEvent>.title(title: String) = movedTitle(title)

@Deprecated(MOVED, ReplaceWith("bookmarks(bookmarks)", "com.vitorpamplona.quartz.nip51Lists.bookmarkSet.bookmarks"))
fun TagArrayBuilder<BookmarkSetEvent>.bookmarks(bookmarks: List<BookmarkIdTag>) = movedBookmarks(bookmarks)

@Deprecated(MOVED, ReplaceWith("description(listDescription)", "com.vitorpamplona.quartz.nip51Lists.bookmarkSet.description"))
fun TagArrayBuilder<BookmarkSetEvent>.description(listDescription: String) = movedDescription(listDescription)

@Deprecated(MOVED, ReplaceWith("image(url)", "com.vitorpamplona.quartz.nip51Lists.bookmarkSet.image"))
fun TagArrayBuilder<BookmarkSetEvent>.image(url: String) = movedImage(url)
