package com.vitorpamplona.amethyst.service.model

import java.math.BigDecimal

interface LnZapEventInterface : EventInterface {

    fun zappedPost(): List<String>

    fun zappedAuthor(): List<String>

    fun taggedAddresses(): List<ATag>

    fun amount(): BigDecimal?

    fun containedPost(): Event?

    fun message(): String
}
