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
package com.vitorpamplona.amethyst.model

import kotlin.IllegalArgumentException

class PendingTransaction(val handle: Long) {
    enum class StatusType {
        OK,
        ERROR,
        CRITICAL,
    }

    class Status(val type: StatusType, val error: String) {
        fun isOk(): Boolean = type == StatusType.OK
    }

    val status: Status
        get() = statusGet()

    private fun statusGet(): Status {
        val status = getStatusJ()
        return Status(StatusType.entries.first { it.ordinal == status }, getErrorString())
    }

    private external fun getStatusJ(): Int

    private external fun getErrorString(): String

    private var savedTxId: String? = null

    val txId: String
        get() = savedTxId ?: throw IllegalStateException("txId unavailable")

    fun saveTxId() {
        savedTxId = getFirstTxIdJ() ?: throw IllegalArgumentException("Invalid handle")
    }

    private external fun getFirstTxIdJ(): String?

    fun commit(
        filename: String = "",
        overwrite: Boolean = false,
    ): Boolean {
        return commitJ(filename, overwrite)
    }

    private external fun commitJ(
        filename: String,
        overwrite: Boolean,
    ): Boolean
}
