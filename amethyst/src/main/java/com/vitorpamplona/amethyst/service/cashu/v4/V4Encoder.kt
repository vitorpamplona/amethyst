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
package com.vitorpamplona.amethyst.service.cashu.v4

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import java.util.Base64

/**
 * Inverse of [V4Parser]: encode a list of NIP-60 proofs into a `cashuB` token
 * string suitable for sending out-of-band.
 *
 * V4 (NUT-00) wire format:
 *   "cashuB" + Base64URL(CBOR(V4Token { m, u, d?, t: [V4T { i, p: [V4Proof] }] }))
 */
object V4Encoder {
    @OptIn(ExperimentalSerializationApi::class)
    fun encode(
        mintUrl: String,
        proofs: List<CashuProof>,
        unit: String = "sat",
        memo: String? = null,
    ): String {
        require(proofs.isNotEmpty()) { "Cannot encode an empty token" }

        // Group proofs by keyset id (the v4 wire format groups by keyset).
        val groups =
            proofs.groupBy { it.id }.map { (keysetId, ps) ->
                V4T(
                    i = keysetId.hexToByteArray(),
                    p =
                        ps
                            .map { proof ->
                                V4Proof(
                                    a = proof.amount.toInt(),
                                    s = proof.secret,
                                    c = proof.c.hexToByteArray(),
                                    d = null,
                                    w = null,
                                )
                            }.toTypedArray(),
                )
            }

        val token =
            V4Token(
                m = mintUrl,
                u = unit,
                d = memo,
                t = groups.toTypedArray(),
            )

        val cbor =
            Cbor {
                ignoreUnknownKeys = true
            }
        val bytes = cbor.encodeToByteArray(token)
        val base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return "cashuB$base64"
    }
}
