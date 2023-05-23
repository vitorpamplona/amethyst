package com.vitorpamplona.amethyst.service.model

import com.google.errorprone.annotations.Immutable
import java.math.BigDecimal

@Immutable
interface LnZapEventInterface : EventInterface {

    fun zappedPost(): List<String>

    fun zappedPollOption(): Int?

    fun zappedAuthor(): List<String>

    fun zappedRequestAuthor(): String?

    fun taggedAddresses(): List<ATag>

    fun amount(): BigDecimal?

    fun containedPost(): Event?
}
