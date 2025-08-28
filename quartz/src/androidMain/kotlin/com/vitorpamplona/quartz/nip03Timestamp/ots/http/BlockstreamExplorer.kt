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
package com.vitorpamplona.quartz.nip03Timestamp.ots.http

import android.util.Log
import com.vitorpamplona.quartz.nip03Timestamp.ots.BitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.BlockHeader
import java.net.URL
import java.util.concurrent.Executors

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
class BlockstreamExplorer : BitcoinExplorer {
    /**
     * Retrieve the block information from the block hash.
     *
     * @param hash Hash of the block.
     * @return the blockheader of the hash
     * @throws Exception desc
     */
    @Throws(Exception::class)
    override fun block(hash: String): BlockHeader {
        val url = URL("$EXPLORA_URL/block/$hash")
        val task = Request(url)
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<Response>(task)
        val take: Response = future.get()
        executor.shutdown()
        if (!take.isOk) throw Exception()

        val jsonObject = take.json
        val merkleRoot = jsonObject!!.get("merkle_root").asText()
        val time = jsonObject.get("timestamp").asInt().toString()
        val blockHeader = BlockHeader(merkleRoot, hash, time)
        Log.i("BlockstreamExplorer", take.fromUrl + " " + blockHeader)
        return blockHeader
        // log.warning("Cannot parse merkleroot from body: " + jsonObject + ": " + e.getMessage());
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
        val url = URL("$EXPLORA_URL/block-height/$height")
        val task = Request(url)
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<Response>(task)
        val take = future.get()
        executor.shutdown()
        if (!take.isOk) throw Exception()
        val blockHash = take.string
        Log.i("BlockstreamExplorer", take.fromUrl + " " + blockHash)
        return blockHash
    }

    companion object {
        private const val EXPLORA_URL = "https://blockstream.info/api"
    }
}
