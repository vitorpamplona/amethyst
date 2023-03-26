package com.vitorpamplona.amethyst.service.zaps

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.LnZapEventInterface
import com.vitorpamplona.amethyst.service.model.zaps.UserZaps
import com.vitorpamplona.amethyst.service.model.zaps.ZapAmount
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.math.BigDecimal

class UserZapsTest {
    @Test
    fun user_without_zaps() {
        val actual = UserZaps.forProfileFeed(zaps = null)

        Assert.assertEquals(emptyList<Pair<Note, Note>>(), actual)
    }

    @Test
    fun avoid_duplicates_with_same_zap_request() {
        val zapAuthor = mockNoteAuthor("user-1")

        val zaps: Map<Note, Note?> = mapOf(
            zapAuthor to mockNoteZap(amount = 100),
            zapAuthor to mockNoteZap(amount = 200)
        )

        val actual = UserZaps.forProfileFeed(zaps)

        Assert.assertEquals(1, actual.count())
        Assert.assertEquals(zapAuthor, actual.first().first)
        Assert.assertEquals(
            BigDecimal(200),
            (actual.first().second.event as LnZapEventInterface).amount()?.total()
        )
    }

    @Test
    fun multiple_zap_requests_by_different_users() {
        val zapAuthorNote1 = mockNoteAuthor("user-1")
        val zapAuthorNote2 = mockNoteAuthor("user-2")

        val zaps: Map<Note, Note?> = mapOf(
            zapAuthorNote1 to mockNoteZap(amount = 100),
            zapAuthorNote2 to mockNoteZap(amount = 200)
        )

        val actual = UserZaps.forProfileFeed(zaps)

        Assert.assertEquals(2, actual.count())

        Assert.assertEquals(zapAuthorNote2, actual[0].first)
        Assert.assertEquals(
            BigDecimal(200),
            (actual[0].second.event as LnZapEventInterface).amount()?.total()
        )

        Assert.assertEquals(zapAuthorNote1, actual[1].first)
        Assert.assertEquals(
            BigDecimal(100),
            (actual[1].second.event as LnZapEventInterface).amount()?.total()
        )
    }

    @Test
    fun group_multiple_zap_requests_by_same_users() {
        val zapAuthorNote1 = mockNoteAuthor("user-1")
        val zapAuthorNote2 = mockNoteAuthor("user-1")

        val zaps: Map<Note, Note?> = mapOf(
            zapAuthorNote1 to mockNoteZap(amount = 100),
            zapAuthorNote2 to mockNoteZap(amount = 200)
        )

        val actual = UserZaps.forProfileFeed(zaps)

        Assert.assertEquals(1, actual.count())

        Assert.assertEquals(zapAuthorNote1, actual[0].first)
        Assert.assertEquals(
            BigDecimal(300),
            (actual[0].second.event as LnZapEventInterface).amount()?.total()
        )
    }

    private fun mockNoteAuthor(pubkeyHex: HexKey): Note {
        val author = mockk<User>()
        every { author.pubkeyHex } returns pubkeyHex

        val zapNote = mockk<Note>()
        every { zapNote.author } returns author
        every { zapNote.clone() } returns zapNote

        return zapNote
    }

    private fun mockNoteZap(amount: Int): Note {
        val zapAmount = ZapAmount(amount.toBigDecimal())

        val lnZapEvent = mockk<LnZapEventInterface>()
        every { lnZapEvent.amount() } returns zapAmount

        val zapNote = mockk<Note>()
        every { zapNote.event } returns lnZapEvent
        every { zapNote.clone() } returns zapNote

        return zapNote
    }
}
