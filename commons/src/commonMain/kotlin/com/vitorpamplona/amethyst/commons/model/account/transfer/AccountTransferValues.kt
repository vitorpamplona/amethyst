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
package com.vitorpamplona.amethyst.commons.model.account.transfer

/**
 * Converts between a platform preference map and the bundle's value shape.
 *
 * Split out of the Android preference code so it can be unit-tested: the type
 * mapping is where an import goes subtly wrong. A `Long` that comes back as an
 * `Int`, or a string set that comes back as a list, throws a
 * ClassCastException deep inside whichever feature reads that key — long after
 * the import reported success.
 */
object AccountTransferValues {
    /**
     * Maps a preference map (`SharedPreferences.all`) to transferable values,
     * dropping the keys [AccountTransferKeys] holds back and any value of a
     * type preferences cannot hold.
     */
    fun fromPreferenceMap(all: Map<String, Any?>): Map<String, TransferValue> = fromMap(all) { AccountTransferKeys.isTransferable(it) }

    /**
     * Same conversion for a store whose keys are opaque to the exclusion list
     * — a DataStore, where nothing is withheld by name.
     */
    fun fromDataStoreMap(all: Map<String, Any?>): Map<String, TransferValue> = fromMap(all) { true }

    private inline fun fromMap(
        all: Map<String, Any?>,
        allowed: (String) -> Boolean,
    ): Map<String, TransferValue> =
        all
            .mapNotNull { (key, value) ->
                if (!allowed(key)) return@mapNotNull null
                toTransferValue(value)?.let { key to it }
            }.toMap()

    private fun toTransferValue(value: Any?): TransferValue? =
        when (value) {
            is String -> TransferValue.Str(value)
            is Boolean -> TransferValue.Bool(value)
            is Int -> TransferValue.Int32(value)
            is Long -> TransferValue.Int64(value)
            is Float -> TransferValue.Flt(value)
            is Double -> TransferValue.Dbl(value)
            // Sorted so the same preferences always produce the same bytes, and
            // filtered because the platform only stores string sets — anything
            // else in there is not something we can put back.
            is Set<*> -> TransferValue.StrSet(value.filterIsInstance<String>().sorted())
            else -> null
        }
}
