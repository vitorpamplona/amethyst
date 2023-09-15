package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.ui.components.Split
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitterTest {
    @Test
    fun testSplit() {
        val mySplit = Split<String>()

        val vitor = mySplit.addItem("Vitor")

        assertEquals(1f, mySplit.items[vitor].percentage, 0.01f)
        assertTrue(mySplit.isEqualSplit())

        val pablo = mySplit.addItem("Pablo")

        assertEquals(0.5f, mySplit.items[pablo].percentage, 0.01f)
        assertEquals(0.5f, mySplit.items[vitor].percentage, 0.01f)
        assertTrue(mySplit.isEqualSplit())

        val gigi = mySplit.addItem("Gigi")

        assertEquals(0.33f, mySplit.items[vitor].percentage, 0.01f)
        assertEquals(0.33f, mySplit.items[pablo].percentage, 0.01f)
        assertEquals(0.33f, mySplit.items[gigi].percentage, 0.01f)
        assertTrue(mySplit.isEqualSplit())

        mySplit.updatePercentage(vitor, 0.5f)

        assertEquals(0.5f, mySplit.items[vitor].percentage, 0.01f)
        assertEquals(0.33f, mySplit.items[pablo].percentage, 0.01f)
        assertEquals(0.16f, mySplit.items[gigi].percentage, 0.01f)
        assertFalse(mySplit.isEqualSplit())

        mySplit.updatePercentage(vitor, 0.95f)

        assertEquals(0.95f, mySplit.items[vitor].percentage, 0.01f)
        assertEquals(0.05f, mySplit.items[pablo].percentage, 0.01f)
        assertEquals(0.0f, mySplit.items[gigi].percentage, 0.01f)
        assertFalse(mySplit.isEqualSplit())

        mySplit.updatePercentage(vitor, 0.15f)

        assertEquals(0.15f, mySplit.items[vitor].percentage, 0.01f)
        assertEquals(0.05f, mySplit.items[pablo].percentage, 0.01f)
        assertEquals(0.80f, mySplit.items[gigi].percentage, 0.01f)
        assertFalse(mySplit.isEqualSplit())

        mySplit.updatePercentage(pablo, 0.95f)

        assertEquals(0.05f, mySplit.items[vitor].percentage, 0.01f)
        assertEquals(0.95f, mySplit.items[pablo].percentage, 0.01f)
        assertEquals(0.00f, mySplit.items[gigi].percentage, 0.01f)

        mySplit.updatePercentage(gigi, 1f)

        assertEquals(0.00f, mySplit.items[vitor].percentage, 0.01f)
        assertEquals(0.00f, mySplit.items[pablo].percentage, 0.01f)
        assertEquals(1.00f, mySplit.items[gigi].percentage, 0.01f)

        mySplit.updatePercentage(vitor, 0.5f)

        assertEquals(0.50f, mySplit.items[vitor].percentage, 0.01f)
        assertEquals(0.00f, mySplit.items[pablo].percentage, 0.01f)
        assertEquals(0.50f, mySplit.items[gigi].percentage, 0.01f)

        mySplit.updatePercentage(pablo, 0.3f)

        assertEquals(0.50f, mySplit.items[vitor].percentage, 0.01f)
        assertEquals(0.30f, mySplit.items[pablo].percentage, 0.01f)
        assertEquals(0.20f, mySplit.items[gigi].percentage, 0.01f)

        mySplit.updatePercentage(gigi, 1f)

        assertEquals(0.00f, mySplit.items[vitor].percentage, 0.01f)
        assertEquals(0.00f, mySplit.items[pablo].percentage, 0.01f)
        assertEquals(1.00f, mySplit.items[gigi].percentage, 0.01f)

        mySplit.updatePercentage(gigi, 0.5f)

        assertEquals(0.00f, mySplit.items[vitor].percentage, 0.01f)
        assertEquals(0.50f, mySplit.items[pablo].percentage, 0.01f)
        assertEquals(0.50f, mySplit.items[gigi].percentage, 0.01f)
    }
}
