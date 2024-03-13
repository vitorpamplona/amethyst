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
package com.vitorpamplona.amethyst.service.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import kotlin.math.min

@OptIn(UnstableApi::class)
class WssStreamDataSource(val httpClient: OkHttpClient) : BaseDataSource(true) {
    val dataStreamCollector: WssDataStreamCollector = WssDataStreamCollector()
    var webSocketClient: WebSocket? = null

    private var currentByteStream: ByteArray? = null
    private var currentPosition = 0
    private var remainingBytes = 0

    override fun open(dataSpec: DataSpec): Long {
        // Form the request and open the socket.
        // Provide the listener
        // which collects the data for us (Previous class).
        webSocketClient =
            httpClient.newWebSocket(
                Request.Builder().apply {
                    dataSpec.httpRequestHeaders.forEach { entry ->
                        addHeader(entry.key, entry.value)
                    }
                }.url(dataSpec.uri.toString()).build(),
                dataStreamCollector,
            )

        return -1 // Return -1 as the size is unknown (streaming)
    }

    override fun getUri(): Uri? {
        webSocketClient?.request()?.url?.let {
            return Uri.parse(it.toString())
        }

        return null
    }

    override fun read(
        target: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        // return 0 (nothing read) when no data present...
        if (currentByteStream == null && !dataStreamCollector.canStream()) {
            return 0
        }

        // parse one (data) ByteString at a time.
        // reset the current position and remaining bytes
        // for every new data
        if (currentByteStream == null) {
            currentByteStream = dataStreamCollector.getNextStream().toByteArray()
            currentPosition = 0
            remainingBytes = currentByteStream?.size ?: 0
        }

        val readSize = min(length, remainingBytes)

        currentByteStream?.copyInto(target, offset, currentPosition, currentPosition + readSize)
        currentPosition += readSize
        remainingBytes -= readSize

        // once the data is read set currentByteStream to null
        // so the next data would be collected to process in next
        // iteration.
        if (remainingBytes == 0) {
            currentByteStream = null
        }

        return readSize
    }

    override fun close() {
        // close the socket and relase the resources
        webSocketClient?.cancel()
    }

    // Factory class for DataSource
    class Factory(val okHttpClient: OkHttpClient) : DataSource.Factory {
        override fun createDataSource(): DataSource = WssStreamDataSource(okHttpClient)
    }
}
