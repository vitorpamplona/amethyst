/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip19Bech32Entities

import android.util.Log
import com.vitorpamplona.quartz.crypto.Hex
import com.vitorpamplona.quartz.nip01Core.addressables.ATag
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NAddress

fun ATag.Companion.isATag(key: String): Boolean = key.startsWith("naddr1") || key.contains(":")

fun ATag.Companion.parse(
    address: String,
    relay: String?,
): ATag? =
    if (address.startsWith("naddr") || address.startsWith("nostr:naddr")) {
        parseNAddr(address)
    } else {
        parseAtag(address, relay)
    }

fun ATag.Companion.parseAtag(
    atag: String,
    relay: String?,
): ATag? =
    try {
        val parts = atag.split(":", limit = 3)
        Hex.decode(parts[1])
        ATag(parts[0].toInt(), parts[1], parts[2], relay)
    } catch (t: Throwable) {
        Log.w("ATag", "Error parsing A Tag: $atag: ${t.message}")
        null
    }

fun ATag.Companion.parseAtagUnckecked(atag: String): ATag? =
    try {
        val parts = atag.split(":")
        ATag(parts[0].toInt(), parts[1], parts[2], null)
    } catch (t: Throwable) {
        null
    }

fun ATag.toNAddr(overrideRelay: String? = relay): String = NAddress.create(kind, pubKeyHex, dTag, overrideRelay ?: relay)

fun ATag.Companion.parseNAddr(naddr: String) =
    NAddress.parse(naddr)?.let { result ->
        ATag(result.kind, result.author, result.dTag, result.relay.firstOrNull())
    }
