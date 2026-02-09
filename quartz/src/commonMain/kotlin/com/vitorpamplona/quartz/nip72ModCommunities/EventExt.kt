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
package com.vitorpamplona.quartz.nip72ModCommunities

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.any
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.tags.CommunityTag

fun Event.isACommunityPost(): Boolean =
    if (this is CommentEvent) {
        this.isCommunityScoped()
    } else {
        this.tags.any(CommunityTag::isTaggedWithKind, CommunityDefinitionEvent.KIND_STR)
    }

fun Event.isForCommunity(communityAddressId: String): Boolean =
    if (this is CommentEvent) {
        this.hasRootAddress(communityAddressId)
    } else {
        tags.any(CommunityTag::isTagged, communityAddressId)
    }

fun Event.communityAddress(): Address? =
    if (this is CommentEvent) {
        this.rootAddress().firstNotNullOfOrNull {
            if (it.kind == CommunityDefinitionEvent.KIND) it else null
        }
    } else {
        this.tags.fastFirstNotNullOfOrNull(CommunityTag::parseAddress)
    }

fun CommentEvent.isCommunityScoped() = hasRootScopeKind(CommunityDefinitionEvent.KIND_STR)

fun CommentEvent.communityScope() = this.tags.fastFirstNotNullOfOrNull(CommunityTag::parseAddress)

fun CommentEvent.communityScopes() = this.tags.mapNotNull(CommunityTag::parseAddress)
