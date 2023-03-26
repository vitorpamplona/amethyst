package com.vitorpamplona.amethyst.service.model.zaps

import java.math.BigDecimal

class ZapAmount(var amount: BigDecimal?) {

    fun total(): BigDecimal? {
        return amount
    }
}
