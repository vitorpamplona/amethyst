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
package com.vitorpamplona.quartz.nip47WalletConnect

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

class ResponseDeserializer : StdDeserializer<Response>(Response::class.java) {
    override fun deserialize(
        jp: JsonParser,
        ctxt: DeserializationContext,
    ): Response? {
        val jsonObject: JsonNode = jp.codec.readTree(jp)
        val resultType = jsonObject.get("result_type")?.asText()

        if (resultType == "pay_invoice") {
            val result = jsonObject.get("result")
            val error = jsonObject.get("error")
            if (result != null) {
                return jp.codec.treeToValue(jsonObject, PayInvoiceSuccessResponse::class.java)
            }
            if (error != null) {
                return jp.codec.treeToValue(jsonObject, PayInvoiceErrorResponse::class.java)
            }
        } else {
            // tries to guess
            if (jsonObject.get("result")?.get("preimage") != null) {
                return jp.codec.treeToValue(jsonObject, PayInvoiceSuccessResponse::class.java)
            }
            if (jsonObject.get("error")?.get("code") != null) {
                return jp.codec.treeToValue(jsonObject, PayInvoiceErrorResponse::class.java)
            }
        }
        return null
    }
}
