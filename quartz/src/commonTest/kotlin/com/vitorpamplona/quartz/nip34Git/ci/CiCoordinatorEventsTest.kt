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
package com.vitorpamplona.quartz.nip34Git.ci

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip34Git.ci.coordinatorAdvertisement.CiCoordinatorAdvertisementEvent
import com.vitorpamplona.quartz.nip34Git.ci.repositoryStatus.CiRepositoryStatusEvent
import com.vitorpamplona.quartz.nip34Git.ci.requestReadiness.CiRequestReadinessListEvent
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiAdmissionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiBillingPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiExecutionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiRepositorySecret
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiRunnerSelector
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiSecretsKey
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiServiceState
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiSoftware
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The coordinator-published kinds — 19843, 19844 and 39844 — against real events (2026-10), verbatim. */
class CiCoordinatorEventsTest {
    private val advertisement =
        """{"id":"b9333ac31e463a2c00f1ff2fa5ce2d4d0e4c5b7d7aed3eea72f57621a7d0da4d","pubkey":"9c14feb6c51985448a350c53ecc4533cfa093b79a1e2079968ca8c344152169a","created_at":1791427654,"kind":19843,"tags":[["software","ngit-ci","0.1.1"],["W","act"],["R","act:ubuntu-22.04"],["R","act:ubuntu-24.04"],["R","act:ubuntu-latest"],["W","nix"],["R","nix:x86_64-linux"],["M","operator-selected"],["X","request-required"],["expiration","1791429454"],["secrets-key","nip44-v2","dac9021eb33311a3382ad088ddafba6a131c8023dcba2bb3cd06a1f5d9772114","wss://relay.ditto.pub","wss://nos.lol"]],"content":"","sig":"01f30151cdb9a4e3759c04e16b795792040b1c04756640cd1e06a14d92ba4c43c119e09b4d674371b197d8d23c2bdb0fa249da55189f9bbb7ee0f391a48a8801"}"""

    private val readiness =
        """{"id":"cdbea7477ac124924e63e6e7ad5978544234c07f5273a096acb51072deff1ee8","pubkey":"9c14feb6c51985448a350c53ecc4533cfa093b79a1e2079968ca8c344152169a","created_at":1791376977,"kind":19844,"tags":[["a","30617:3fdabe9e7a9fbf707bafeb477f1e8b1ad3a316807a4bfc3708762eae4e850332:ngit-ci-publish-smoke-1782993398"],["a","30617:a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d:gitworkshop"],["a","30617:a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d:ngit"],["a","30617:a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d:ngit-ci"],["a","30617:a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d:ngit-grasp"],["p","a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d"],["expiration","1791463377"]],"content":"","sig":"c9ebd3005c9ed75d01e8d7056482683f256ed1dce19da9a5ad54b1f336d222e070fbef1a424911831887f33339589f26255c0bc34b7c9b716d6174f9024fb347"}"""

    private val statusJson =
        """{"id":"98a21509bac1d73bf01420fc8adc329b3cfbc2cb30cf78355ccdef53908f0fa3","pubkey":"9e086849862369ac74314dbd4701dc3dee79058203137df4c14d5e9ca7df7391","created_at":1791405985,"kind":39844,"tags":[["d","30617:b3c95ce33dfa84326611e8b7a9c10b78df28754c38b106a4bc0196b9be5f4e4a:wyrd"],["a","30617:b3c95ce33dfa84326611e8b7a9c10b78df28754c38b106a4bc0196b9be5f4e4a:wyrd","wss://grasp.t5.st"],["a","30617:86a314a7ef4aa4fb4e00d738a7bec5fc96ac1312246c7c25888ae609046e6965:wyrd","wss://grasp.t5.st"],["a","30617:d84afa5ba1239500e03ce4d6a2b1e5eef1696a814c3f9cc25b50721bedef0a0f:wyrd","wss://grasp.t5.st"],["s","acting"],["W","act"],["workflow-path",".ngit/act/workflows/*.yaml"],["workflow-path",".ngit/act/workflows/*.yml"],["R","act:ubuntu-latest"],["secret","CLOUDFLARE_ACCOUNT_ID","b3c95ce33dfa84326611e8b7a9c10b78df28754c38b106a4bc0196b9be5f4e4a","1790113942"],["secret","CLOUDFLARE_API_TOKEN","b3c95ce33dfa84326611e8b7a9c10b78df28754c38b106a4bc0196b9be5f4e4a","1790117523"],["secret","NGIT_NSEC"],["secret","OPENROUTER_API_KEY"],["expiration","1791492385"]],"content":"","sig":"db0793af41c39d6e41b8eda34f25856eb22ed35c65a76d7e0462b73ee1fab6ea8bccf6c1b2e7505b06d93b48c068d7496431159b9672041e68ff5e59c50569ab"}"""

    private val coordinator = "9c14feb6c51985448a350c53ecc4533cfa093b79a1e2079968ca8c344152169a"
    private val wyrdOwner = "b3c95ce33dfa84326611e8b7a9c10b78df28754c38b106a4bc0196b9be5f4e4a"

    @Test
    fun factoryBuildsAllThree() {
        assertIs<CiCoordinatorAdvertisementEvent>(Event.fromJson(advertisement))
        assertIs<CiRequestReadinessListEvent>(Event.fromJson(readiness))
        assertIs<CiRepositoryStatusEvent>(Event.fromJson(statusJson))
        listOf(CiCoordinatorAdvertisementEvent.KIND, CiRequestReadinessListEvent.KIND, CiRepositoryStatusEvent.KIND).forEach {
            assertTrue(EventFactory.isKnownKind(it), "kind $it")
        }
    }

    @Test
    fun readsTheAdvertisement() {
        val ad = assertIs<CiCoordinatorAdvertisementEvent>(Event.fromJson(advertisement))
        assertEquals("19843:$coordinator:", ad.address().toValue())
        assertEquals(CiSoftware("ngit-ci", "0.1.1"), ad.software())
        assertEquals(listOf("act", "nix"), ad.runnerFamilies())
        assertEquals(
            listOf("act:ubuntu-22.04", "act:ubuntu-24.04", "act:ubuntu-latest", "nix:x86_64-linux"),
            ad.runnerSelectors().map { it.toValue() },
        )
        assertEquals(CiAdmissionPolicy.OPERATOR_SELECTED, ad.admission())
        assertEquals(CiExecutionPolicy.REQUEST_REQUIRED, ad.execution())
        assertNull(ad.billing())
        assertEquals("dac9021eb33311a3382ad088ddafba6a131c8023dcba2bb3cd06a1f5d9772114", ad.secretsKey()?.recipient)
        assertEquals(listOf("wss://relay.ditto.pub/", "wss://nos.lol/"), ad.secretsKey()?.inboxRelays?.map { it.url })
        assertTrue(ad.acceptsSecretUpdates())
        assertEquals(1791429454L, ad.validExpiration())
        assertTrue(ad.isLive(now = 1791428000L))
        assertFalse(ad.isLive(now = 1791429454L))
    }

    @Test
    fun unknownOrAmbiguousPoliciesAreNeverPermissive() {
        fun ad(vararg tags: Array<String>) = assertIs<CiCoordinatorAdvertisementEvent>(EventFactory.create<Event>("1".repeat(64), coordinator, 1L, CiCoordinatorAdvertisementEvent.KIND, arrayOf(*tags), "", "0".repeat(128)))

        val unknown = ad(arrayOf("M", "invite-only"), arrayOf("X", "whenever"), arrayOf("B", "free-ish"))
        assertEquals(CiAdmissionPolicy.UNKNOWN, unknown.admission())
        assertEquals(CiExecutionPolicy.UNKNOWN, unknown.execution())
        assertEquals(CiBillingPolicy.UNKNOWN, unknown.billing())

        // Two disagreeing tags must not let a reader pick the open one.
        val ambiguous = ad(arrayOf("M", "operator-selected"), arrayOf("M", "open"), arrayOf("X", "request-required"), arrayOf("X", "automatic"))
        assertEquals(CiAdmissionPolicy.UNKNOWN, ambiguous.admission())
        assertEquals(CiExecutionPolicy.UNKNOWN, ambiguous.execution())

        val absent = ad()
        assertNull(absent.admission())
        assertNull(absent.execution())
        assertNull(absent.billing())
        assertFalse(absent.acceptsSecretUpdates())
        assertFalse(absent.isLive(now = 0L))

        // A selector whose family is not advertised is dropped; families are case-folded.
        val caps = ad(arrayOf("W", "ACT"), arrayOf("R", "act:Ubuntu-Latest"), arrayOf("R", "nix:x86_64-linux"), arrayOf("R", "no-colon"), arrayOf("R", ":empty"))
        assertEquals(listOf("act"), caps.runnerFamilies())
        assertEquals(listOf(CiRunnerSelector("act", "ubuntu-latest")), caps.runnerSelectors())

        // A secrets-key with no inbox relay, or a malformed key, is not an offer to accept secrets.
        assertNull(ad(arrayOf("secrets-key", "nip44-v2", "d".repeat(64))).secretsKey())
        assertNull(ad(arrayOf("secrets-key", "nip44-v2", "short", "wss://nos.lol")).secretsKey())
        // A stray `d` does not split the replaceable address.
        assertEquals("", ad(arrayOf("d", "x")).dTag())
    }

    @Test
    fun advertisementHasNoGraphEdges() {
        val ad = Event.fromJson(advertisement)
        assertFalse(ad is PubKeyHintProvider)
        assertFalse(ad is EventHintProvider)
        assertFalse(ad is AddressHintProvider)
    }

    @Test
    fun advertisementBuilderRoundTrip() {
        val key = CiSecretsKey("nip44-v2", "a".repeat(64), listOfNotNull(RelayUrlNormalizer.normalizeOrNull("wss://nos.lol")))
        val template =
            CiCoordinatorAdvertisementEvent.build(
                software = CiSoftware("ngit-ci", "0.2.0"),
                runnerFamilies = listOf("act"),
                runnerSelectors = listOf(CiRunnerSelector("act", "ubuntu-latest")),
                admission = CiAdmissionPolicy.MAINTAINER_REQUEST,
                execution = CiExecutionPolicy.AUTOMATIC,
                billing = CiBillingPolicy.OUT_OF_BAND,
                secretsKey = key,
                createdAt = 1_000L,
            )
        val ad = assertIs<CiCoordinatorAdvertisementEvent>(EventFactory.create<Event>("1".repeat(64), coordinator, template.createdAt, template.kind, template.tags, template.content, "0".repeat(128)))
        assertEquals(CiSoftware("ngit-ci", "0.2.0"), ad.software())
        assertEquals(listOf("act"), ad.runnerFamilies())
        assertEquals(listOf(CiRunnerSelector("act", "ubuntu-latest")), ad.runnerSelectors())
        assertEquals(CiAdmissionPolicy.MAINTAINER_REQUEST, ad.admission())
        assertEquals(CiExecutionPolicy.AUTOMATIC, ad.execution())
        assertEquals(CiBillingPolicy.OUT_OF_BAND, ad.billing())
        assertEquals(key, ad.secretsKey())
        assertEquals(1_000L + CiCoordinatorAdvertisementEvent.MAX_EXPIRATION_SECONDS, ad.validExpiration())
    }

    @Test
    fun readsTheReadinessList() {
        val list = assertIs<CiRequestReadinessListEvent>(Event.fromJson(readiness))
        assertEquals("", list.dTag())
        assertEquals(5, list.repositories().size)
        assertEquals("30617:a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d:gitworkshop", list.repositories()[1].toTag())
        assertEquals(listOf("a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d"), list.maintainers().map { it.pubKey })
        assertEquals(5, list.linkedAddressIds().size)
        assertEquals(listOf("a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d"), list.linkedPubKeys())
        assertEquals(1791463377L, list.validExpiration())
    }

    @Test
    fun readinessItemsWithAMarkerAreNotItems() {
        val repo = "30617:${"a".repeat(64)}:repo"
        val tags =
            arrayOf(
                arrayOf("a", repo, "wss://relay.ngit.dev"),
                arrayOf("a", repo),
                arrayOf("a", "30617:${"b".repeat(64)}:other", "", "marker"),
                arrayOf("p", "c".repeat(64), "", "marker"),
                arrayOf("expiration", "${1L + 24 * 60 * 60 + 1}"),
            )
        val list = assertIs<CiRequestReadinessListEvent>(EventFactory.create<Event>("1".repeat(64), coordinator, 1L, CiRequestReadinessListEvent.KIND, tags, "", "0".repeat(128)))
        assertEquals(listOf(repo), list.repositories().map { it.toTag() })
        assertEquals(emptyList(), list.maintainers())
        assertEquals(listOf(repo), list.linkedAddressIds())
        assertEquals(listOf(repo to "wss://relay.ngit.dev/"), list.addressHints().map { it.addressId to it.relay.url })
        // Beyond the 24-hour bound.
        assertNull(list.validExpiration())
    }

    @Test
    fun readinessBuilderRoundTrip() {
        val repo = ATag(30617, "a".repeat(64), "repo", null)
        val template = CiRequestReadinessListEvent.build(listOf(repo, repo), listOf(PTag("b".repeat(64))), createdAt = 10L)
        assertEquals(1, template.tags.count { it[0] == "a" })
        val list = assertIs<CiRequestReadinessListEvent>(EventFactory.create<Event>("1".repeat(64), coordinator, template.createdAt, template.kind, template.tags, template.content, "0".repeat(128)))
        assertEquals(listOf(repo.toTag()), list.repositories().map { it.toTag() })
        assertEquals(listOf("b".repeat(64)), list.maintainers().map { it.pubKey })
        assertEquals(10L + CiRequestReadinessListEvent.MAX_EXPIRATION_SECONDS, list.validExpiration())
    }

    @Test
    fun readsTheRepositoryStatus() {
        val status = assertIs<CiRepositoryStatusEvent>(Event.fromJson(statusJson))
        assertEquals("30617:$wyrdOwner:wyrd", status.selectedRepository()?.toValue())
        assertEquals("30617:$wyrdOwner:wyrd", status.repositories().first().toTag())
        assertEquals(3, status.repositories().size)
        assertEquals(CiServiceState.ACTING, status.state())
        assertTrue(status.isActing())
        assertEquals(listOf("act"), status.runnerFamilies())
        assertEquals(listOf(CiRunnerSelector("act", "ubuntu-latest")), status.runnerSelectors())
        assertEquals(listOf(".ngit/act/workflows/*.yaml", ".ngit/act/workflows/*.yml"), status.workflowPaths())
        assertEquals(
            listOf(
                CiRepositorySecret("CLOUDFLARE_ACCOUNT_ID", wyrdOwner, 1790113942L, false),
                CiRepositorySecret("CLOUDFLARE_API_TOKEN", wyrdOwner, 1790117523L, false),
                CiRepositorySecret("NGIT_NSEC", null, null, false),
                CiRepositorySecret("OPENROUTER_API_KEY", null, null, false),
            ),
            status.secrets(),
        )
        assertTrue(status.secrets()[2].isOperatorProvided())

        assertEquals(3, status.linkedAddressIds().size)
        assertEquals(3, status.addressHints().size)
        assertEquals(listOf(wyrdOwner), status.linkedPubKeys())
    }

    @Test
    fun statusToleratesMalformedTags() {
        val tags =
            arrayOf(
                arrayOf("d", "not-an-address"),
                arrayOf("s", "resting"),
                arrayOf("secret", "BAD_ORIGIN", "short", "1"),
                arrayOf("secret", "BAD_TIME", "a".repeat(64), "yesterday"),
                arrayOf("secret", "SEALED", "a".repeat(64), "12", "sealed"),
                arrayOf("secret"),
            )
        val status = assertIs<CiRepositoryStatusEvent>(EventFactory.create<Event>("1".repeat(64), coordinator, 1L, CiRepositoryStatusEvent.KIND, tags, "", "0".repeat(128)))
        assertNull(status.selectedRepository())
        assertNull(status.state())
        assertFalse(status.isActing())
        assertEquals(listOf(CiRepositorySecret("SEALED", "a".repeat(64), 12L, true)), status.secrets())
    }

    @Test
    fun statusBuilderRoundTrip() {
        val selected = ATag(30617, wyrdOwner, "wyrd", RelayUrlNormalizer.normalizeOrNull("wss://grasp.t5.st"))
        val others = listOf(ATag(30617, "d".repeat(64), "wyrd", null), ATag(30617, "c".repeat(64), "wyrd", null), selected)
        val secrets = listOf(CiRepositorySecret("TOKEN", wyrdOwner, 5L, true), CiRepositorySecret("OPERATOR", null, null, false))
        val template =
            CiRepositoryStatusEvent.build(
                selected = selected,
                maintainerRepositories = others,
                state = CiServiceState.ACTING,
                runnerFamilies = listOf("act"),
                workflowPaths = listOf(".ngit/act/workflows/*.yml"),
                runnerSelectors = listOf(CiRunnerSelector("act", "ubuntu-latest")),
                secrets = secrets,
                createdAt = 100L,
            )
        val status = assertIs<CiRepositoryStatusEvent>(EventFactory.create<Event>("1".repeat(64), coordinator, template.createdAt, template.kind, template.tags, template.content, "0".repeat(128)))
        assertEquals(selected.toTag(), status.dTag())
        // Selected root first, then the rest of the closure sorted and without the root repeated.
        assertEquals(listOf(selected.toTag(), "30617:${"c".repeat(64)}:wyrd", "30617:${"d".repeat(64)}:wyrd"), status.repositories().map { it.toTag() })
        assertEquals(CiServiceState.ACTING, status.state())
        assertEquals(listOf(".ngit/act/workflows/*.yml"), status.workflowPaths())
        assertEquals(secrets, status.secrets())
        assertEquals(100L + CiRepositoryStatusEvent.MAX_EXPIRATION_SECONDS, status.validExpiration())
    }
}
