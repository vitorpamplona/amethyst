package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.service.model.zaps.ZapAmount

interface LnZapEventInterface : EventInterface {

    fun zappedPost(): List<String>

    fun zappedAuthor(): List<String>

    fun taggedAddresses(): List<ATag>

    fun amount(): ZapAmount?

    fun containedPost(): Event?
}
