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
package com.vitorpamplona.amethyst.commons.buzz.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_agent_label
import com.vitorpamplona.amethyst.commons.resources.buzz_agent_managed_by
import com.vitorpamplona.amethyst.commons.resources.buzz_agent_managed_by_you
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.agentProfiles.AgentProfileEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Whether [author] has published a Buzz agent profile (kind 10100). The profile is requested with
 * every user's kind 0, so this only watches the cache; it never asks a relay itself.
 */
@Composable
fun rememberHasBuzzAgentProfile(author: User): Boolean {
    val note = remember(author) { LocalCache.getOrCreateAddressableNote(AgentProfileEvent.createAddress(author.pubkeyHex)) }
    val hasProfile by produceState(note.event is AgentProfileEvent, note) {
        note
            .flow()
            .metadata.stateFlow
            .collect { value = note.event is AgentProfileEvent }
    }
    return hasProfile
}

/**
 * "Agent · managed by Alice" beside an agent's name, the way Buzz labels agents. [author] counts as
 * an agent when it published a kind-10100 agent profile or its kind 0 names an [owner] through a
 * verified NIP-OA `auth` tag; the owner part appears only for the latter. Draws nothing for a person.
 */
@Composable
fun BuzzAgentLabel(
    author: User,
    owner: HexKey?,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val hasAgentProfile = rememberHasBuzzAgentProfile(author)
    if (owner == null && !hasAgentProfile) return

    val agent = stringRes(Res.string.buzz_agent_label)
    val text =
        when (owner) {
            null -> agent
            accountViewModel.userProfile().pubkeyHex -> "$agent · ${stringRes(Res.string.buzz_agent_managed_by_you)}"
            else -> "$agent · ${stringRes(Res.string.buzz_agent_managed_by, observeUserNameByHex(owner, accountViewModel))}"
        }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
