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
package com.vitorpamplona.quartz.nip72ModCommunities.rules

import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.KindRuleTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.MaxEventSizeTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.MinRulesCreatedAtTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.PubkeyRuleTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.WotTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommunityRulesValidatorTest {
    private val owner = "a".repeat(64)
    private val author = "b".repeat(64)
    private val villain = "c".repeat(64)
    private val communityAddr = "34550:$owner:my-community"
    private val rulesD = "my-community"

    private fun rules(
        kindRules: List<KindRuleTag> = listOf(KindRuleTag(1111, 16384, null)),
        pubkeyRules: List<PubkeyRuleTag> = emptyList(),
        wotGates: List<WotTag> = emptyList(),
        maxSize: Int? = null,
        minRulesCreatedAt: Long? = null,
        createdAt: Long = 1_700_000_000L,
    ): CommunityRulesEvent {
        val tags =
            buildList {
                add(arrayOf("d", rulesD))
                add(arrayOf("a", communityAddr))
                kindRules.forEach { add(it.toTagArray()) }
                pubkeyRules.forEach { add(it.toTagArray()) }
                wotGates.forEach { add(it.toTagArray()) }
                maxSize?.let { add(MaxEventSizeTag.assemble(it)) }
                minRulesCreatedAt?.let { add(MinRulesCreatedAtTag.assemble(it)) }
            }.toTypedArray()
        return CommunityRulesEvent("id", owner, createdAt, tags, "", "sig")
    }

    @Test
    fun allowsConformantPost() {
        val v = CommunityRulesValidator(rules()).validate(author, 1111, 1024)
        assertNull(v)
    }

    @Test
    fun rejectsDisallowedKind() {
        val v = CommunityRulesValidator(rules()).validate(author, 30023, 1024)
        val violation = assertIs<CommunityRulesValidator.Violation.KindNotAllowed>(v)
        assertEquals(30023, violation.kind)
    }

    @Test
    fun rejectsOversizedPostByKindLimit() {
        val v = CommunityRulesValidator(rules()).validate(author, 1111, 20_000)
        val violation = assertIs<CommunityRulesValidator.Violation.KindSizeExceeded>(v)
        assertEquals(1111, violation.kind)
        assertEquals(20_000, violation.sizeBytes)
        assertEquals(16384, violation.maxBytes)
    }

    @Test
    fun rejectsOversizedPostByGlobalCap() {
        val r = rules(kindRules = listOf(KindRuleTag(1111, null, null)), maxSize = 8000)
        val v = CommunityRulesValidator(r).validate(author, 1111, 9000)
        val violation = assertIs<CommunityRulesValidator.Violation.MaxSizeExceeded>(v)
        assertEquals(8000, violation.maxBytes)
    }

    @Test
    fun rejectsDeniedAuthorRegardlessOfKind() {
        val r =
            rules(
                pubkeyRules = listOf(PubkeyRuleTag(villain, PubkeyRuleTag.Policy.DENY, null)),
            )
        val v = CommunityRulesValidator(r).validate(villain, 1111, 100)
        val violation = assertIs<CommunityRulesValidator.Violation.AuthorDenied>(v)
        assertEquals(villain, violation.author)
    }

    @Test
    fun denyOverridesAllowForSamePubkey() {
        val r =
            rules(
                pubkeyRules =
                    listOf(
                        PubkeyRuleTag(villain, PubkeyRuleTag.Policy.ALLOW, null),
                        PubkeyRuleTag(villain, PubkeyRuleTag.Policy.DENY, null),
                    ),
            )
        val v = CommunityRulesValidator(r).validate(villain, 1111, 100)
        assertIs<CommunityRulesValidator.Violation.AuthorDenied>(v)
    }

    @Test
    fun enforcesPerKindDailyQuota() {
        val r = rules(kindRules = listOf(KindRuleTag(1111, null, 5)))
        val v = CommunityRulesValidator(r).validate(author, 1111, 100, postsTodayByKind = { 5 })
        val violation = assertIs<CommunityRulesValidator.Violation.QuotaExceeded>(v)
        assertEquals(5, violation.postsToday)
        assertEquals(5, violation.maxPerDay)
    }

    @Test
    fun unknownQuotaPostsDoesNotBlock() {
        val r = rules(kindRules = listOf(KindRuleTag(1111, null, 5)))
        val v = CommunityRulesValidator(r).validate(author, 1111, 100, postsTodayByKind = { null })
        assertNull(v)
    }

    @Test
    fun wotGatePassesWhenReachable() {
        val r = rules(wotGates = listOf(WotTag(owner, 2)))
        val v =
            CommunityRulesValidator(r).validate(author, 1111, 100) { _, _, _ -> true }
        assertNull(v)
    }

    @Test
    fun wotGateFailsWhenUnreachable() {
        val r = rules(wotGates = listOf(WotTag(owner, 2)))
        val v =
            CommunityRulesValidator(r).validate(author, 1111, 100) { _, _, _ -> false }
        val violation = assertIs<CommunityRulesValidator.Violation.WotGateFailed>(v)
        assertEquals(1, violation.gateCount)
    }

    @Test
    fun explicitAllowBypassesWotGate() {
        val r =
            rules(
                pubkeyRules = listOf(PubkeyRuleTag(author, PubkeyRuleTag.Policy.ALLOW, "vip")),
                wotGates = listOf(WotTag(owner, 2)),
            )
        val v =
            CommunityRulesValidator(r).validate(author, 1111, 100) { _, _, _ -> false }
        assertNull(v)
    }

    @Test
    fun multipleWotGatesActAsOr() {
        val r =
            rules(
                wotGates =
                    listOf(
                        WotTag(owner, 1),
                        WotTag(villain, 5),
                    ),
            )
        // Only the second gate accepts.
        val v =
            CommunityRulesValidator(r).validate(author, 1111, 100) { _, root, _ ->
                root == villain
            }
        assertNull(v)
    }

    @Test
    fun staleRulesAreRejected() {
        val r = rules(minRulesCreatedAt = 1_800_000_000L, createdAt = 1_700_000_000L)
        val v = CommunityRulesValidator(r).validate(author, 1111, 100)
        val violation = assertIs<CommunityRulesValidator.Violation.StaleRules>(v)
        assertEquals(1_700_000_000L, violation.rulesCreatedAt)
        assertEquals(1_800_000_000L, violation.minRulesCreatedAt)
    }

    @Test
    fun parsesEventAccessors() {
        val r = rules()
        assertEquals(communityAddr, r.communityAddress())
        assertEquals(setOf(1111), r.allowedKinds())
        assertTrue(r.kindRules().isNotEmpty())
    }
}
