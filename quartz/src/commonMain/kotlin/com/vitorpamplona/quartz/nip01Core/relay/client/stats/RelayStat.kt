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
package com.vitorpamplona.quartz.nip01Core.relay.client.stats

import androidx.collection.LruCache
import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Every field is atomic: every relay's receive coroutine and every thread that sends write them at
 * once, and plain `+=` lost updates. No lock, so counting a frame costs one atomic add.
 *
 * [pingInMs] and [compression] are only ever overwritten; they are atomics too, rather than
 * `@Volatile` constructor properties, because those crashed the Kotlin compiler's IR lowering in
 * `:commons:compileAndroidMain` (Kotlin 2.4.20, in a caller's inline lambda reading this class).
 */
@OptIn(ExperimentalAtomicApi::class)
@Stable
class RelayStat(
    receivedBytes: Int = 0,
    sentBytes: Int = 0,
    spamCounter: Int = 0,
    errorCounter: Int = 0,
    pingInMs: Int = 0,
    compression: Boolean = false,
    connectionTentatives: Int = 0,
    connectionCompleted: Int = 0,
) {
    private val received = AtomicInt(receivedBytes)
    private val sent = AtomicInt(sentBytes)
    private val spam = AtomicInt(spamCounter)
    private val errors = AtomicInt(errorCounter)
    private val tentatives = AtomicInt(connectionTentatives)
    private val completed = AtomicInt(connectionCompleted)
    private val ping = AtomicInt(pingInMs)
    private val compressed = AtomicBoolean(compression)

    var pingInMs: Int
        get() = ping.load()
        set(value) = ping.store(value)

    var compression: Boolean
        get() = compressed.load()
        set(value) = compressed.store(value)

    val receivedBytes: Int get() = received.load()
    val sentBytes: Int get() = sent.load()
    val spamCounter: Int get() = spam.load()
    val errorCounter: Int get() = errors.load()
    val connectionTentatives: Int get() = tentatives.load()
    val connectionCompleted: Int get() = completed.load()

    val messages = LruCache<IRelayDebugMessage, IRelayDebugMessage>(100)

    /** Tried this session and never got through: the relay looks dead. An untried relay is not. */
    val hasNeverConnected: Boolean
        get() = connectionTentatives > 0 && connectionCompleted == 0

    fun newNotice(notice: String?) {
        val debugMessage =
            NoticeDebugMessage(
                message = notice ?: "No error message provided",
            )

        messages.put(debugMessage, debugMessage)
    }

    fun newError(error: String?) {
        errors.incrementAndFetch()

        val debugMessage =
            ErrorDebugMessage(
                message = error ?: "No error message provided",
            )

        messages.put(debugMessage, debugMessage)
    }

    fun addBytesReceived(bytesUsedInMemory: Int) {
        received.addAndFetch(bytesUsedInMemory)
    }

    fun addBytesSent(bytesUsedInMemory: Int) {
        sent.addAndFetch(bytesUsedInMemory)
    }

    fun newConnection() {
        tentatives.incrementAndFetch()
    }

    fun connectionCompleted() {
        completed.incrementAndFetch()
    }

    fun newSpam(
        link1: String,
        link2: String,
    ) {
        spam.incrementAndFetch()

        val debugMessage = SpamDebugMessage(link1, link2)

        messages.put(debugMessage, debugMessage)
    }
}

@Stable
sealed interface IRelayDebugMessage {
    val time: Long
}

/** Debug messages that carry a single plain-text payload (notices, errors). */
sealed interface IRelayDebugMessageText : IRelayDebugMessage {
    val message: String
}

class SpamDebugMessage(
    val link1: String,
    val link2: String,
    override val time: Long = TimeUtils.now(),
) : IRelayDebugMessage

class NoticeDebugMessage(
    override val message: String,
    override val time: Long = TimeUtils.now(),
) : IRelayDebugMessageText

class ErrorDebugMessage(
    override val message: String,
    override val time: Long = TimeUtils.now(),
) : IRelayDebugMessageText
