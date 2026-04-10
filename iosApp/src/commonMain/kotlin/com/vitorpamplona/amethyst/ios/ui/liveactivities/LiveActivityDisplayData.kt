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
package com.vitorpamplona.amethyst.ios.ui.liveactivities

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag

/**
 * Display data for a live activity card (NIP-53, kind 30311).
 */
data class LiveActivityDisplayData(
    val id: String,
    val addressId: String,
    val hostPubKeyHex: String,
    val hostDisplayName: String,
    val hostProfilePicture: String?,
    val title: String,
    val summary: String?,
    val image: String?,
    val status: StatusTag.STATUS?,
    val streamingUrl: String?,
    val currentParticipants: Int?,
    val totalParticipants: Int?,
    val participantCount: Int,
    val starts: Long?,
    val ends: Long?,
    val createdAt: Long,
)

/**
 * Extension to convert a Note containing a LiveActivitiesEvent to display data.
 */
fun Note.toLiveActivityDisplayData(cache: IosLocalCache? = null): LiveActivityDisplayData? {
    val event = this.event as? LiveActivitiesEvent ?: return null
    val hostPubKey = event.host()?.pubKey ?: event.pubKey
    val hostUser = cache?.getUserIfExists(hostPubKey)

    return LiveActivityDisplayData(
        id = event.id,
        addressId = event.addressTag(),
        hostPubKeyHex = hostPubKey,
        hostDisplayName = hostUser?.toBestDisplayName() ?: hostPubKey.take(16) + "...",
        hostProfilePicture = hostUser?.profilePicture(),
        title = event.title() ?: "Untitled Live Activity",
        summary = event.summary(),
        image = event.image(),
        status = event.status(),
        streamingUrl = event.streaming(),
        currentParticipants = event.currentParticipants(),
        totalParticipants = event.totalParticipants(),
        participantCount = event.participantKeys().size,
        starts = event.starts(),
        ends = event.ends(),
        createdAt = event.createdAt,
    )
}
