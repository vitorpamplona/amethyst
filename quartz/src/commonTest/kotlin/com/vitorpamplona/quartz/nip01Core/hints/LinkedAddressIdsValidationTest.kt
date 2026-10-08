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
package com.vitorpamplona.quartz.nip01Core.hints

import com.vitorpamplona.quartz.experimental.kanban.card.KanbanCardEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList.FavoriteFollowSetsListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.pictureCurationSet.PictureCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `linkedAddressIds()` feeds the app's address lookups, so it must only carry well-formed
 * `<kind>:<64-hex pubkey>:<d>` coordinates — never the raw value of an `a`/`q` tag.
 */
class LinkedAddressIdsValidationTest {
    private val pk = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val valid = "30023:$pk:article"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    /** Garbage that the old raw `tag[1]` parsers passed straight through. */
    private val garbage = listOf("garbage", "30023:not-a-pubkey:article", "kind:$pk:d", "123456:$pk:d")

    private fun tagsWith(name: String) = (garbage.map { arrayOf(name, it) } + listOf(arrayOf(name, valid))).toTypedArray()

    private val aProviders: List<Pair<String, (Array<Array<String>>) -> Event>> =
        listOf(
            "MusicPlaylist" to { t -> MusicPlaylistEvent(id, pk, 1, t, "", sig) },
            "AdminCommand" to { t -> AdminCommandEvent(id, pk, 1, t, "", sig) },
            "SoftwareApplication" to { t -> SoftwareApplicationEvent(id, pk, 1, t, "", sig) },
            "UserStatus" to { t -> UserStatusEvent(id, pk, 1, t, "", sig) },
            "MintRecommendation" to { t -> MintRecommendationEvent(id, pk, 1, t, "", sig) },
            "AppDefinition" to { t -> AppDefinitionEvent(id, pk, 1, t, "", sig) },
            "ChatMessage" to { t -> ChatMessageEvent(id, pk, 1, t, "", sig) },
            "FavoriteAlgoFeeds" to { t -> FavoriteAlgoFeedsListEvent(id, pk, 1, t, "", sig) },
            "FavoriteFollowSets" to { t -> FavoriteFollowSetsListEvent(id, pk, 1, t, "", sig) },
            "InterestList" to { t -> InterestListEvent(id, pk, 1, t, "", sig) },
            "PictureCurationSet" to { t -> PictureCurationSetEvent(id, pk, 1, t, "", sig) },
            "FavoriteRelayList" to { t -> FavoriteRelayListEvent(id, pk, 1, t, "", sig) },
            // The valid 30023 address is not a board, so it reads as the tracked event.
            "KanbanCard" to { t -> KanbanCardEvent(id, pk, 1, t, "", sig) },
        )

    private val qProviders: List<Pair<String, (Array<Array<String>>) -> Event>> =
        listOf(
            "ChatMessage" to { t -> ChatMessageEvent(id, pk, 1, t, "", sig) },
            "Torrent" to { t -> TorrentEvent(id, pk, 1, t, "", sig) },
            "Picture" to { t -> PictureEvent(id, pk, 1, t, "", sig) },
            "Thread" to { t -> ThreadEvent(id, pk, 1, t, "", sig) },
            "Poll" to { t -> PollEvent(id, pk, 1, t, "", sig) },
            "PublicMessage" to { t -> PublicMessageEvent(id, pk, 1, t, "", sig) },
            "Chat" to { t -> ChatEvent(id, pk, 1, t, "", sig) },
            "Classifieds" to { t -> ClassifiedsEvent(id, pk, 1, t, "", sig) },
        )

    private fun linked(event: Event) = (event as AddressHintProvider).linkedAddressIds()

    @Test
    fun malformedATagsAreNotLinked() {
        aProviders.forEach { (name, build) -> assertEquals(listOf(valid), linked(build(tagsWith("a"))), name) }
    }

    @Test
    fun malformedQTagsAreNotLinked() {
        qProviders.forEach { (name, build) -> assertEquals(listOf(valid), linked(build(tagsWith("q"))), name) }
    }
}
