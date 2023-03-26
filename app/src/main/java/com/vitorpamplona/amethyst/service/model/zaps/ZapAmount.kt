package com.vitorpamplona.amethyst.service.model.zaps

import java.math.BigDecimal

interface ZapAmountInterface {
    fun total(): BigDecimal?
}

class ZapAmount(var amount: BigDecimal?) : ZapAmountInterface {

    override fun total(): BigDecimal? {
        return amount
    }
}
