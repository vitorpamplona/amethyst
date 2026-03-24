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
package com.vitorpamplona.quartz.nip01Core

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip10Notes.tags.markedETags
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emoji
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.utils.nsecToSigner
import kotlinx.serialization.json.Json
import nostrability.schemata.validator.SchemataValidator
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Validates that Amethyst's event builders produce events whose JSON shape
 * conforms to the nostrability/schemata JSON Schema definitions.
 *
 * Uses the real builders (MetadataEvent.createNew, TextNoteEvent.build) with
 * every tag extension that screens actually use. Each extension is tested
 * individually — if each building block is schema-valid, any screen-level
 * combination of them will be too.
 */
class SchemaValidationTest {

    val signer = "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToSigner()

    val testPubkey = "a".repeat(64)
    val testPubkey2 = "b".repeat(64)
    val testEventId = "c".repeat(64)
    val testEventId2 = "d".repeat(64)
    val testRelay = NormalizedRelayUrl("wss://relay.example.com/")

    private fun validateEvent(event: Event): Pair<Boolean, List<String>> {
        val json = JacksonMapper.toJson(event)
        val jsonElement = Json.parseToJsonElement(json)
        val result = SchemataValidator.validateNote(jsonElement)
        val errorMessages = result.errors.map { "${it.instancePath}: ${it.message}" }
        return Pair(result.valid, errorMessages)
    }

    private fun assertSchemaValid(event: Event, description: String) {
        val (valid, errors) = validateEvent(event)
        assertTrue(valid, "$description failed schema validation: $errors")
    }

    // ---- Kind 0 (User Metadata / NIP-01) ----

    @Test
    fun `kind 0 basic profile`() {
        assertSchemaValid(
            signer.sign(MetadataEvent.createNew(name = "alice", createdAt = 1700000000)),
            "Kind 0 basic profile",
        )
    }

    @Test
    fun `kind 0 full profile`() {
        assertSchemaValid(
            signer.sign(
                MetadataEvent.createNew(
                    name = "Vitor",
                    displayName = "Vitor Pamplona",
                    about = "Nostr developer",
                    picture = "https://example.com/avatar.jpg",
                    banner = "https://example.com/banner.jpg",
                    pronouns = "he/him",
                    website = "https://example.com",
                    nip05 = "_@example.com",
                    lnAddress = "user@getalby.com",
                    lnURL = "lnurl1test",
                    github = "https://gist.github.com/user/abc123",
                    createdAt = 1700000000,
                ),
            ),
            "Kind 0 full profile",
        )
    }

    @Test
    fun `kind 0 newUser`() {
        assertSchemaValid(
            signer.sign(MetadataEvent.newUser(name = "bob", createdAt = 1700000000)),
            "Kind 0 newUser()",
        )
    }

    @Test
    fun `kind 0 updateFromPast`() {
        val original = signer.sign(MetadataEvent.createNew(name = "alice", createdAt = 1700000000))
        assertSchemaValid(
            signer.sign(
                MetadataEvent.updateFromPast(
                    latest = original,
                    name = "alice2",
                    displayName = "Alice Updated",
                    createdAt = 1700000001,
                ),
            ),
            "Kind 0 updateFromPast()",
        )
    }

    // ---- Kind 1 (Text Note / NIP-01) ----

    @Test
    fun `kind 1 simple note`() {
        assertSchemaValid(
            signer.sign(TextNoteEvent.build(note = "Hello, Nostr!", createdAt = 1700000000)),
            "Kind 1 simple note",
        )
    }

    // ---- Kind 1 + tag extensions used by screens ----

    @Test
    fun `kind 1 with hashtags`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Building on #nostr #dev", createdAt = 1700000000) {
                    hashtags(listOf("nostr", "dev"))
                },
            ),
            "Kind 1 with hashtags",
        )
    }

    @Test
    fun `kind 1 with single hashtag`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Hello #nostr", createdAt = 1700000000) {
                    hashtag("nostr")
                },
            ),
            "Kind 1 with single hashtag",
        )
    }

    @Test
    fun `kind 1 with p-tags`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Hello friends", createdAt = 1700000000) {
                    pTags(listOf(PTag(testPubkey, testRelay), PTag(testPubkey2)))
                },
            ),
            "Kind 1 with p-tags",
        )
    }

    @Test
    fun `kind 1 with single p-tag`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Hey!", createdAt = 1700000000) {
                    pTag(testPubkey, testRelay)
                },
            ),
            "Kind 1 with single p-tag",
        )
    }

    @Test
    fun `kind 1 with e-tag`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Replying", createdAt = 1700000000) {
                    eTag(ETag(testEventId, testRelay, testPubkey))
                },
            ),
            "Kind 1 with e-tag",
        )
    }

    @Test
    fun `kind 1 with marked e-tags for reply threading`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Thread reply", createdAt = 1700000000) {
                    markedETags(
                        listOf(
                            MarkedETag(testEventId, testRelay, MarkedETag.MARKER.ROOT, testPubkey),
                            MarkedETag(testEventId2, testRelay, MarkedETag.MARKER.REPLY, testPubkey2),
                        ),
                    )
                },
            ),
            "Kind 1 with marked e-tags",
        )
    }

    @Test
    fun `kind 1 with notify p-tags`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Mentioning someone", createdAt = 1700000000) {
                    notify(listOf(PTag(testPubkey, testRelay)))
                },
            ),
            "Kind 1 with notify p-tags",
        )
    }

    @Test
    fun `kind 1 with references`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Check https://example.com", createdAt = 1700000000) {
                    references(listOf("https://example.com", "https://nostr.com"))
                },
            ),
            "Kind 1 with references",
        )
    }

    @Test
    fun `kind 1 with quotes`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Quoting this", createdAt = 1700000000) {
                    quotes(listOf(QEventTag(testEventId, testRelay, testPubkey)))
                },
            ),
            "Kind 1 with quotes",
        )
    }

    @Test
    fun `kind 1 with geohash`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Posting from here", createdAt = 1700000000) {
                    geohash("u69twee")
                },
            ),
            "Kind 1 with geohash",
        )
    }

    @Test
    fun `kind 1 with content warning`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Sensitive content", createdAt = 1700000000) {
                    contentWarning("spoiler")
                },
            ),
            "Kind 1 with content warning",
        )
    }

    @Test
    fun `kind 1 with expiration`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Ephemeral note", createdAt = 1700000000) {
                    expiration(1700003600)
                },
            ),
            "Kind 1 with expiration",
        )
    }

    @Test
    fun `kind 1 with custom emoji`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Love this :soapbox:", createdAt = 1700000000) {
                    emoji(EmojiUrlTag("soapbox", "https://example.com/soapbox.png"))
                },
            ),
            "Kind 1 with custom emoji",
        )
    }

    @Test
    fun `kind 1 with multiple custom emojis`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = ":bitcoin: to the moon :rocket:", createdAt = 1700000000) {
                    emojis(
                        listOf(
                            EmojiUrlTag("bitcoin", "https://example.com/bitcoin.png"),
                            EmojiUrlTag("rocket", "https://example.com/rocket.png"),
                        ),
                    )
                },
            ),
            "Kind 1 with multiple custom emojis",
        )
    }

    @Test
    fun `kind 1 with imeta`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Check this image https://example.com/photo.jpg", createdAt = 1700000000) {
                    imetas(
                        listOf(
                            IMetaTag(
                                url = "https://example.com/photo.jpg",
                                properties = mapOf(
                                    "m" to listOf("image/jpeg"),
                                    "dim" to listOf("1920x1080"),
                                ),
                            ),
                        ),
                    )
                },
            ),
            "Kind 1 with imeta",
        )
    }

    @Test
    fun `kind 1 with zapraiser`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Fundraising for a cause", createdAt = 1700000000) {
                    zapraiser(100000L)
                },
            ),
            "Kind 1 with zapraiser",
        )
    }

    @Test
    fun `kind 1 with zap splits`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Split zaps", createdAt = 1700000000) {
                    zapSplits(
                        listOf(
                            ZapSplitSetup(testPubkey, testRelay, 0.5),
                            ZapSplitSetup(testPubkey2, null, 0.5),
                        ),
                    )
                },
            ),
            "Kind 1 with zap splits",
        )
    }

    // ---- Kind 1 second overload: build(note, replyingTo, forkingFrom) ----

    @Test
    fun `kind 1 reply to root note`() {
        // First note becomes root (no existing threading tags)
        val rootNote = signer.sign(
            TextNoteEvent.build(note = "I am the root", createdAt = 1700000000),
        )
        val rootHint = EventHintBundle(rootNote, testRelay)

        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(
                    note = "Replying to root",
                    replyingTo = rootHint,
                    createdAt = 1700000001,
                ),
            ),
            "Kind 1 reply to root note",
        )
    }

    @Test
    fun `kind 1 reply to reply (deep thread)`() {
        // Build a 3-level thread: root -> reply1 -> reply2
        val rootNote = signer.sign(
            TextNoteEvent.build(note = "Root", createdAt = 1700000000),
        )
        val reply1 = signer.sign(
            TextNoteEvent.build(
                note = "First reply",
                replyingTo = EventHintBundle(rootNote, testRelay),
                createdAt = 1700000001,
            ),
        )
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(
                    note = "Second reply (deep thread)",
                    replyingTo = EventHintBundle(reply1, testRelay),
                    createdAt = 1700000002,
                ),
            ),
            "Kind 1 reply to reply (deep thread)",
        )
    }

    @Test
    fun `kind 1 fork from note`() {
        val originalNote = signer.sign(
            TextNoteEvent.build(note = "Original post", createdAt = 1700000000),
        )

        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(
                    note = "Forked version",
                    forkingFrom = EventHintBundle(originalNote, testRelay),
                    createdAt = 1700000001,
                ),
            ),
            "Kind 1 fork from note",
        )
    }

    @Test
    fun `kind 1 reply with fork`() {
        val rootNote = signer.sign(
            TextNoteEvent.build(note = "Root", createdAt = 1700000000),
        )
        val forkSource = signer.sign(
            TextNoteEvent.build(note = "Source to fork", createdAt = 1700000000),
        )

        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(
                    note = "Reply with fork",
                    replyingTo = EventHintBundle(rootNote, testRelay),
                    forkingFrom = EventHintBundle(forkSource, testRelay),
                    createdAt = 1700000001,
                ),
            ),
            "Kind 1 reply with fork",
        )
    }

    @Test
    fun `kind 1 reply with additional tags`() {
        val rootNote = signer.sign(
            TextNoteEvent.build(note = "Root post", createdAt = 1700000000),
        )

        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(
                    note = "Reply with extras #nostr",
                    replyingTo = EventHintBundle(rootNote, testRelay),
                    createdAt = 1700000001,
                ) {
                    notify(listOf(PTag(testPubkey, testRelay)))
                    hashtags(listOf("nostr"))
                    references(listOf("https://example.com"))
                },
            ),
            "Kind 1 reply with additional tags",
        )
    }

    // ---- Combined: all extensions with second overload ----

    @Test
    fun `kind 1 with all tag extensions combined`() {
        assertSchemaValid(
            signer.sign(
                TextNoteEvent.build(note = "Full post with everything #nostr :bitcoin:", createdAt = 1700000000) {
                    markedETags(listOf(MarkedETag(testEventId, testRelay, MarkedETag.MARKER.ROOT, testPubkey)))
                    notify(listOf(PTag(testPubkey, testRelay)))
                    hashtags(listOf("nostr"))
                    references(listOf("https://example.com"))
                    quotes(listOf(QEventTag(testEventId2, testRelay, testPubkey2)))
                    geohash("u69twee")
                    contentWarning("test")
                    expiration(1700003600)
                    emojis(listOf(EmojiUrlTag("bitcoin", "https://example.com/bitcoin.png")))
                    imetas(listOf(IMetaTag("https://example.com/photo.jpg", mapOf("m" to listOf("image/jpeg")))))
                    zapraiser(50000L)
                    zapSplits(listOf(ZapSplitSetup(testPubkey2, testRelay, 1.0)))
                },
            ),
            "Kind 1 with all tag extensions combined",
        )
    }
}
