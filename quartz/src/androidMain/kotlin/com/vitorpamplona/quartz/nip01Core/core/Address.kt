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
package com.vitorpamplona.quartz.nip01Core.core

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.Stable

@Stable
actual data class Address actual constructor(
    actual val kind: Kind,
    actual val pubKeyHex: HexKey,
    actual val dTag: String,
) : Comparable<Address>,
    Parcelable {
    actual fun toValue() = assemble(kind, pubKeyHex, dTag)

    actual override fun compareTo(other: Address): Int {
        val kindComparison = kind.compareTo(other.kind)
        return if (kindComparison == 0) {
            val pubkeyComparison = pubKeyHex.compareTo(other.pubKeyHex)
            if (pubkeyComparison == 0) {
                dTag.compareTo(other.dTag)
            } else {
                pubkeyComparison
            }
        } else {
            kindComparison
        }
    }

    actual companion object {
        // -----------
        // Manual Parcelable implementation to avoid issues between kmp and the parcelize plugin
        // -----------
        @JvmField
        val CREATOR =
            object : Parcelable.Creator<Address> {
                // Create an instance from the Parcel
                override fun createFromParcel(parcel: Parcel): Address = Address(parcel)

                // Create a new array of the Parcelable class
                override fun newArray(size: Int): Array<Address?> = arrayOfNulls(size)
            }

        actual fun assemble(
            kind: Int,
            pubKeyHex: HexKey,
            dTag: String,
        ) = AddressSerializer.assemble(kind, pubKeyHex, dTag)

        actual fun parse(addressId: String) = AddressSerializer.parse(addressId)

        actual fun isOfKind(
            addressId: String,
            kind: String,
        ) = addressId.startsWith(kind) && addressId[kind.length] == ':'
    }

    // -----------
    // Manual Parcelable implementation to avoid issues between kmp and the parcelize plugin
    // -----------
    override fun writeToParcel(
        parcel: Parcel,
        flags: Int,
    ) {
        parcel.writeInt(kind)
        parcel.writeString(pubKeyHex)
        parcel.writeString(dTag)
    }

    override fun describeContents() = 0

    // 4. Primary constructor for deserialization
    constructor(parcel: Parcel) : this(
        kind = parcel.readInt(),
        pubKeyHex = parcel.readString() ?: "",
        dTag = parcel.readString() ?: "",
    )
}
