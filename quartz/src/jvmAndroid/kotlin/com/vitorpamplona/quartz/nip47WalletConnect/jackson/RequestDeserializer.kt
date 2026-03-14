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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.vitorpamplona.quartz.nip47WalletConnect.CancelHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.CreateConnectionMethod
import com.vitorpamplona.quartz.nip47WalletConnect.GetBalanceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.GetBudgetMethod
import com.vitorpamplona.quartz.nip47WalletConnect.GetInfoMethod
import com.vitorpamplona.quartz.nip47WalletConnect.ListTransactionsMethod
import com.vitorpamplona.quartz.nip47WalletConnect.LookupInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.MakeHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.MakeInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.NwcMethod
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.PayKeysendMethod
import com.vitorpamplona.quartz.nip47WalletConnect.Request
import com.vitorpamplona.quartz.nip47WalletConnect.SettleHoldInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.SignMessageMethod
import com.vitorpamplona.quartz.utils.asTextOrNull

class RequestDeserializer : StdDeserializer<Request>(Request::class.java) {
    override fun deserialize(
        jp: JsonParser,
        ctxt: DeserializationContext,
    ): Request? {
        val jsonObject: JsonNode = jp.codec.readTree(jp)
        val method = jsonObject.get("method")?.asTextOrNull()

        return when (method) {
            NwcMethod.PAY_INVOICE -> jp.codec.treeToValue(jsonObject, PayInvoiceMethod::class.java)
            NwcMethod.PAY_KEYSEND -> jp.codec.treeToValue(jsonObject, PayKeysendMethod::class.java)
            NwcMethod.MAKE_INVOICE -> jp.codec.treeToValue(jsonObject, MakeInvoiceMethod::class.java)
            NwcMethod.LOOKUP_INVOICE -> jp.codec.treeToValue(jsonObject, LookupInvoiceMethod::class.java)
            NwcMethod.LIST_TRANSACTIONS -> jp.codec.treeToValue(jsonObject, ListTransactionsMethod::class.java)
            NwcMethod.GET_BALANCE -> jp.codec.treeToValue(jsonObject, GetBalanceMethod::class.java)
            NwcMethod.GET_INFO -> jp.codec.treeToValue(jsonObject, GetInfoMethod::class.java)
            NwcMethod.GET_BUDGET -> jp.codec.treeToValue(jsonObject, GetBudgetMethod::class.java)
            NwcMethod.SIGN_MESSAGE -> jp.codec.treeToValue(jsonObject, SignMessageMethod::class.java)
            NwcMethod.CREATE_CONNECTION -> jp.codec.treeToValue(jsonObject, CreateConnectionMethod::class.java)
            NwcMethod.MAKE_HOLD_INVOICE -> jp.codec.treeToValue(jsonObject, MakeHoldInvoiceMethod::class.java)
            NwcMethod.CANCEL_HOLD_INVOICE -> jp.codec.treeToValue(jsonObject, CancelHoldInvoiceMethod::class.java)
            NwcMethod.SETTLE_HOLD_INVOICE -> jp.codec.treeToValue(jsonObject, SettleHoldInvoiceMethod::class.java)
            else -> null
        }
    }
}
