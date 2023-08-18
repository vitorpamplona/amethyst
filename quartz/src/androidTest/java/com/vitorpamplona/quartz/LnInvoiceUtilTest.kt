package com.vitorpamplona.quartz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.encoders.LnInvoiceUtil
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LnInvoiceUtilTest {
    @Test
    fun test100KAmountCalculation() {
        val bolt11 = "lnbc1m1pjt9u0qsp553q90pj5mafzv20w45eqavned9tgwhl4q99n9s5ppcw24nzw3zeqpp5002kd3ktym67du86kj665fgaev7ka8ys7j5yz5fg686lr5e2gfkshp5dkk27nnuax05az3pk2r6ytxtvwn5j4xzsq9ajprhc7crjkmgvr3qxqyjw5qcqpjrzjqtzxvfsuxe4l92pf97tt4rcgpy2xalkmlwexh899wqxf83l8nwv4xzh0gvqq89qqqqqqqqlgqqqqq0gqvs9qxpqysgqx5mz04wd7kqu5zhhel9enr036hjrp4gga0nz084p2asjl36a0zmrk6mhqa249zsgqref2rlvhffm73u7rxgr47gden6rugup4ksvpzsqvds4pz"
        // Context of the app under test.
        Assert.assertEquals(100000, LnInvoiceUtil.getAmountInSats(bolt11).toLong())
    }

    @Test
    fun test100GAmountCalculation() {
        val bolt11 = "lnbc1000000000000000p1pjtxqf0pp5myqxhcufqy56elfsg9dd4dthnqptusnnpwnul7u86l95xzjgqd8shp5gueg34sgm3u3nxqjqyunvvqdu0pr6jz6mwh4ew4886f2lpf4cmrqxqztgsp5w0cdfd45dfnqwex5gn85x7fru3jcrxhlcx3enx835477m3gdfcuq9qyyssqelrcmm7p9qazgjuxtdg7sd8nq5cscl2tratjlclt5rk5mc7uq2lphq3r2a43j5ua4leakc4emq8yp2yxdnzvzszpw6u2afac0kgl7hspfj67ta"
        // Context of the app under test.
        Assert.assertEquals(100000000000, LnInvoiceUtil.getAmountInSats(bolt11).toLong())
    }
}
