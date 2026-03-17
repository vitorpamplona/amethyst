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
package com.vitorpamplona.quartz.nip47WalletConnect.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.vitorpamplona.quartz.nip47WalletConnect.CancelHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.CreateConnectionSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.GetBalanceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.GetBudgetSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.GetInfoSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.ListTransactionsSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.LookupInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.MakeHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.MakeInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.PayKeysendSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip47WalletConnect.SettleHoldInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.SignMessageSuccessResponse

class ResponseSerializer : StdSerializer<Response>(Response::class.java) {
    override fun serialize(
        value: Response,
        gen: JsonGenerator,
        provider: SerializerProvider,
    ) {
        gen.writeStartObject()
        if (value.resultType.isNotEmpty()) {
            gen.writeStringField("result_type", value.resultType)
        }
        when (value) {
            is NwcErrorResponse -> {
                if (value.error != null) {
                    gen.writeObjectField("error", value.error)
                }
            }

            is PayInvoiceErrorResponse -> {
                if (value.error != null) {
                    gen.writeObjectField("error", value.error)
                }
            }

            is PayInvoiceSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is PayKeysendSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is MakeInvoiceSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is LookupInvoiceSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is ListTransactionsSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is GetBalanceSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is GetInfoSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is MakeHoldInvoiceSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is CancelHoldInvoiceSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is SettleHoldInvoiceSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is GetBudgetSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is SignMessageSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }

            is CreateConnectionSuccessResponse -> {
                if (value.result != null) {
                    gen.writeObjectField("result", value.result)
                }
            }
        }
        gen.writeEndObject()
    }
}
