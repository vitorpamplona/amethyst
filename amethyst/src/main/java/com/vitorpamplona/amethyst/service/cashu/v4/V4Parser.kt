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
package com.vitorpamplona.amethyst.service.cashu.v4

import com.vitorpamplona.amethyst.service.cashu.CashuToken
import com.vitorpamplona.amethyst.service.cashu.Proof
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

class V4Parser {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun parseCashuB(cashuToken: String): GenericLoadable<ImmutableList<CashuToken>> {
            try {
                val base64token = cashuToken.replace("cashuB", "")
                val parser = Cbor { ignoreUnknownKeys = true }
                val v4Token = parser.decodeFromByteArray<V4Token>(Base64.getUrlDecoder().decode(base64token))
                val v4proofs = v4Token.t ?: return GenericLoadable.Error("No token found")

                val converted =
                    v4proofs.map { id ->
                        val proofs =
                            id.p.map {
                                Proof(
                                    it.a,
                                    id.i.toHexKey(),
                                    it.s,
                                    it.c.toHexKey(),
                                )
                            }
                        val mint = v4Token.m

                        var totalAmount = 0L
                        for (proof in proofs) {
                            totalAmount += proof.amount
                        }

                        CashuToken(cashuToken, mint, totalAmount, proofs)
                    }

                return GenericLoadable.Loaded(converted.toImmutableList())
            } catch (e: Exception) {
                e.printStackTrace()
                if (e is CancellationException) throw e
                return GenericLoadable.Error("Could not parse this cashu token")
            }
        }
    }
}
