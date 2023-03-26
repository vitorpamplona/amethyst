package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.service.model.zaps.ZapAmountInterface

interface LnZapEventInterface : EventInterface {

    fun zappedPost(): List<String>

    fun zappedAuthor(): List<String>

    fun taggedAddresses(): List<ATag>

    fun amount(): ZapAmountInterface?

    fun containedPost(): Event?
}
