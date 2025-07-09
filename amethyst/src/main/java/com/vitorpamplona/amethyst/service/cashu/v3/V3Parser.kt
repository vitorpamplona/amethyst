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
package com.vitorpamplona.amethyst.service.cashu.v3

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.service.cashu.CashuToken
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

class V3Parser {
    companion object Companion {
        fun parseCashuA(cashuToken: String): GenericLoadable<ImmutableList<CashuToken>> {
            try {
                val base64token = cashuToken.replace("cashuA", "")
                val cashu = jacksonObjectMapper().readValue<V3Token>(String(Base64.getDecoder().decode(base64token)))

                if (cashu.token == null) {
                    return GenericLoadable.Error("No token found")
                }

                val converted =
                    cashu.token.map { token ->
                        val proofs = token.proofs
                        val mint = token.mint

                        var totalAmount = 0L
                        for (proof in proofs) {
                            totalAmount += proof.amount
                        }

                        CashuToken(cashuToken, mint, totalAmount, proofs)
                    }

                return GenericLoadable.Loaded(converted.toImmutableList())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return GenericLoadable.Error("Could not parse this cashu token")
            }
        }
    }
}
