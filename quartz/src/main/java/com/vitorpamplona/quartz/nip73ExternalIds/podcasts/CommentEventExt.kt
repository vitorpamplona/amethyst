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
package com.vitorpamplona.quartz.nip73ExternalIds.podcasts

import com.vitorpamplona.quartz.nip22Comments.CommentEvent

fun CommentEvent.isPodcastEpisodeScoped() = hasScopeKind(PodcastEpisodeId.KIND)

fun CommentEvent.podcastEpisodeScope() = firstScopeValue(PodcastEpisodeId::parse)

fun CommentEvent.podcastEpisodeScopes() = scopeValues(PodcastEpisodeId::parse)

fun CommentEvent.isPodcastFeedScoped() = hasScopeKind(PodcastFeedId.KIND)

fun CommentEvent.podcastFeedScope() = firstScopeValue(PodcastFeedId::parse)

fun CommentEvent.podcastFeedScopes() = scopeValues(PodcastFeedId::parse)

fun CommentEvent.isPodcastPublisherScoped() = hasScopeKind(PodcastPublisherId.KIND)

fun CommentEvent.podcastPublisherScope() = firstScopeValue(PodcastPublisherId::parse)

fun CommentEvent.podcastPublisherScopes() = scopeValues(PodcastPublisherId::parse)
