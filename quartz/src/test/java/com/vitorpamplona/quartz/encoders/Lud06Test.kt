package com.vitorpamplona.quartz.encoders

import org.junit.Assert.assertEquals
import org.junit.Test

class Lud06Test {
    val lnTips = "LNURL1DP68GURN8GHJ7MRW9E6XJURN9UH8WETVDSKKKMN0WAHZ7MRWW4EXCUP0XPURXEFEX9SKGCT9V5ER2V33X4NRGP2NE42"
    @Test()
    fun parseLnUrlp() {
        assertEquals("https://ln.tips/.well-known/lnurlp/0x3e91adaee25215f4", Lud06().toLnUrlp(lnTips))
        assertEquals("0x3e91adaee25215f4@ln.tips", Lud06().toLud16(lnTips))
    }
}