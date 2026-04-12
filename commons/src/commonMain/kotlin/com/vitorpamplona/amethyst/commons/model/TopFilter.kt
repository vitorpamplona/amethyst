/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.serialization.Serializable

@Serializable
sealed class TopFilter(
    val code: String,
) {
    @Serializable
    object Global : TopFilter(" Global ")

    @Serializable
    object AllFollows : TopFilter(" All Follows ")

    @Serializable
    object AllUserFollows : TopFilter(" All User Follows ")

    @Serializable
    object DefaultFollows : TopFilter(" Main User Follows ")

    @Serializable
    object AroundMe : TopFilter(" Around Me ")

    @Serializable
    object Chess : TopFilter(" Chess ")

    @Serializable
    class PeopleList(
        val address: Address,
    ) : TopFilter(address.toValue())

    @Serializable
    class MuteList(
        val address: Address,
    ) : TopFilter(address.toValue())

    @Serializable
    class Community(
        val address: Address,
    ) : TopFilter("Community/${address.toValue()}")

    @Serializable
    class Hashtag(
        val tag: String,
    ) : TopFilter("Hashtag/$tag")

    @Serializable
    class Geohash(
        val tag: String,
    ) : TopFilter("Geohash/$tag")

    @Serializable
    class Relay(
        val url: String,
    ) : TopFilter("Relay/$url")
}
