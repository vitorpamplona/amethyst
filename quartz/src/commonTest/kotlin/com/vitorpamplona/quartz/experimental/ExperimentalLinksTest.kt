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
package com.vitorpamplona.quartz.experimental

import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashPresenceEvent
import com.vitorpamplona.quartz.experimental.citations.ExternalCitationEvent
import com.vitorpamplona.quartz.experimental.citations.HardcopyCitationEvent
import com.vitorpamplona.quartz.experimental.clink.debits.DebitEvent
import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.RoadEventConfirmationEvent
import com.vitorpamplona.quartz.experimental.roadstr.report.RoadEventReportEvent
import com.vitorpamplona.quartz.experimental.videoCollaboration.VideoCollaborationEvent
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class ExperimentalLinksTest {
    private val me = "0".repeat(64)
    private val alice = "1".repeat(64)
    private val bob = "2".repeat(64)
    private val note1 = "e1".repeat(32)
    private val note2 = "e2".repeat(32)
    private val article = "30023:$alice:post"

    private fun tags(vararg tags: Array<String>) = arrayOf(*tags)

    @Test
    fun fundraiserLinksItsHashtagsOnly() {
        val tags = tags(arrayOf("d", "roof"), arrayOf("t", "Bitcoin"), arrayOf("w", "bc1qexample"), arrayOf("goal", "100000"))
        assertEquals(
            listOf(Link(Relation.HASHTAG, LinkTarget.Tag("t", "bitcoin"), "t")),
            FundraiserEvent(me, me, 0, tags, "", me).links(),
        )
    }

    @Test
    fun attestationLinksItsAssertionAndTheRequestItAnswers() {
        val request = "31872:$bob:req"
        val tags =
            tags(
                arrayOf("d", "att"),
                arrayOf("e", note1, "", alice),
                arrayOf("a", article),
                arrayOf("request", request),
                // not a kind-31872 request: dropped
                arrayOf("request", "30000:$bob:list"),
                arrayOf("s", "valid"),
            )
        assertEquals(
            listOf(
                Link(Relation.ASSERTION, LinkTarget.Event(note1), "e"),
                Link(Relation.ASSERTION, LinkTarget.Address(article), "a"),
                Link(Relation.REQUEST, LinkTarget.Address(request), "request"),
            ),
            AttestationEvent(me, me, 0, tags, "", me).links(),
        )
    }

    @Test
    fun attestationRequestLinksItsAttestors() {
        val tags = tags(arrayOf("d", "req"), arrayOf("a", article), arrayOf("p", alice), arrayOf("p", bob), arrayOf("cashu_token", "cashuA"))
        assertEquals(
            listOf(
                Link(Relation.ASSERTION, LinkTarget.Address(article), "a"),
                Link(Relation.ATTESTOR, LinkTarget.User(alice), "p"),
                Link(Relation.ATTESTOR, LinkTarget.User(bob), "p"),
            ),
            AttestationRequestEvent(me, me, 0, tags, "", me).links(),
        )
    }

    @Test
    fun attestorProficiencyLinksItsKinds() {
        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
                Link(Relation.TAG, LinkTarget.Tag("k", "30023"), "k"),
            ),
            AttestorProficiencyEvent(me, me, 0, tags(arrayOf("k", "1"), arrayOf("k", "30023")), "", me).links(),
        )
    }

    @Test
    fun attestorRecommendationRecommendsThePubkeyInItsD() {
        assertEquals(
            listOf(
                Link(Relation.RECOMMENDED, LinkTarget.User(alice), "d"),
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
            ),
            AttestorRecommendationEvent(me, me, 0, tags(arrayOf("d", alice), arrayOf("k", "1")), "", me).links(),
        )
        // a `d` that is not a pubkey recommends nobody
        assertEquals(
            emptyList<Link>(),
            AttestorRecommendationEvent(me, me, 0, tags(arrayOf("d", "someone")), "", me).links(),
        )
    }

    @Test
    fun audioTrackCarriesEachParticipantsRole() {
        val tags = tags(arrayOf("d", "track"), arrayOf("p", alice, "", "Host"), arrayOf("p", bob), arrayOf("c", "Podcast"))
        assertEquals(
            listOf(
                Link(Relation.PARTICIPANT, LinkTarget.User(alice), "p", mapOf("role" to "Host")),
                Link(Relation.PARTICIPANT, LinkTarget.User(bob), "p"),
            ),
            AudioTrackEvent(me, me, 0, tags, "", me).links(),
        )
    }

    @Test
    fun birdstarLinksSpeciesAndPlaces() {
        val gallinule = "https://www.wikidata.org/entity/Q27074644"
        val oriole = "https://www.wikidata.org/entity/Q805774"
        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("i", gallinule), "i"),
                Link(Relation.TAG, LinkTarget.Tag("g", "dhwm"), "g"),
            ),
            BirdDetectionEvent(me, me, 0, tags(arrayOf("n", "Porphyrio martinica"), arrayOf("i", gallinule), arrayOf("g", "dhwm")), "", me).links(),
        )
        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("i", gallinule), "i"),
                Link(Relation.TAG, LinkTarget.Tag("i", oriole), "i"),
            ),
            BirdexEvent(me, me, 0, tags(arrayOf("i", gallinule), arrayOf("n", "Porphyrio martinica"), arrayOf("n", "Icterus galbula"), arrayOf("i", oriole)), "", me).links(),
        )
    }

    @Test
    fun geohashChannelsLinkTheirCell() {
        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pruy"), "g"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "teleport"), "t"),
            ),
            GeohashChatEvent(me, me, 0, tags(arrayOf("g", "u4pruy"), arrayOf("n", "nick"), arrayOf("t", "teleport")), "hi", me).links(),
        )
        assertEquals(
            listOf(Link(Relation.TAG, LinkTarget.Tag("g", "u4pr"), "g")),
            GeohashPresenceEvent(me, me, 0, tags(arrayOf("g", "u4pr")), "", me).links(),
        )
    }

    @Test
    fun citationsLinkTheirTimestampAndPlace() {
        assertEquals(
            listOf(
                Link(Relation.OPEN_TIMESTAMP, LinkTarget.Event(note1), "open_timestamp"),
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pr"), "g"),
            ),
            ExternalCitationEvent(me, me, 0, tags(arrayOf("u", "https://example.com/a"), arrayOf("open_timestamp", note1), arrayOf("g", "u4pr")), "", me).links(),
        )
        assertEquals(
            listOf(Link(Relation.TAG, LinkTarget.Tag("g", "u4pr"), "g")),
            HardcopyCitationEvent(me, me, 0, tags(arrayOf("title", "A book"), arrayOf("doi", "10.1/x"), arrayOf("g", "u4pr")), "", me).links(),
        )
    }

    @Test
    fun clinkMessagesLinkTheirCounterpartyAndRequest() {
        val tags = tags(arrayOf("p", alice), arrayOf("e", note1), arrayOf("clink_version", "1"))
        val expected =
            listOf(
                Link(Relation.RECIPIENT, LinkTarget.User(alice), "p"),
                Link(Relation.REQUEST, LinkTarget.Event(note1), "e"),
            )
        assertEquals(expected, OfferEvent(me, me, 0, tags, "", me).links())
        assertEquals(expected, DebitEvent(me, me, 0, tags, "", me).links())
    }

    @Test
    fun textNoteModificationLinksTheFirstEditedNoteAndItsAuthor() {
        val tags = tags(arrayOf("e", note1), arrayOf("e", note2), arrayOf("p", alice), arrayOf("summary", "typo"))
        assertEquals(
            listOf(
                Link(Relation.EDITED, LinkTarget.Event(note1), "e"),
                Link(Relation.EDITED_AUTHOR, LinkTarget.User(alice), "p"),
            ),
            TextNoteModificationEvent(me, me, 0, tags, "fixed", me).links(),
        )
    }

    @Test
    fun workoutLinksTemplatesButNotRunstrVerbs() {
        val squat = "33401:$alice:squat"
        val template = "33402:$alice:leg-day"
        val tags =
            tags(
                arrayOf("exercise", squat, "wss://relay.example/", "100", "5"),
                arrayOf("exercise", "running"),
                arrayOf("template", template, "wss://relay.example/"),
                arrayOf("t", "Fitness"),
            )
        assertEquals(
            listOf(
                Link(Relation.EXERCISE, LinkTarget.Address(squat), "exercise"),
                Link(Relation.TEMPLATE, LinkTarget.Address(template), "template"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "fitness"), "t"),
            ),
            WorkoutRecordEvent(me, me, 0, tags, "", me).links(),
        )
    }

    @Test
    fun adminCommandSplitsKickFromMute() {
        val room = "30312:$alice:room"
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Address(room), "a"),
                Link(Relation.KICKED, LinkTarget.User(bob), "p"),
            ),
            AdminCommandEvent(me, me, 0, tags(arrayOf("a", room), arrayOf("p", bob), arrayOf("action", "kick")), "", me).links(),
        )
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Address(room), "a"),
                Link(Relation.CHANNEL_MUTED, LinkTarget.User(bob), "p"),
            ),
            AdminCommandEvent(me, me, 0, tags(arrayOf("a", room), arrayOf("p", bob), arrayOf("action", "mute")), "", me).links(),
        )
        // an unknown verb says nothing about its target
        assertEquals(
            listOf(Link(Relation.ROOT, LinkTarget.Address(room), "a")),
            AdminCommandEvent(me, me, 0, tags(arrayOf("a", room), arrayOf("p", bob), arrayOf("action", "ban")), "", me).links(),
        )
    }

    @Test
    fun softwareAppsLinkReleasesAndAssetsTheirApp() {
        val release = "30063:$alice:com.example@1.0"
        assertEquals(
            listOf(
                Link(Relation.RELEASE, LinkTarget.Address(release), "a"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "nostr"), "t"),
            ),
            SoftwareApplicationEvent(me, me, 0, tags(arrayOf("d", "com.example"), arrayOf("a", release), arrayOf("t", "Nostr")), "", me).links(),
        )
        assertEquals(
            listOf(Link(Relation.TAG, LinkTarget.Tag("i", "com.example"), "i")),
            SoftwareAssetEvent(me, me, 0, tags(arrayOf("i", "com.example"), arrayOf("x", "abc")), "", me).links(),
        )
    }

    @Test
    fun fileHeaderLinksItsDataEvents() {
        assertEquals(
            listOf(Link(Relation.FILE_DATA, LinkTarget.Event(note1), "e")),
            FileStorageHeaderEvent(me, me, 0, tags(arrayOf("e", note1, "", alice), arrayOf("m", "image/png")), "", me).links(),
        )
    }

    @Test
    fun wakeUpLinksWhatItIsAboutNotARecipient() {
        assertEquals(
            listOf(
                Link(Relation.ABOUT, LinkTarget.Event(note1), "e"),
                Link(Relation.ABOUT_AUTHOR, LinkTarget.User(alice), "p"),
                Link(Relation.TAG, LinkTarget.Tag("k", "7"), "k"),
            ),
            WakeUpEvent(me, me, 0, tags(arrayOf("e", note1), arrayOf("p", alice), arrayOf("k", "7")), "", me).links(),
        )
    }

    @Test
    fun profileGalleryLinksItsSource() {
        assertEquals(
            listOf(Link(Relation.SOURCE, LinkTarget.Event(note1), "e")),
            ProfileGalleryEntryEvent(me, me, 0, tags(arrayOf("url", "https://img.example/a.png"), arrayOf("e", note1)), "", me).links(),
        )
    }

    @Test
    fun roadstrLinksReportsAndCells() {
        assertEquals(
            listOf(
                Link(Relation.CONFIRMED, LinkTarget.Event(note1), "e", mapOf("status" to "no_longer_there")),
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pr"), "g"),
            ),
            RoadEventConfirmationEvent(me, me, 0, tags(arrayOf("e", note1, "", alice), arrayOf("status", "no_longer_there"), arrayOf("g", "u4pr")), "", me).links(),
        )
        assertEquals(
            listOf(
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "police"), "t"),
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pr"), "g"),
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pru"), "g"),
            ),
            RoadEventReportEvent(me, me, 0, tags(arrayOf("t", "Police"), arrayOf("g", "u4pr"), arrayOf("g", "u4pru"), arrayOf("lat", "1.0")), "", me).links(),
        )
    }

    @Test
    fun videoCollaborationCarriesTheAnswer() {
        val video = "34236:$alice:clip"
        val declined = mapOf("status" to "declined", "role" to "Director")
        assertEquals(
            listOf(
                Link(Relation.COLLABORATED, LinkTarget.Address(video), "a", declined),
                Link(Relation.COLLABORATED_AUTHOR, LinkTarget.User(alice), "p", declined),
            ),
            VideoCollaborationEvent(
                me,
                me,
                0,
                tags(arrayOf("d", video), arrayOf("a", video), arrayOf("p", alice), arrayOf("role", "Director"), arrayOf("status", "declined")),
                "",
                me,
            ).links(),
        )
        // no status: the event's existence is the acceptance
        assertEquals(
            listOf(Link(Relation.COLLABORATED, LinkTarget.Address(video), "a", mapOf("status" to "accepted"))),
            VideoCollaborationEvent(me, me, 0, tags(arrayOf("d", "random"), arrayOf("a", video)), "", me).links(),
        )
    }
}
