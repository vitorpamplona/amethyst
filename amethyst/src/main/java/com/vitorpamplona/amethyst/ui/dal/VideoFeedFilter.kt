/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.commons.richtext.RichTextParser.Companion.isImageOrVideoUrl
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.SUPPORTED_VIDEO_FEED_MIME_TYPES_SET
import com.vitorpamplona.quartz.experimental.nip95.FileStorageHeaderEvent
import com.vitorpamplona.quartz.nip01Core.addressables.AddressableEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoMeta
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent

class VideoFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.settings.defaultStoriesFollowList.value

    override fun showHiddenKey(): Boolean =
        account.settings.defaultStoriesFollowList.value == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.settings.defaultStoriesFollowList.value == MuteListEvent.blockListFor(account.userProfile().pubkeyHex)

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        val notes =
            LocalCache.notes.filterIntoSet { _, it ->
                acceptableEvent(it, params)
            } +
                LocalCache.addressables.filterIntoSet { _, it ->
                    acceptableEvent(it, params)
                }

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val params = buildFilterParams(account)

        return collection.filterTo(HashSet()) { acceptableEvent(it, params) }
    }

    fun acceptableUrls(
        baseUrls: List<String>,
        mimeType: String?,
    ): Boolean {
        // we don't have an youtube player
        val urls = baseUrls.filter { !it.contains("youtu.be") && !it.contains("youtube.com") }

        val isSupportedMimeType = mimeType?.let { SUPPORTED_VIDEO_FEED_MIME_TYPES_SET.contains(it) } ?: false

        return urls.isNotEmpty() && (urls.any { isImageOrVideoUrl(it) } || isSupportedMimeType)
    }

    fun acceptableiMetas(iMetas: List<VideoMeta>): Boolean =
        iMetas.any {
            !it.url.contains("youtu.be") && (isImageOrVideoUrl(it.url) || (it.mimeType == null || SUPPORTED_VIDEO_FEED_MIME_TYPES_SET.contains(it.mimeType)))
        }

    fun acceptanceEvent(noteEvent: FileHeaderEvent) = acceptableUrls(noteEvent.urls(), noteEvent.mimeType())

    fun acceptanceEvent(noteEvent: VideoVerticalEvent) = acceptableiMetas(noteEvent.imetaTags())

    fun acceptanceEvent(noteEvent: VideoHorizontalEvent) = acceptableiMetas(noteEvent.imetaTags())

    fun acceptableEvent(
        note: Note,
        params: FilterByListParams,
    ): Boolean {
        val noteEvent = note.event

        if (noteEvent is AddressableEvent && note !is AddressableNote) {
            return false
        }

        return (
            (noteEvent is FileHeaderEvent && acceptanceEvent(noteEvent)) ||
                (noteEvent is VideoVerticalEvent && acceptanceEvent(noteEvent)) ||
                (noteEvent is VideoHorizontalEvent && acceptanceEvent(noteEvent)) ||
                (noteEvent is FileStorageHeaderEvent && noteEvent.isOneOf(SUPPORTED_VIDEO_FEED_MIME_TYPES_SET)) ||
                noteEvent is PictureEvent
        ) &&
            params.match(noteEvent) &&
            (params.isHiddenList || account.isAcceptable(note))
    }

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            userHex = account.userProfile().pubkeyHex,
            selectedListName = account.settings.defaultStoriesFollowList.value,
            followLists = account.liveStoriesFollowLists.value,
            hiddenUsers = account.flowHiddenUsers.value,
        )

    override fun sort(collection: Set<Note>): List<Note> = collection.sortedWith(DefaultFeedOrder)
}
