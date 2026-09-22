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
package com.vitorpamplona.amethyst.ui.note.types.lists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList.LabeledBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.pictureCurationSet.PictureCurationSetEvent

/** NIP-51 kind 10003: the user's bookmarks — notes, articles, hashtags and links. */
@Composable
fun RenderBookmarkList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? BookmarkListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicBookmarks() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateBookmarks(it) }

    BookmarkCard(
        title = listTitle(noteEvent.title(), null, R.string.kind_bookmark_list),
        description = null,
        public = public,
        private = private,
        hidesPrivate = noteEvent.hidesPrivateMembers(private),
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * NIP-51 kind 30001: the deprecated bookmark set. Still rendered, because the events are on relays
 * whether or not the spec wants them to be.
 */
@Suppress("DEPRECATION")
@Composable
fun RenderOldBookmarkList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? OldBookmarkListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicBookmarks() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateBookmarks(it) }

    BookmarkCard(
        title = listTitle(noteEvent.title(), noteEvent.dTag(), R.string.kind_bookmark_list),
        description = null,
        public = public,
        private = private,
        hidesPrivate = noteEvent.hidesPrivateMembers(private),
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** NIP-51 kind 30003: "user-defined bookmarks categories, for when bookmarks must be in labeled separate groups". */
@Composable
fun RenderLabeledBookmarkList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? LabeledBookmarkListEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicBookmarks() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateBookmarks(it) }

    BookmarkCard(
        title = listTitle(noteEvent.titleOrName(), noteEvent.dTag(), R.string.kind_bookmark_set),
        description = noteEvent.description(),
        public = public,
        private = private,
        hidesPrivate = noteEvent.hidesPrivateMembers(private),
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** NIP-51 kind 30004: "groups of articles picked by users as interesting". */
@Composable
fun RenderArticleCurationSet(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? ArticleCurationSetEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicItems() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateItems(it) }

    BookmarkCard(
        title = listTitle(noteEvent.title(), noteEvent.dTag(), R.string.kind_article_curation_set),
        description = noteEvent.description(),
        public = public,
        private = private,
        hidesPrivate = noteEvent.hidesPrivateMembers(private),
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** NIP-51 kind 30006: "groups of pictures picked by users as interesting". */
@Composable
fun RenderPictureCurationSet(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? PictureCurationSetEvent ?: return

    val public = remember(noteEvent) { noteEvent.publicItems() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateItems(it) }

    BookmarkCard(
        title = listTitle(noteEvent.title(), noteEvent.dTag(), R.string.kind_picture_curation_set),
        description = noteEvent.description(),
        public = public,
        private = private,
        hidesPrivate = noteEvent.hidesPrivateMembers(private),
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * The five bookmark-shaped kinds differ only in where their name comes from; what they hold is the
 * same `e`/`a` reference, so the body they draw is the same one.
 */
@Composable
private fun BookmarkCard(
    title: String,
    description: String?,
    public: List<BookmarkIdTag>,
    private: List<BookmarkIdTag>?,
    hidesPrivate: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ListCard(
        title = title,
        description = description,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = hidesPrivate,
        backgroundColor = backgroundColor,
    ) { item ->
        key(item.toTagIdOnly().joinToString(":")) {
            BookmarkMemberRow(item, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}
