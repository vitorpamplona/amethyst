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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.quartz.utils

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.SoftwareReleaseEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.build
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.buildDTag
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.redemption.NutzapRedemptionEvent
import com.vitorpamplona.quartz.nip61Nutzaps.redemption.build
import com.vitorpamplona.quartz.nip61Nutzaps.token.TokenEvent
import com.vitorpamplona.quartz.nip61Nutzaps.token.build
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The classes merged into another class keep their old companion calls compiling
 * through the deprecated typealiases. If any of these stop resolving, external
 * Quartz users break on upgrade.
 */
class DeprecatedMergedEventApiTest {
    private val nutzap = NutzapEvent("a".repeat(64), "1".repeat(64), 1, emptyArray(), "", "b".repeat(128))

    @Test
    fun tokenEventBuildStillTakesEncryptedContent() {
        val template = TokenEvent.build("encrypted")
        assertEquals(CashuTokenEvent.KIND, template.kind)
        assertEquals("encrypted", template.content)
    }

    @Test
    fun nutzapRedemptionBuildStillAddsRedeemedAndSenderTags() {
        val template = NutzapRedemptionEvent.build(EventHintBundle(nutzap), "encrypted")
        assertEquals(CashuSpendingHistoryEvent.KIND, template.kind)
        assertEquals(listOf("e", "p"), template.tags.map { it[0] })
    }

    @Test
    fun softwareReleaseBuildStillBuildsNip82Release() {
        assertEquals("app@1.0", SoftwareReleaseEvent.buildDTag("app", "1.0"))
        val template = SoftwareReleaseEvent.build("app", "1.0", "main", emptyList())
        assertEquals("app@1.0", template.tags.first { it[0] == "d" }[1])
        assertEquals("1.0", template.tags.first { it[0] == "version" }[1])
    }
}
