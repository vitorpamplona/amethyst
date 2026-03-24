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
package com.vitorpamplona.amethyst.desktop.ui.deck

import java.util.UUID

sealed class DeckColumnType {
    object HomeFeed : DeckColumnType()

    object Notifications : DeckColumnType()

    object Messages : DeckColumnType()

    object Search : DeckColumnType()

    object Reads : DeckColumnType()

    object Bookmarks : DeckColumnType()

    object GlobalFeed : DeckColumnType()

    object MyProfile : DeckColumnType()

    object Chess : DeckColumnType()

    object Settings : DeckColumnType()

    data class Profile(
        val pubKeyHex: String,
    ) : DeckColumnType()

    data class Thread(
        val noteId: String,
    ) : DeckColumnType()

    data class Article(
        val addressTag: String,
    ) : DeckColumnType()

    data class Editor(
        val draftSlug: String? = null,
    ) : DeckColumnType()

    object Drafts : DeckColumnType()

    data class Hashtag(
        val tag: String,
    ) : DeckColumnType()

    fun title(): String =
        when (this) {
            HomeFeed -> "Home"
            Notifications -> "Notifications"
            Messages -> "Messages"
            Search -> "Search"
            Reads -> "Reads"
            Bookmarks -> "Bookmarks"
            GlobalFeed -> "Global"
            MyProfile -> "Profile"
            Chess -> "Chess"
            Settings -> "Settings"
            is Article -> "Article"
            is Editor -> "New Article"
            Drafts -> "Drafts"
            is Profile -> "Profile"
            is Thread -> "Thread"
            is Hashtag -> "#$tag"
        }

    fun typeKey(): String =
        when (this) {
            HomeFeed -> "home"
            Notifications -> "notifications"
            Messages -> "messages"
            Search -> "search"
            Reads -> "reads"
            Bookmarks -> "bookmarks"
            GlobalFeed -> "global"
            MyProfile -> "my_profile"
            Chess -> "chess"
            Settings -> "settings"
            is Article -> "article"
            is Editor -> "editor"
            Drafts -> "drafts"
            is Profile -> "profile"
            is Thread -> "thread"
            is Hashtag -> "hashtag"
        }
}

data class DeckColumn(
    val id: String = UUID.randomUUID().toString(),
    val type: DeckColumnType,
    val width: Float = 400f,
)
