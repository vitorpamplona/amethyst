/**
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
package com.vitorpamplona.amethyst.model.topNavFeeds.noteBased

import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedFlowsType
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.aroundMe.LocationTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.hashtag.HashtagTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.interests.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.locations.GeohashListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transformLatest

class NoteFeedFlow(
    val metadataFlow: StateFlow<NoteState?>,
    val signer: NostrSigner,
    val allFollowRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedFlowsType {
    fun process(noteEvent: Event): IFeedTopNavFilter =
        when (noteEvent) {
            is PeopleListEvent -> {
                if (noteEvent.dTag() == PeopleListEvent.Companion.BLOCK_LIST_D_TAG) {
                    MutedAuthorsByOutboxTopNavFilter(noteEvent.publicAndCachedPrivateUsersAndWords().users)
                } else {
                    AuthorsByOutboxTopNavFilter(noteEvent.publicAndCachedPrivateUsersAndWords().users)
                }
            }
            is MuteListEvent -> {
                MutedAuthorsByOutboxTopNavFilter(noteEvent.publicAndCachedUsersAndWords().users)
            }
            is FollowListEvent -> {
                AuthorsByOutboxTopNavFilter(noteEvent.pubKeys().toSet())
            }
            is CommunityListEvent -> {
                AllCommunitiesTopNavFilter(noteEvent.publicAndCachedPrivateCommunityIds().toSet())
            }
            is HashtagListEvent -> {
                HashtagTopNavFilter(noteEvent.publicAndCachedPrivateHashtags(), allFollowRelays)
            }
            is GeohashListEvent -> {
                LocationTopNavFilter(noteEvent.publicAndCachedPrivateGeohash(), allFollowRelays)
            }
            is CommunityDefinitionEvent -> {
                SingleCommunityTopNavFilter(
                    community = noteEvent.addressTag(),
                    authors = noteEvent.moderatorKeys().toSet().ifEmpty { null },
                    relays = noteEvent.relayUrls().toSet(),
                )
            }
            else -> AuthorsByOutboxTopNavFilter(emptySet())
        }

    suspend fun FlowCollector<IFeedTopNavFilter>.process(noteEvent: Event) {
        when (noteEvent) {
            is PeopleListEvent -> {
                if (noteEvent.dTag() == PeopleListEvent.Companion.BLOCK_LIST_D_TAG) {
                    emit(MutedAuthorsByOutboxTopNavFilter(noteEvent.publicAndCachedPrivateUsersAndWords().users))

                    noteEvent.publicAndPrivateUsersAndWords(signer)?.let {
                        emit(MutedAuthorsByOutboxTopNavFilter(it.users))
                    }
                } else {
                    emit(AuthorsByOutboxTopNavFilter(noteEvent.publicAndCachedPrivateUsersAndWords().users))

                    noteEvent.publicAndPrivateUsersAndWords(signer)?.let {
                        emit(AuthorsByOutboxTopNavFilter(it.users))
                    }
                }
            }
            is MuteListEvent -> {
                emit(MutedAuthorsByOutboxTopNavFilter(noteEvent.publicAndCachedUsersAndWords().users))

                noteEvent.publicAndPrivateUsersAndWords(signer)?.let {
                    emit(MutedAuthorsByOutboxTopNavFilter(it.users))
                }
            }
            is FollowListEvent -> {
                emit(AuthorsByOutboxTopNavFilter(noteEvent.pubKeys().toSet()))
            }
            is CommunityListEvent -> {
                emit(AllCommunitiesTopNavFilter(noteEvent.publicCommunityIds().toSet()))

                noteEvent.publicAndPrivateCommunities(signer)?.let {
                    val communities = it.map { it.addressId }.toSet()
                    emit(AllCommunitiesTopNavFilter(communities))
                }
            }
            is HashtagListEvent -> {
                emit(HashtagTopNavFilter(noteEvent.publicHashtags().toSet(), allFollowRelays))

                noteEvent.publicAndPrivateHashtag(signer)?.let {
                    emit(HashtagTopNavFilter(it, allFollowRelays))
                }
            }
            is GeohashListEvent -> {
                emit(LocationTopNavFilter(noteEvent.publicGeohashes().toSet(), allFollowRelays))

                noteEvent.publicAndPrivateGeohash(signer)?.let {
                    emit(LocationTopNavFilter(it, allFollowRelays))
                }
            }
            is CommunityDefinitionEvent -> {
                emit(
                    SingleCommunityTopNavFilter(
                        community = noteEvent.addressTag(),
                        authors = noteEvent.moderatorKeys().toSet().ifEmpty { null },
                        relays = noteEvent.relayUrls().toSet(),
                    ),
                )
            }
            else ->
                emit(
                    AuthorsByOutboxTopNavFilter(emptySet()),
                )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun flow() =
        metadataFlow.transformLatest { noteState ->
            val noteEvent = noteState?.note?.event
            if (noteEvent == null) {
                AuthorsByOutboxTopNavFilter(emptySet())
            } else {
                process(noteEvent)
            }
        }

    override fun startValue(): IFeedTopNavFilter {
        val noteEvent = metadataFlow.value?.note?.event
        if (noteEvent == null) {
            return AuthorsByOutboxTopNavFilter(emptySet())
        } else {
            return process(noteEvent)
        }
    }

    override suspend fun startValue(collector: FlowCollector<IFeedTopNavFilter>) {
        val noteEvent = metadataFlow.value?.note?.event
        if (noteEvent == null) {
            collector.emit(AuthorsByOutboxTopNavFilter(emptySet()))
        } else {
            collector.process(noteEvent)
        }
    }
}
