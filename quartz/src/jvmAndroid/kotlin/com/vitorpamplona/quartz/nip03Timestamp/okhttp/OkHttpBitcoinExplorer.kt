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
package com.vitorpamplona.quartz.nip03Timestamp.okhttp

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip03Timestamp.ots.BitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.BlockHeader
import com.vitorpamplona.quartz.nip03Timestamp.ots.OtsBlockHeightCache
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.UrlException
import com.vitorpamplona.quartz.utils.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

class OkHttpBitcoinExplorer(
    val baseUrl: () -> String,
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
    override suspend fun block(hash: String): BlockHeader {
        cache.getHeader(hash)?.let {
            return it
        }

        val baseAPI = baseUrl()
        val url = "$baseAPI/block/$hash"

        val request =
            Request
                .Builder()
                .header("Accept", "application/json")
                .url(url)
                .get()
                .build()

        return client.newCall(request).execute().use {
            if (it.isSuccessful) {
                Log.d("OkHttpBlockstreamExplorer", "$baseAPI/block/$hash")

                val jsonObject = JacksonMapper.mapper.readTree(it.body.string())

                val merkleRoot = jsonObject["merkle_root"].asText()
                val time = jsonObject["timestamp"].asInt().toString()
                val blockHeader = BlockHeader(merkleRoot, hash, time)
                cache.putHeader(hash, blockHeader)
                blockHeader
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
    override suspend fun blockHash(height: Int): String {
        cache.getHeight(height)?.let {
            return it
        }

        val baseAPI = baseUrl()
        val url = "$baseAPI/block-height/$height"

        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()

        return client.newCall(request).executeAsync().use {
            if (it.isSuccessful) {
                val blockHash = it.body.string()

                Log.d("OkHttpBlockstreamExplorer", "$url $blockHash")

                cache.putHeight(height, blockHash)
                blockHash
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
