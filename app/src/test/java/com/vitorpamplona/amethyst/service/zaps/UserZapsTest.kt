package com.vitorpamplona.amethyst.service.zaps

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.LnZapEventInterface
import com.vitorpamplona.amethyst.service.model.zaps.UserZaps
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.math.BigDecimal

class UserZapsTest {
    @Test
    fun nothing() {
        Assert.assertEquals(1, 1)
    }

    @Test
    fun user_without_zaps() {
        val actual = UserZaps.forProfileFeed(zaps = null)

        Assert.assertEquals(emptyList<Pair<Note, Note>>(), actual)
    }

    @Test
    fun avoid_duplicates_with_same_zap_request() {
        val zapRequest = mockNoteAuthor("user-1")

        val zaps: Map<Note, Note?> = mapOf(
            zapRequest to mockNoteZap(amount = 100),
            zapRequest to mockNoteZap(amount = 200)
        )

        val actual = UserZaps.forProfileFeed(zaps)

        Assert.assertEquals(1, actual.count())
        Assert.assertEquals(zapRequest, actual.first().first)
        Assert.assertEquals(
            BigDecimal(200),
            (actual.first().second.event as LnZapEventInterface).amount()
        )
    }

    @Test
    fun multiple_zap_requests_by_different_users() {
        val zapRequest1 = mockNoteAuthor("user-1")
        val zapRequest2 = mockNoteAuthor("user-2")

        val zaps: Map<Note, Note?> = mapOf(
            zapRequest1 to mockNoteZap(amount = 100),
            zapRequest2 to mockNoteZap(amount = 200)
        )

        val actual = UserZaps.forProfileFeed(zaps)

        Assert.assertEquals(2, actual.count())

        Assert.assertEquals(zapRequest2, actual[0].first)
        Assert.assertEquals(
            BigDecimal(200),
            (actual[0].second.event as LnZapEventInterface).amount()
        )

        Assert.assertEquals(zapRequest1, actual[1].first)
        Assert.assertEquals(
            BigDecimal(100),
            (actual[1].second.event as LnZapEventInterface).amount()
        )
    }

    private fun mockNoteAuthor(pubkeyHex: HexKey): Note {
        val author = mockk<User>()
        every { author.pubkeyHex } returns pubkeyHex

        val zapNote = mockk<Note>()
        every { zapNote.author } returns author

        return zapNote
    }

    private fun mockNoteZap(amount: Int): Note {
        val lnZapEvent = mockk<LnZapEventInterface>()
        every { lnZapEvent.amount() } returns amount.toBigDecimal()

        val zapNote = mockk<Note>()
        every { zapNote.event } returns lnZapEvent

        return zapNote
    }
}
