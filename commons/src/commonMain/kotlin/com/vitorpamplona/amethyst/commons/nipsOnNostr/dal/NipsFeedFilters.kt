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
package com.vitorpamplona.amethyst.commons.nipsOnNostr.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.nipsOnNostr.definesKind
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent

/**
 * Every NIP published on Nostr (kind 30817) in the cache, newest first: the NIPs screen's feed. A
 * NIP by a muted or blocked author, or one the account's spam rules reject, is left out, as in every
 * other feed. It is kept in [com.vitorpamplona.amethyst.commons.viewmodels.AccountFeedContentStates],
 * so new NIPs, new versions and deletions reach it through the cache like any other feed's.
 */
class NipsFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex

    override fun limit() = 1000

    override fun feed(): List<Note> = sort(LocalCache.addressables.filterIntoSet(NipTextEvent.KIND) { _, note -> accept(note) })

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { accept(it) }

    private fun accept(note: Note): Boolean = note.event is NipTextEvent && !note.isHiddenFor(account.hiddenUsers.flow.value) && account.isAcceptable(note)

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}

/** The NIPs on Nostr whose `k` tags define [kind], newest first: the kind page's first section. */
class NipsDefiningKindFeedFilter(
    val kind: Int,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-k" + kind

    override fun limit() = 100

    override fun feed(): List<Note> = sort(LocalCache.addressables.filterIntoSet(NipTextEvent.KIND) { _, note -> accept(note) })

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { accept(it) }

    private fun accept(note: Note): Boolean {
        val event = note.event as? NipTextEvent ?: return false
        return definesKind(event.tags, kind) && !note.isHiddenFor(account.hiddenUsers.flow.value) && account.isAcceptable(note)
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}

/**
 * The events of [kind] in the cache, newest first: the kind page's sample. A replaceable or
 * addressable kind lives among the addressable notes (an unknown one too, at its address), a regular
 * one among the notes. Events the cache keeps out of feeds (the account's private settings) never
 * reach it, so they are not listed.
 */
class KindEventsFeedFilter(
    val kind: Int,
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-kind" + kind

    override fun limit() = 50

    override fun feed(): List<Note> {
        val addressable = LocalCache.addressables.filterIntoSet(kind) { _, note -> accept(note) }
        val regular = LocalCache.notes.filterIntoSet { _, note -> accept(note) }
        return sort(addressable + regular)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { accept(it) }

    private fun accept(note: Note): Boolean = note.event?.kind == kind && !note.isHiddenFor(account.hiddenUsers.flow.value) && account.isAcceptable(note)

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
