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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The parameters produced by encrypting + uploading a new Marmot group avatar,
 * ready to be folded into a [com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData]
 * via [com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData.withImage].
 */
class MarmotGroupIconUpload(
    /** SHA-256 (hex) of the encrypted blob = its Blossom content hash. */
    val imageHash: HexKey,
    /** 32-byte HKDF seed for the image AEAD key (MIP-01 v2). */
    val imageKey: ByteArray,
    /** 12-byte nonce. */
    val imageNonce: ByteArray,
    /** 32-byte HKDF seed for the Blossom-auth keypair (MIP-01 v2). */
    val imageUploadKey: ByteArray,
    /**
     * Media type of the DECRYPTED image.
     *
     * MIP-01's blob never carried one, but the current profile's `0x8002`
     * component requires it on a present image — and it is bound into the
     * AEAD's AAD there, so a receiver cannot be steered into decoding the
     * plaintext as a different type than the uploader meant.
     */
    val mediaType: String,
)

/**
 * How a metadata update should treat the group icon.
 */
sealed class MarmotGroupIconChange {
    /** Leave the existing icon (if any) untouched. */
    data object Keep : MarmotGroupIconChange()

    /** Remove the current icon. */
    data object Clear : MarmotGroupIconChange()

    /** Replace the icon with a freshly-uploaded one. */
    class Set(
        val upload: MarmotGroupIconUpload,
    ) : MarmotGroupIconChange()
}
