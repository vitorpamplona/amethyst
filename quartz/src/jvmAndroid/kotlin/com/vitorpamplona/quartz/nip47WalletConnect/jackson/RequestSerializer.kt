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
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.CancelHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.CreateConnectionMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ListTransactionsMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.LookupInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayKeysendMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SettleHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.SignMessageMethod

class RequestSerializer : StdSerializer<Request>(Request::class.java) {
    override fun serialize(
        value: Request,
        gen: JsonGenerator,
        provider: SerializerProvider,
    ) {
        gen.writeStartObject()
        if (value.method != null) {
            gen.writeStringField("method", value.method)
        }
        when (value) {
            is PayInvoiceMethod -> {
                if (value.params != null) {
                    gen.writeObjectField("params", value.params)
                }
            }

            is PayKeysendMethod -> {
                if (value.params != null) {
                    gen.writeObjectField("params", value.params)
                }
            }

            is MakeInvoiceMethod -> {
                if (value.params != null) {
                    gen.writeObjectField("params", value.params)
                }
            }

            is LookupInvoiceMethod -> {
                if (value.params != null) {
                    gen.writeObjectField("params", value.params)
                }
            }

            is ListTransactionsMethod -> {
                if (value.params != null) {
                    gen.writeObjectField("params", value.params)
                }
            }

            is MakeHoldInvoiceMethod -> {
                if (value.params != null) {
                    gen.writeObjectField("params", value.params)
                }
            }

            is CancelHoldInvoiceMethod -> {
                if (value.params != null) {
                    gen.writeObjectField("params", value.params)
                }
            }

            is SettleHoldInvoiceMethod -> {
                if (value.params != null) {
                    gen.writeObjectField("params", value.params)
                }
            }

            is SignMessageMethod -> {
                if (value.params != null) {
                    gen.writeObjectField("params", value.params)
                }
            }

            is CreateConnectionMethod -> {
                if (value.params != null) {
                    gen.writeObjectField("params", value.params)
                }
            }
        }
        gen.writeEndObject()
    }
}
