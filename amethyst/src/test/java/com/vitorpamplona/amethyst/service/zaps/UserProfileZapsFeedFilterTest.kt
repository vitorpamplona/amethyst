/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service.zaps

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.dal.UserProfileZapsFeedFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.math.BigDecimal

class UserProfileZapsFeedFilterTest {
    @Test
    fun nothing() {
        Assert.assertEquals(1, 1)
    }

    @Test
    fun user_without_zaps() {
        val actual = UserProfileZapsFeedFilter.forProfileFeed(zaps = null)

        Assert.assertEquals(emptyList<Pair<Note, Note>>(), actual)
    }

    @Test
    fun avoid_duplicates_with_same_zap_request() {
        val zapRequest = mockk<Note>()

        val zaps: Map<Note, Note?> =
            mapOf(
                zapRequest to mockZapNoteWith("user-1", amount = 100),
                zapRequest to mockZapNoteWith("user-1", amount = 200),
            )

        val actual = UserProfileZapsFeedFilter.forProfileFeed(zaps)

        Assert.assertEquals(1, actual.count())
        Assert.assertEquals(zapRequest, actual.first().zapRequest)
        Assert.assertEquals(
            BigDecimal(200),
            (actual.first().zapEvent.event as LnZapEvent).amount(),
        )
    }

    private fun mockZapNoteWith(
        pubkey: HexKey,
        amount: Int,
    ): Note {
        val lnZapEvent = mockk<LnZapEvent>()
        every { lnZapEvent.amount() } returns amount.toBigDecimal()
        every { lnZapEvent.pubKey } returns pubkey

        val zapNote = mockk<Note>()
        every { zapNote.event } returns lnZapEvent

        return zapNote
    }
}
