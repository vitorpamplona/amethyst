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
package com.vitorpamplona.quartz.experimental.walletScrutiny.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * WalletScrutiny's `["x", "<sha256>", "<file name>"]`: one release file by its SHA-256. On an
 * asset bundle (kind 9401) every file of the bundle has one, with its name; on a verification
 * (kind 30301) the `x` tags repeat the registered download hashes as a join key, usually without
 * the name. A hash that is not 64 hex chars is skipped: it could not match any file.
 */
@Immutable
data class AssetFileTag(
    val hash: HexKey,
    val filename: String? = null,
) {
    fun toTagArray() = assemble(hash, filename)

    companion object {
        const val TAG_NAME = "x"

        fun parse(tag: Array<String>): AssetFileTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64 && Hex.isHex64(tag[1])) { return null }
            return AssetFileTag(tag[1].lowercase(), tag.getOrNull(2)?.ifEmpty { null })
        }

        fun parseHash(tag: Array<String>): HexKey? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64 && Hex.isHex64(tag[1])) { return null }
            return tag[1].lowercase()
        }

        fun assemble(
            hash: HexKey,
            filename: String? = null,
        ) = arrayOfNotNull(TAG_NAME, hash, filename?.ifEmpty { null })
    }
}
