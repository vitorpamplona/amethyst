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
package com.vitorpamplona.amethyst.commons.service.lnurl

import com.vitorpamplona.amethyst.commons.notices.InvoiceNotice
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

// Jackson's JsonNode readers, which LNURL servers' loosely-typed replies were written against:
// asText() gives a scalar's text and "" for a container; asBoolean() reads true, a non-zero
// number or the text "true". JSON null reads as absent rather than as the text "null".

internal fun JsonElement.asText(): String? =
    when (this) {
        is JsonNull -> null
        is JsonPrimitive -> content
        else -> ""
    }

internal fun JsonElement.isBoolean(): Boolean = this is JsonPrimitive && !isString && booleanOrNull != null

internal fun JsonElement.asBoolean(): Boolean =
    when {
        this !is JsonPrimitive || this is JsonNull -> false
        isString -> content.trim() == "true"
        else -> booleanOrNull ?: longOrNull?.let { it != 0L } ?: false
    }

/**
 * A LUD-06 `minSendable`/`maxSendable` bound in millisats, or null when it is absent, not a
 * whole number or not positive, so a malformed bound never blocks a payment.
 */
private fun JsonElement.asMilliSatsBound(): Long? = asText()?.trim()?.toLongOrNull()?.takeIf { it > 0 }

/**
 * Why [milliSats] is outside this LNURL-pay response's sendable range, in whole sats, or null
 * when it fits. The bounds are millisats, which servers quote as-is in their own refusals
 * ("between 5000 and …" for a 5-sat minimum), so the caller reports this instead of asking.
 * The minimum rounds up and the maximum down, to the nearest whole sat a payment can use.
 */
internal fun JsonObject.sendableRangeNotice(
    lnAddress: String,
    milliSats: Long,
): InvoiceNotice? {
    val min = this["minSendable"]?.asMilliSatsBound()
    if (min != null && milliSats < min) return InvoiceNotice.AmountBelowMinimum(lnAddress, (min + 999) / 1000)
    val max = this["maxSendable"]?.asMilliSatsBound()
    if (max != null && milliSats > max) return InvoiceNotice.AmountAboveMaximum(lnAddress, max / 1000)
    return null
}
