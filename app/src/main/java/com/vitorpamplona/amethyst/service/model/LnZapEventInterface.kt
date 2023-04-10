package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.service.model.zaps.ZapAmount

interface LnZapEventInterface : EventInterface {

    fun zappedPost(): List<String>

    fun zappedPollOption(): Int?

    fun zappedAuthor(): List<String>

    fun zappedRequestAuthor(): String?

    fun taggedAddresses(): List<ATag>

    fun amount(): ZapAmount?

    fun containedPost(): Event?

    fun message(): String
}
