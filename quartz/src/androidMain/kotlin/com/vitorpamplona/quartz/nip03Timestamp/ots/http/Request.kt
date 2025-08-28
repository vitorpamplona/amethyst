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
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.zip.GZIPInputStream

/**
 * For making an HTTP request.
 */
class Request(
    private val url: URL,
) : Callable<Response?> {
    private var data: ByteArray? = null
    private var headers: MutableMap<String, String> = mutableMapOf()

    fun setData(data: ByteArray?) {
        this.data = data
    }

    fun setHeaders(headers: MutableMap<String, String>) {
        this.headers = headers
    }

    @Throws(Exception::class)
    override fun call(): Response {
        val response = Response()

        try {
            val httpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.setReadTimeout(10000)
            httpURLConnection.setConnectTimeout(10000)
            httpURLConnection.setRequestProperty("User-Agent", "OpenTimestamps Java")
            httpURLConnection.setRequestProperty("Accept", "application/json")
            httpURLConnection.setRequestProperty("Accept-Encoding", "gzip")

            for (entry in headers.entries) {
                httpURLConnection.setRequestProperty(entry.key, entry.value)
            }

            if (data != null) {
                httpURLConnection.setDoOutput(true)
                httpURLConnection.setRequestMethod("POST")
                httpURLConnection.setRequestProperty(
                    "Content-Length",
                    "" + this.data!!.size.toString(),
                )
                val wr = DataOutputStream(httpURLConnection.getOutputStream())
                wr.write(this.data, 0, this.data!!.size)
                wr.flush()
                wr.close()
            } else {
                httpURLConnection.setRequestMethod("GET")
            }

            httpURLConnection.connect()

            val responseCode = httpURLConnection.getResponseCode()

            Log.i("OpenTimestamp", "$responseCode responseCode ")

            response.status = responseCode
            response.fromUrl = url.toString()
            var `is` = httpURLConnection.getInputStream()
            if ("gzip" == httpURLConnection.getContentEncoding()) {
                `is` = GZIPInputStream(`is`)
            }
            response.setStream(`is`)
        } catch (e: Exception) {
            Log.w("OpenTimestamp", "$url exception $e")
        }

        return response
    }
}
