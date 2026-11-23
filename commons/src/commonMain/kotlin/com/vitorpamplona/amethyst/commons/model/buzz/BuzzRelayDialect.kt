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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.buzz.workspace.BuzzChannelLabels
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.fastFirstOrNull
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks which relays speak the **Buzz dialect** of NIP-29 — `block/buzz` workspace
 * relays whose channel timelines carry Buzz-custom kinds (stream messages 40002,
 * edits 40003, diffs 40008, system rows 40099, canvas 40100, forum 45001/45003,
 * agent jobs 43001-43006, huddle cards 48100-48103) alongside plain kind-9 chat.
 *
 * Detection sources, in order of arrival:
 * 1. **Event shape** (implemented): the first Buzz-only channel kind consumed from a
 *    relay marks it (`LocalCache` calls [mark] before materializing the channel). A
 *    vanilla NIP-29 relay never serves those kinds, so there are no false positives.
 * 2. **NIP-11**: a relay whose info document names Buzz as its `software`
 *    ([looksLikeBuzzRelay]); `amy` reads it before its first group command, which is
 *    all a CLI run knows of the relay.
 *
 * The registry decides which channel type `LocalCache` materializes for a group
 * (`BuzzWorkspaceChannel` vs plain `RelayGroupChannel`) and which timeline kinds the
 * group-chat REQ builders put on the wire for that relay. Marks only ever ADD
 * capability: un-marking is never needed because a marked relay provably served a
 * Buzz kind, and extra requested kinds are harmless on any relay.
 *
 * Like `LocalCache`, this is a process-wide singleton (one copy per Android process).
 */
object BuzzRelayDialect {
    private val buzzRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

    /** Relays known to speak the Buzz dialect; UI can collect this for affordances. */
    val flow: StateFlow<Set<NormalizedRelayUrl>> = buzzRelays

    fun isBuzz(relay: NormalizedRelayUrl): Boolean = relay in buzzRelays.value

    /** Marks [relay] as Buzz. Returns true when this call changed the state. */
    fun mark(relay: NormalizedRelayUrl): Boolean {
        while (true) {
            val current = buzzRelays.value
            if (relay in current) return false
            if (buzzRelays.compareAndSet(current, current + relay)) return true
        }
    }

    /**
     * Whether [event] (a 39000/39001/39002) has the shape of a Buzz relay's group state, for a
     * client that has not otherwise learnt the relay's dialect (the `amy` CLI); the app goes by
     * [isBuzz].
     *
     * A current Buzz relay stamps the channel type as the first `t` tag and the creator as `P`
     * ([BuzzChannelLabels.looksLikeBuzzGroupState]). One from before that (buzz `8bb49a1b6`) has the
     * channel-type `t` alone, so that shape counts too when the group's `d` is a UUID, which every
     * Buzz channel id is and NIP-29 group ids rarely are: a NIP-29 hashtag `#stream` on a group named
     * by a short id stays a NIP-29 group.
     */
    fun looksLikeBuzzGroupState(event: Event?): Boolean {
        if (event == null) return false
        if (BuzzChannelLabels.looksLikeBuzzGroupState(event)) return true
        val firstTopic = event.tags.fastFirstOrNull { it.size > 1 && it[0] == "t" }?.get(1) ?: return false
        if (firstTopic !in BuzzChannelLabels.RESERVED) return false
        val groupId = event.tags.fastFirstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: return false
        return UUID.matches(groupId)
    }

    private val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    /**
     * Whether [info] is a Buzz relay's NIP-11 document: Buzz names its repository as `software`
     * (`https://github.com/block/buzz`, `buzz-relay/src/nip11.rs`). When it is, [relay] is marked.
     */
    fun looksLikeBuzzRelay(
        relay: NormalizedRelayUrl,
        info: Nip11RelayInformation?,
    ): Boolean {
        val software = info?.software?.trimEnd('/')?.removeSuffix(".git") ?: return false
        if (!software.endsWith(BUZZ_SOFTWARE_SUFFIX, ignoreCase = true)) return false
        mark(relay)
        return true
    }

    private const val BUZZ_SOFTWARE_SUFFIX = "github.com/block/buzz"

    /** Test-only: clears all marks so unit tests don't leak state into each other. */
    fun clearForTesting() {
        buzzRelays.value = emptySet()
    }
}
