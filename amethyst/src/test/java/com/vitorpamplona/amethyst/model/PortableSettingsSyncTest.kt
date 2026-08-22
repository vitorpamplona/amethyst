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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.concord.ConcordViewMode
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupViewMode
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The settings that used to be device-only now ride the NIP-78 blob so they
 * reach a new phone. Same nullable-default hazard the mute set documents in
 * [MutedPublicChatsSyncTest]: `AppSpecificState` replays the cached blob on
 * every launch, so a field an older client dropped must read as "absent, keep
 * local", never as "reset to the default".
 */
class PortableSettingsSyncTest {
    private val local =
        PortableAccountSettings(
            feedFilters =
                mapOf(
                    "defaultHomeFollowList" to TopFilter.AllFollows,
                    "defaultArticlesFollowList" to TopFilter.Global,
                ),
            mutedPublicChats = setOf("a".repeat(64)),
            disabledChatFeeds = setOf(ChatFeedType.NIP04.code),
            disabledHomeFeedTypes = setOf("reposts"),
            relayGroupViewMode = RelayGroupViewMode.DEFAULT,
            concordViewMode = ConcordViewMode.DEFAULT,
            relayAuthPolicy = RelayAuthPolicy.CUSTOM,
            relayAuthTrustMyRelaysAndVenues = true,
            relayAuthTrustReadFollows = false,
            relayAuthTrustMessageFollows = true,
            relayAuthTrustMessageStrangers = false,
            hideDeleteRequestDialog = true,
            hideBlockAlertDialog = false,
            hideNip17WarningDialog = true,
            hasDonatedInVersion = setOf("1.0"),
            callsEnabled = false,
            splitNotificationsEnabled = true,
            showMessagesInNotifications = false,
        )

    // ---
    // Absent vs explicit
    // ---

    @Test
    fun anAppSectionFromAnOlderClientDecodesEveryFieldToNull() {
        // Decoded on its own rather than through AccountSyncedSettingsInternal:
        // that type's language defaults call Resources.getSystem(), which is null
        // off-device, so the whole envelope cannot be decoded in a JVM test. The
        // envelope's own `app` field is declared nullable with a null default,
        // which is what makes an absent section decode to null.
        val decoded = JsonMapper.fromJson<AccountAppPreferencesInternal>("{}")

        assertNull(decoded.feedFilters)
        assertNull(decoded.disabledChatFeeds)
        assertNull(decoded.disabledHomeFeedTypes)
        assertNull(decoded.relayAuthPolicy)
        assertNull(decoded.callsEnabled)
        assertNull(decoded.hasDonatedInVersion)
    }

    @Test
    fun aBlobWithoutTheAppSectionLeavesEverythingAlone() {
        assertEquals(local, mergePortableSettings(local, null))
    }

    @Test
    fun anAppSectionMissingAFieldLeavesThatFieldAlone() {
        // What an older client writes: it knows the section but not this field.
        val remote = JsonMapper.fromJson<AccountAppPreferencesInternal>("""{"callsEnabled":true}""")

        val merged = mergePortableSettings(local, remote)

        assertEquals("the field it did write is adopted", true, merged.callsEnabled)
        assertEquals(local.relayAuthPolicy, merged.relayAuthPolicy)
        assertEquals(local.disabledChatFeeds, merged.disabledChatFeeds)
        assertEquals(local.hideDeleteRequestDialog, merged.hideDeleteRequestDialog)
    }

    @Test
    fun anExplicitlyEmptyDisabledListIsAdopted() {
        // Distinct from absent: this is a real "re-enable everything" from a
        // client that knows the field.
        val remote = AccountAppPreferencesInternal(disabledChatFeeds = emptyList())

        assertEquals(emptySet<String>(), mergePortableSettings(local, remote).disabledChatFeeds)
    }

    // ---
    // Per-field merge rules
    // ---

    @Test
    fun feedFiltersMergePerKeyRatherThanReplacing() {
        // A client that predates a feed writes the map without that feed's key.
        // Replacing the whole map would reset the filter it never knew about.
        val remote =
            AccountAppPreferencesInternal(
                feedFilters = mapOf("defaultHomeFollowList" to JsonMapper.toJson<TopFilter>(TopFilter.Global)),
            )

        val merged = mergePortableSettings(local, remote)

        assertEquals(TopFilter.Global.code, merged.feedFilters["defaultHomeFollowList"]?.code)
        assertEquals(TopFilter.Global.code, merged.feedFilters["defaultArticlesFollowList"]?.code)
        assertEquals(2, merged.feedFilters.size)
    }

    @Test
    fun donationsAreUnionedNotReplaced() {
        // A donation on either phone is a fact about the user; dropping one puts
        // the donation nag back in front of a supporter.
        val remote = AccountAppPreferencesInternal(hasDonatedInVersion = listOf("2.0"))

        assertEquals(setOf("1.0", "2.0"), mergePortableSettings(local, remote).hasDonatedInVersion)
    }

    @Test
    fun anUnknownEnumNameKeepsTheLocalValue() {
        // Written by a newer client. Falling back to the enum's DEFAULT would
        // silently reset a setting the user chose.
        val remote =
            AccountAppPreferencesInternal(
                relayAuthPolicy = "SOMETHING_WE_DO_NOT_HAVE_YET",
                relayGroupViewMode = "ALSO_UNKNOWN",
                concordViewMode = "UNKNOWN_TOO",
            )

        val merged = mergePortableSettings(local, remote)

        assertEquals(RelayAuthPolicy.CUSTOM, merged.relayAuthPolicy)
        assertEquals(RelayGroupViewMode.DEFAULT, merged.relayGroupViewMode)
        assertEquals(ConcordViewMode.DEFAULT, merged.concordViewMode)
    }

    @Test
    fun aFeedCodeThisBuildDoesNotKnowSurvivesTheMerge() {
        // Codes are resolved to enums only when applied, so a chat type added by
        // a newer client stays disabled here and round-trips back out instead of
        // being silently re-enabled for that client.
        val remote = AccountAppPreferencesInternal(disabledChatFeeds = listOf("nip04", "a_protocol_from_the_future"))

        assertEquals(setOf("nip04", "a_protocol_from_the_future"), mergePortableSettings(local, remote).disabledChatFeeds)
    }

    // ---
    // Wire format
    // ---

    @Test
    fun everySettingSurvivesTheRoundTrip() {
        val decoded = JsonMapper.fromJson<AccountAppPreferencesInternal>(JsonMapper.toJson(local.toInternal()))

        assertEquals(local, mergePortableSettings(local.copy(feedFilters = emptyMap()), decoded))
    }

    @Test
    fun theSerializedFormIsStableForUnchangedSettings() {
        // The publish check compares serialized forms, so a set that iterates in
        // a different order must not look like an edit and republish the event.
        val reordered = local.copy(hasDonatedInVersion = setOf("1.0"), disabledChatFeeds = setOf(ChatFeedType.NIP04.code))

        assertEquals(JsonMapper.toJson(local.toInternal()), JsonMapper.toJson(reordered.toInternal()))
    }

    @Test
    fun aChangedSettingChangesTheSerializedForm() {
        val changed = local.copy(callsEnabled = !local.callsEnabled)

        assertNotEquals(JsonMapper.toJson(local.toInternal()), JsonMapper.toJson(changed.toInternal()))
    }

    @Test
    fun aFilterKindThisBuildCannotReadDropsOnlyThatEntry() {
        // The load-bearing property of the wire format. TopFilter is a sealed
        // hierarchy, so a typed map would put a subclass discriminator on the
        // blob and one unknown kind would fail the WHOLE decode — silently
        // ending settings sync on every older client. Opaque JSON per entry
        // confines the failure to the entry.
        val decoded =
            decodeFeedFilters(
                mapOf(
                    "defaultHomeFollowList" to JsonMapper.toJson<TopFilter>(TopFilter.Hashtag("nostr")),
                    "defaultArticlesFollowList" to """{"type":"com.example.FilterFromTheFuture"}""",
                    "defaultShortsFollowList" to "not json at all",
                ),
            )

        assertEquals(setOf("defaultHomeFollowList"), decoded.keys)
        assertEquals(TopFilter.Hashtag("nostr").code, decoded["defaultHomeFollowList"]?.code)
    }

    @Test
    fun feedFiltersSurviveAFullRoundTripThroughTheBlob() {
        val filters =
            mapOf(
                "defaultHomeFollowList" to TopFilter.AllFollows,
                "defaultArticlesFollowList" to TopFilter.Hashtag("nostr"),
            )

        val wire = JsonMapper.fromJson<AccountAppPreferencesInternal>(JsonMapper.toJson(local.copy(feedFilters = filters).toInternal()))
        val decoded = decodeFeedFilters(wire.feedFilters!!)

        // by code: the addressable subclasses are plain classes with no equals()
        assertEquals(TopFilter.AllFollows.code, decoded["defaultHomeFollowList"]?.code)
        assertEquals(TopFilter.Hashtag("nostr").code, decoded["defaultArticlesFollowList"]?.code)
    }
}
