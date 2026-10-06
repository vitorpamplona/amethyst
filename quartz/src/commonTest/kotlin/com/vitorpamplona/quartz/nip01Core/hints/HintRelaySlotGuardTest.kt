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

import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectMemberTag
import com.vitorpamplona.quartz.experimental.interactiveStories.tags.RootSceneTag
import com.vitorpamplona.quartz.experimental.interactiveStories.tags.StoryOptionTag
import com.vitorpamplona.quartz.experimental.publications.tags.SourceAddressTag
import com.vitorpamplona.quartz.experimental.publications.tags.SourceEventTag
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.EventTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip64Chess.baseEvent.tags.OpponentTag
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.tags.ChallengeEventTag
import com.vitorpamplona.quartz.nip71Video.tags.TextTrackTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.ClientTag
import com.vitorpamplona.quartz.nip90Dvms.tags.InputTag
import com.vitorpamplona.quartz.nipA4PublicMessages.tags.ReceiverTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.ReplyAuthorTag as VoiceReplyAuthorTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.ReplyEventTag as VoiceReplyEventTag

/**
 * A tag's relay SLOT often holds something else — a pubkey a builder shifted left when it
 * dropped a null relay, or a role/label (`inspired-by`, `github`, `root`). The forgiving
 * [RelayUrlNormalizer.normalizeOrNull] would turn each of those into `wss://<word>/`, a fake
 * relay in the hint index and the broadcast set. Every hint parser must decline them, while still
 * completing a schemeless host (`relay.damus.io`) a publisher forgot the scheme on.
 */
class HintRelaySlotGuardTest {
    private val pk = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val id = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val addr = "30023:$pk:article"
    private val relay = "wss://relay.damus.io/"

    /** Values that sit in relay slots in the wild and are not relays. */
    private val notRelays =
        listOf(
            pk,
            "inspired-by",
            "github",
            "root",
            "reply",
            // a zap weight, a d-tag with a dot inside an address, and free text
            "1.0",
            "30023:$pk:my.article",
            "relay damus.io",
        )

    /** Schemeless hosts a publisher forgot the scheme on: these ARE relays. */
    private val bareHosts = listOf("relay.damus.io", "nos.lol", "relay.example.com:7777", "relay.damus.io/")

    @Test
    fun normalizeHintOrNullCompletesOnlyHostLookingValues() {
        notRelays.forEach { assertNull(RelayUrlNormalizer.normalizeHintOrNull(it), it) }
        assertNull(RelayUrlNormalizer.normalizeHintOrNull(null))
        assertNull(RelayUrlNormalizer.normalizeHintOrNull(""))
        assertNull(RelayUrlNormalizer.normalizeHintOrNull("wss://"))
        // The forgiving normalizer is what made these fake relays.
        assertEquals("wss://inspired-by/", RelayUrlNormalizer.normalizeOrNull("inspired-by")?.url)

        assertEquals(relay, RelayUrlNormalizer.normalizeHintOrNull("wss://relay.damus.io")?.url)
        assertNotNull(RelayUrlNormalizer.normalizeHintOrNull("ws://relay.example.com"))

        bareHosts.forEach { assertNotNull(RelayUrlNormalizer.normalizeHintOrNull(it), it) }
        assertEquals(relay, RelayUrlNormalizer.normalizeHintOrNull("relay.damus.io")?.url)
    }

    /** Each case builds a tag around the given relay slot value and runs one hint parser on it. */
    private val cases: List<Pair<String, (String) -> Any?>> =
        listOf(
            "chess challenge e" to { r -> ChallengeEventTag.parseAsHint(arrayOf("e", id, r)) },
            "chess opponent p" to { r -> OpponentTag.parseAsHint(arrayOf("p", pk, r)) },
            "nip22 E" to { r -> RootEventTag.parseAsHint(arrayOf("E", id, r)) },
            "nip22 e" to { r -> ReplyEventTag.parseAsHint(arrayOf("e", id, r)) },
            "nip22 P" to { r -> RootAuthorTag.parseAsHint(arrayOf("P", pk, r)) },
            "nip22 p" to { r -> ReplyAuthorTag.parseAsHint(arrayOf("p", pk, r)) },
            "nip22 A" to { r -> RootAddressTag.parseAsHint(arrayOf("A", addr, r)) },
            "nip22 a" to { r -> ReplyAddressTag.parseAsHint(arrayOf("a", addr, r)) },
            "voice e" to { r -> VoiceReplyEventTag.parseAsHint(arrayOf("e", id, r)) },
            "voice p" to { r -> VoiceReplyAuthorTag.parseAsHint(arrayOf("p", pk, r)) },
            "public message p" to { r -> ReceiverTag.parseAsHint(arrayOf("p", pk, r)) },
            "nip51 p" to { r -> UserTag.parseAsHint(arrayOf("p", pk, r)) },
            "nip51 e" to { r -> EventTag.parseAsHint(arrayOf("e", id, r)) },
            "nip51 a" to { r -> AddressBookmark.parseAsHint(arrayOf("a", addr, r)) },
            "a" to { r -> ATag.parseAsHint(arrayOf("a", addr, r)) },
            "nip85 provider" to { r -> ServiceProviderTag.parseAsHint(arrayOf("30382:rank", pk, r)) },
            "project member" to { r -> ProjectMemberTag.parseAsHint(arrayOf("a", "30617:$pk:repo", r)) },
            "story root" to { r -> RootSceneTag.parseAsHint(arrayOf("A", addr, r)) },
            "story option" to { r -> StoryOptionTag.parseAsHint(arrayOf("option", "Go left", addr, r)) },
            "publication source A" to { r -> SourceAddressTag.parseAsHint(arrayOf("A", addr, r)) },
            "publication source E" to { r -> SourceEventTag.parseAsHint(arrayOf("E", id, r)) },
            "dvm input" to { r -> InputTag.parseEventAsHint(arrayOf("i", id, "event", r)) },
            "client" to { r -> ClientTag.parseAddressAsHint(arrayOf("client", "app", "31990:$pk:app", r)) },
            "text track" to { r -> TextTrackTag.parseAddressAsHint(arrayOf("text-track", "30383:$pk:subs", r)) },
        )

    @Test
    fun everyHintParserDeclinesWhatIsNotARelay() {
        cases.forEach { (name, parse) ->
            notRelays.forEach { slot -> assertNull(parse(slot), "$name must not hint '$slot'") }
        }
    }

    @Test
    fun everyHintParserStillHintsARealRelay() {
        cases.forEach { (name, parse) -> assertNotNull(parse(relay), name) }
    }

    @Test
    fun everyHintParserCompletesABareHost() {
        cases.forEach { (name, parse) ->
            bareHosts.forEach { slot -> assertNotNull(parse(slot), "$name must hint '$slot'") }
        }
    }
}
