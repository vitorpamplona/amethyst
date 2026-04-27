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
package com.vitorpamplona.amethyst.service.ai

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MLKitSmartReplyService {
    private val client = SmartReply.getClient()

    suspend fun suggestReplies(history: List<SmartReplyMessage>): List<String> {
        if (history.isEmpty()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val conversation =
                    history.map { msg ->
                        if (msg.isLocalUser) {
                            TextMessage.createForLocalUser(msg.text, msg.timestampMs)
                        } else {
                            TextMessage.createForRemoteUser(msg.text, msg.timestampMs, msg.remoteUserId)
                        }
                    }

                val result = Tasks.await(client.suggestReplies(conversation))
                when (result.status) {
                    SmartReplySuggestionResult.STATUS_SUCCESS -> {
                        result.suggestions
                            .map { it.text }
                            .distinct()
                            .take(3)
                    }

                    SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE -> {
                        emptyList()
                    }

                    SmartReplySuggestionResult.STATUS_NO_REPLY -> {
                        emptyList()
                    }

                    else -> {
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun close() {
        client.close()
    }
}
