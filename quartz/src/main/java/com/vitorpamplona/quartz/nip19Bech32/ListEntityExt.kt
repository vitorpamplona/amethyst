/**
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
package com.vitorpamplona.quartz.nip19Bech32

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec

fun NEvent.toEventHint() = relay.map { EventIdHint(hex, it) }

fun NAddress.toAddressHint() = relay.map { AddressHint(aTag(), it) }

fun NProfile.toPubKeyHint() = relay.map { PubKeyHint(hex, it) }

fun List<Entity>.eventHints(): List<EventIdHint> =
    mapNotNull { entity ->
        if (entity is NEvent) {
            entity.toEventHint()
        } else {
            null
        }
    }.flatten()

fun List<Entity>.eventIds(): List<HexKey> =
    mapNotNull { entity ->
        when (entity) {
            is NEvent -> entity.hex
            is NNote -> entity.hex
            is NEmbed -> entity.event.id
            else -> null
        }
    }

fun List<Entity>.addressHints(): List<AddressHint> =
    mapNotNull { entity ->
        if (entity is NAddress) {
            entity.toAddressHint()
        } else {
            null
        }
    }.flatten()

fun List<Entity>.addressIds(): List<String> =
    mapNotNull { entity ->
        when (entity) {
            is NAddress -> entity.aTag()
            is NEmbed -> if (entity.event is AddressableEvent) entity.event.addressTag() else null
            else -> null
        }
    }

fun List<Entity>.pubKeyHints(): List<PubKeyHint> =
    mapNotNull { entity ->
        if (entity is NProfile) {
            entity.relay.map { PubKeyHint(entity.hex, it) }
        } else {
            null
        }
    }.flatten()

fun List<Entity>.pubKeys(): List<HexKey> =
    mapNotNull { entity ->
        when (entity) {
            is NProfile -> entity.hex
            is NPub -> entity.hex
            is NSec -> entity.hex
            else -> null
        }
    }
