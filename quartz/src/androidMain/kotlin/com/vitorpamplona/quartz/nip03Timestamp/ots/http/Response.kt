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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Holds the response from an HTTP request.
 */
class Response : Closeable {
    private var stream: InputStream? = null

    var fromUrl: String? = null
    var status: Int? = null

    constructor()

    constructor(stream: InputStream) {
        this.stream = stream
    }

    fun setStream(stream: InputStream) {
        this.stream = stream
    }

    val isOk: Boolean
        get() = this.status != null && 200 == this.status

    fun getStream(): InputStream = this.stream!!

    @get:Throws(IOException::class)
    val string: String
        get() = kotlin.text.String(this.bytes, StandardCharsets.UTF_8)

    @get:Throws(IOException::class)
    val bytes: ByteArray
        get() {
            val buffer = ByteArrayOutputStream()
            var nRead: Int
            val data = ByteArray(16384)

            while ((this.stream!!.read(data, 0, data.size).also { nRead = it }) != -1) {
                buffer.write(data, 0, nRead)
            }

            buffer.flush()

            return buffer.toByteArray()
        }

    @get:Throws(IOException::class)
    val json: JsonNode?
        get() {
            val jsonString = this.string
            val builder =
                JsonMapper.builder().build()
            return builder.readTree(jsonString)
        }

    override fun close() {
        stream?.close()
        stream = null
    }
}
