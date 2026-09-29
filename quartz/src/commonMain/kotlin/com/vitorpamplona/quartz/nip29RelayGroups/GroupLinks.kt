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
package com.vitorpamplona.quartz.nip29RelayGroups

import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.OrderProps
import com.vitorpamplona.quartz.nip29RelayGroups.tags.AddressPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.EventPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupPin

/**
 * A NIP-29 pin list (the 9010 request and the relay's 39005): `e` and `a` pins interleaved in
 * display order. A graph keeps no edge order, so each [Relation.PIN] carries its position in
 * [pins] as `order`.
 */
fun LinkBuilder.groupPinLinks(pins: List<GroupPin>) =
    pins.forEachIndexed { index, pin ->
        val order = OrderProps(index)
        when (pin) {
            is EventPin -> event(Relation.PIN, pin.eventId, EventPin.TAG_NAME, order)
            is AddressPin -> address(Relation.PIN, pin.address, AddressPin.TAG_NAME, order)
        }
    }
