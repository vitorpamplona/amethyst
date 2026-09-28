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
package com.vitorpamplona.quartz.nip29RelayGroups.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.utils.Hex

/**
 * One entry of a NIP-29 group pin list — the kind-9010 `update-pin-list` write and the
 * relay-signed kind-39005 read side. Pins are either a regular event (`e` tag, event id
 * hex) or an addressable event (`a` tag, `<kind>:<pubkey>:<d>`), interleaved in display
 * order.
 *
 * [ref] is the pin's identity (the id or the address value), so a list can be edited
 * without caring which kind of reference each entry is. Any extra tag values (relay hint,
 * marker…) are kept verbatim so re-submitting another client's pins doesn't lose them.
 */
@Immutable
sealed interface GroupPin {
    /** The event id hex (for `e`) or the `kind:pubkey:d` address value (for `a`). */
    val ref: String

    fun toTagArray(): Array<String>

    companion object {
        fun parse(tag: Array<String>): GroupPin? = EventPin.parse(tag) ?: AddressPin.parse(tag)

        /**
         * A pin from a user-facing reference: a 64-hex id, `note1…` or `nevent1…` becomes an `e`
         * pin; `naddr1…` or a raw `<kind>:<pubkey>:<d>` becomes an `a` pin. Null when unparseable.
         */
        fun fromReference(input: String): GroupPin? {
            val trimmed = input.trim().removePrefix("nostr:")
            if (trimmed.length == 64 && Hex.isHex64(trimmed)) return EventPin(trimmed.lowercase())
            if (':' in trimmed) return Address.parse(trimmed)?.let { AddressPin(it) }
            return when (val entity = Nip19Parser.uriToRoute(trimmed)?.entity) {
                is NAddress -> AddressPin(entity.address())
                is NEvent -> EventPin(entity.hex)
                is NNote -> EventPin(entity.hex)
                else -> null
            }
        }
    }
}

@Immutable
class EventPin(
    val eventId: HexKey,
    val extras: List<String> = emptyList(),
) : GroupPin {
    override val ref: String get() = eventId

    override fun toTagArray() = arrayOf(TAG_NAME, eventId, *extras.toTypedArray())

    override fun equals(other: Any?) = other is EventPin && other.eventId == eventId

    override fun hashCode() = eventId.hashCode()

    override fun toString() = "EventPin($eventId)"

    companion object {
        const val TAG_NAME = "e"

        fun parse(tag: Array<String>): EventPin? {
            if (!tag.has(1) || tag[0] != TAG_NAME) return null
            if (tag[1].length != 64 || !Hex.isHex64(tag[1])) return null
            return EventPin(tag[1], if (tag.size > 2) tag.copyOfRange(2, tag.size).asList() else emptyList())
        }
    }
}

@Immutable
class AddressPin(
    val address: Address,
    val extras: List<String> = emptyList(),
) : GroupPin {
    override val ref: String get() = address.toValue()

    override fun toTagArray() = arrayOf(TAG_NAME, address.toValue(), *extras.toTypedArray())

    override fun equals(other: Any?) = other is AddressPin && other.ref == ref

    override fun hashCode() = ref.hashCode()

    override fun toString() = "AddressPin($ref)"

    companion object {
        const val TAG_NAME = "a"

        fun parse(tag: Array<String>): AddressPin? {
            if (!tag.has(1) || tag[0] != TAG_NAME) return null
            val address = Address.parse(tag[1]) ?: return null
            return AddressPin(address, if (tag.size > 2) tag.copyOfRange(2, tag.size).asList() else emptyList())
        }
    }
}
