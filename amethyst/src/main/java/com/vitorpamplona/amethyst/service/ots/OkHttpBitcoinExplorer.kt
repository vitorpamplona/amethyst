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
package com.vitorpamplona.amethyst.service.ots

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.quartz.nip03Timestamp.ots.BitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.BlockHeader
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.UrlException
import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpBitcoinExplorer(
    val baseAPI: String,
    val client: OkHttpClient,
    val cache: OtsBlockHeightCache,
) : BitcoinExplorer {
    /**
     * Retrieve the block information from the block hash.
     *
     * @param hash Hash of the block.
     * @return the blockheader of the hash
     * @throws Exception desc
     */
    override fun block(hash: String): BlockHeader {
        cache.cacheHeaders.get(hash)?.let {
            return it
        }

        val url = "$baseAPI/block/$hash"

        val request =
            Request
                .Builder()
                .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .url(url)
                .get()
                .build()

        client.newCall(request).execute().use {
            if (it.isSuccessful) {
                Log.d("OkHttpBlockstreamExplorer", "$baseAPI/block/$hash")

                val jsonObject = jacksonObjectMapper().readTree(it.body.string())

                val blockHeader = BlockHeader()
                blockHeader.merkleroot = jsonObject["merkle_root"].asText()
                blockHeader.setTime(jsonObject["timestamp"].asInt().toString())
                blockHeader.blockHash = hash
                cache.cacheHeaders.put(hash, blockHeader)
                return blockHeader
            } else {
                throw UrlException(
                    "Couldn't open $url: " + it.message + " " + it.code,
                )
            }
        }
    }

    /**
     * Retrieve the block hash from the block height.
     *
     * @param height Height of the block.
     * @return the hash of the block at height height
     * @throws Exception desc
     */
    @Throws(Exception::class)
    override fun blockHash(height: Int): String {
        cache.cacheHeights[height]?.let {
            return it
        }

        val url = "$baseAPI/block-height/$height"

        val request =
            Request
                .Builder()
                .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                .url(url)
                .get()
                .build()

        client.newCall(request).execute().use {
            if (it.isSuccessful) {
                val blockHash = it.body.string()

                Log.d("OkHttpBlockstreamExplorer", "$url $blockHash")

                cache.cacheHeights.put(height, blockHash)
                return blockHash
            } else {
                throw UrlException("Couldn't open $url: " + it.message + " " + it.code)
            }
        }
    }

    companion object {
        // doesn't accept Tor
        const val BLOCKSTREAM_API_URL = "https://blockstream.info/api"

        // accepts Tor
        const val MEMPOOL_API_URL = "https://mempool.space/api/"
    }
}
