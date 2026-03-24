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
package com.vitorpamplona.quartz.nip03Timestamp.ktor

import com.vitorpamplona.quartz.nip03Timestamp.ots.BitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.BlockHeader
import com.vitorpamplona.quartz.nip03Timestamp.ots.OtsBlockHeightCache
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.UrlException
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class KtorBitcoinExplorer(
    val baseUrl: () -> String,
    val client: HttpClient,
    val cache: OtsBlockHeightCache,
) : BitcoinExplorer {
    override suspend fun block(hash: String): BlockHeader {
        cache.getHeader(hash)?.let {
            return it
        }

        val baseAPI = baseUrl()
        val url = "$baseAPI/block/$hash"

        val response =
            client.get(url) {
                header("Accept", "application/json")
            }

        if (response.status.isSuccess()) {
            Log.d("KtorBitcoinExplorer", "$baseAPI/block/$hash")

            val jsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            val merkleRoot = jsonObject["merkle_root"]!!.jsonPrimitive.content
            val time = jsonObject["timestamp"]!!.jsonPrimitive.content
            val blockHeader = BlockHeader(merkleRoot, hash, time)
            cache.putHeader(hash, blockHeader)
            return blockHeader
        } else {
            throw UrlException("Couldn't open $url: ${response.status.description} ${response.status.value}")
        }
    }

    @Throws(Exception::class)
    override suspend fun blockHash(height: Int): String {
        cache.getHeight(height)?.let {
            return it
        }

        val baseAPI = baseUrl()
        val url = "$baseAPI/block-height/$height"

        val response = client.get(url)

        if (response.status.isSuccess()) {
            val blockHash = response.bodyAsText()

            Log.d("KtorBitcoinExplorer", "$url $blockHash")

            cache.putHeight(height, blockHash)
            return blockHash
        } else {
            throw UrlException("Couldn't open $url: ${response.status.description} ${response.status.value}")
        }
    }

    companion object {
        // doesn't accept Tor
        const val BLOCKSTREAM_API_URL = "https://blockstream.info/api"

        // accepts Tor
        const val MEMPOOL_API_URL = "https://mempool.space/api/"
    }
}
