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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.community_approved_posts
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * A NIP-72 post approval (kind 4550): the community it approves for (`a` to 34550), then the
 * approved post (`e`, or `a` for a replaceable approved as it changes), quoted within the row's
 * budget. Until a relay serves the post, the copy NIP-72 asks the moderator to embed in `content`
 * stands in, once its id and signature check out and it is the post the tags name; else a one-line
 * link marks the post that is still loading.
 */
@Composable
fun RenderPostApproval(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? CommunityPostApprovalEvent ?: return
    val communities = remember(event) { event.communityAddresses() }
    val approvedIds = remember(event) { event.approvedEvents() }
    // "Community `a` tags always begin with 34550": any other `a` is the approved post.
    val approvedAddresses = remember(event) { event.approvedAddresses().filter { it.kind != CommunityDefinitionEvent.KIND } }

    LaunchedEffect(event) { loadEmbeddedApprovedPost(event) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        communities.forEach { address ->
            key(address.toValue()) {
                LoadAddressableNote(address) { community ->
                    community?.let { RenderCommunity(baseNote = it, accountViewModel = accountViewModel, nav = nav) }
                }
            }
        }

        if (quotesLeft > 0 && (approvedIds.isNotEmpty() || approvedAddresses.isNotEmpty())) {
            Text(
                text = stringRes(id = Res.string.community_approved_posts),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )

            // A version approved by id is the one the moderator saw; an `a` alone approves the
            // replaceable as its author keeps editing it, so its current version is shown.
            approvedIds.forEach { id ->
                key(id) { KindCardQuoteEvent(id, quotesLeft, backgroundColor, accountViewModel, nav) }
            }
            if (approvedIds.isEmpty()) {
                approvedAddresses.forEach { address ->
                    key(address.toValue()) { KindCardQuoteAddress(address, quotesLeft, backgroundColor, accountViewModel, nav) }
                }
            }
        }
    }
}

/**
 * Puts the post [approval] embeds into the cache, so the quotes above can show it before a relay
 * serves it. The content is the moderator's (attacker-controllable) JSON, so the copy is used only
 * when it is the very post the approval's tags name and its id and signature hold, as for reposts;
 * the cache's deletion check still applies.
 */
private suspend fun loadEmbeddedApprovedPost(approval: CommunityPostApprovalEvent) {
    withContext(Dispatchers.IO) {
        val post = approval.containedPost() ?: return@withContext
        if (!isApprovedPost(approval, post)) return@withContext
        if (LocalCache.getNoteIfExists(post.id)?.event != null) return@withContext
        if (post.verify()) LocalCache.checkDeletionAndConsume(post, null, wasVerified = true)
    }
}

private fun isApprovedPost(
    approval: CommunityPostApprovalEvent,
    post: Event,
): Boolean {
    if (post.id in approval.approvedEvents()) return true
    if (post !is AddressableEvent) return false
    val address = post.addressTag()
    return approval.approvedAddresses().any { it.toValue() == address }
}
