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
package com.vitorpamplona.quartz.nip61Nutzaps.nutzap

import com.vitorpamplona.quartz.utils.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * NIP-61 nutzap amount accounting.
 *
 * Each `proof` tag carries a JSON payload that NUT-00 specifies as
 * `{"id": "...", "amount": Long, "secret": "...", "C": "...", ...}`.
 * The amount field is what the sender claims is in the proof. UI
 * surfaces (reaction-row total, notifications card) only need the
 * claimed sum — verifying it would require talking to the mint, which
 * happens lazily at redeem time inside `CashuWalletOps.redeemNutzap`.
 *
 * The parser is lenient: a malformed proof contributes 0 sats rather
 * than throwing, so a single bad proof in a relay echo can't sink the
 * whole event from the cache.
 */
fun NutzapEvent.claimedSatsTotal(): Long =
    proofs().sumOf { proofJson ->
        try {
            NUTZAP_AMOUNT_JSON.decodeFromString<NutzapProofAmount>(proofJson).amount
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w("NutzapEvent") { "claimedSatsTotal: skipping unparseable proof on ${id.take(8)}: ${e.message}" }
            0L
        }
    }

@Serializable
private class NutzapProofAmount(
    val amount: Long = 0L,
)

private val NUTZAP_AMOUNT_JSON =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
