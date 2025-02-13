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
package com.vitorpamplona.ammolite.relays

import com.vitorpamplona.quartz.nip01Core.relay.RelayStat

object RelayStats {
    private val innerCache = mutableMapOf<String, RelayStat>()

    fun get(url: String): RelayStat = innerCache.getOrPut(url) { RelayStat() }

    fun addBytesReceived(
        url: String,
        bytesUsedInMemory: Long,
    ) {
        get(url).addBytesReceived(bytesUsedInMemory)
    }

    fun addBytesSent(
        url: String,
        bytesUsedInMemory: Long,
    ) {
        get(url).addBytesSent(bytesUsedInMemory)
    }

    fun newError(
        url: String,
        error: String?,
    ) {
        get(url).newError(error)
    }

    fun newNotice(
        url: String,
        notice: String?,
    ) {
        get(url).newNotice(notice)
    }

    fun setPing(
        url: String,
        pingInMs: Long,
    ) {
        get(url).pingInMs = pingInMs
    }

    fun newSpam(
        url: String,
        explanation: String,
    ) {
        get(url).newSpam(explanation)
    }
}
