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
package com.vitorpamplona.nestsclient.moq.lite

import kotlinx.coroutines.flow.Flow

// Caller-facing handles returned by MoqLiteSession.subscribe / .announce,
// plus the typed protocol-level rejection exception. Kept together
// because they share a single concern: "what the consumer gets back
// from a subscribe / announce request and how it surfaces failure."

/**
 * Active subscription handle returned by [MoqLiteSession.subscribe].
 * [frames] emits every frame the publisher pushes; [unsubscribe]
 * FINs the bidi to signal "no longer interested" (moq-lite has no
 * UNSUBSCRIBE message — FIN is the protocol).
 */
class MoqLiteSubscribeHandle internal constructor(
    val id: Long,
    val ok: MoqLiteSubscribeOk,
    val frames: Flow<MoqLiteFrame>,
    private val unsubscribeAction: suspend () -> Unit,
) {
    suspend fun unsubscribe() = unsubscribeAction()
}

/**
 * Active announce-discovery handle returned by [MoqLiteSession.announce].
 * [updates] emits every [MoqLiteAnnounce] update the relay streams
 * back; [close] FINs the bidi to stop receiving updates.
 */
class MoqLiteAnnouncesHandle internal constructor(
    val updates: Flow<MoqLiteAnnounce>,
    private val close: suspend () -> Unit,
) {
    suspend fun close() = close.invoke()
}

/** Thrown when subscribe is rejected (Drop) or the response stream dies. */
class MoqLiteSubscribeException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
