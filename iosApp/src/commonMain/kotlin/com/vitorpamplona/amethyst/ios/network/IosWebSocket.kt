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
package com.vitorpamplona.amethyst.ios.network

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionWebSocketCloseCode
import platform.Foundation.NSURLSessionWebSocketDelegateProtocol
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketMessageTypeString
import platform.Foundation.NSURLSessionWebSocketTask
import platform.darwin.NSObject

/**
 * iOS WebSocket implementation using NSURLSessionWebSocketTask.
 * Mirrors BasicOkHttpWebSocket for the JVM/Android targets.
 */
class IosWebSocket(
    val url: NormalizedRelayUrl,
    val out: WebSocketListener,
) : WebSocket {
    private var task: NSURLSessionWebSocketTask? = null
    private var session: NSURLSession? = null

    override fun needsReconnect() = task == null

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun connect() {
        val nsUrl = NSURL(string = url.url)

        val config = NSURLSessionConfiguration.defaultSessionConfiguration
        val delegate = WebSocketDelegate(this)
        session = NSURLSession.sessionWithConfiguration(config, delegate, NSOperationQueue.mainQueue)
        val webSocketTask = session!!.webSocketTaskWithURL(nsUrl)
        task = webSocketTask
        webSocketTask.resume()
        receiveMessage()
    }

    private fun receiveMessage() {
        val currentTask = task ?: return
        currentTask.receiveMessageWithCompletionHandler { message, error ->
            if (error != null) {
                // Connection error — will be handled by delegate
                return@receiveMessageWithCompletionHandler
            }
            if (message != null) {
                when (message.type) {
                    NSURLSessionWebSocketMessageTypeString -> {
                        message.string?.let { out.onMessage(it) }
                    }

                    else -> {
                        // Binary messages — ignore for Nostr
                    }
                }
                // Continue receiving
                receiveMessage()
            }
        }
    }

    override fun disconnect() {
        task?.cancelWithCloseCode(1000, null)
        task = null
        session?.invalidateAndCancel()
        session = null
    }

    override fun send(msg: String): Boolean {
        val currentTask = task ?: return false
        val message = NSURLSessionWebSocketMessage(msg)
        currentTask.sendMessage(message) { error ->
            if (error != null) {
                // Send error — ignored for now, relay will reconnect
            }
        }
        return true
    }

    internal fun onOpen() {
        out.onOpen(0, false)
    }

    internal fun onClosed(
        code: Int,
        reason: String,
    ) {
        task = null
        out.onClosed(code, reason)
    }

    internal fun onError(error: NSError?) {
        task = null
        out.onFailure(
            Exception(error?.localizedDescription ?: "Unknown error"),
            error?.code?.toInt(),
            error?.localizedDescription,
        )
    }

    class Builder : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ) = IosWebSocket(url, out)
    }
}

@Suppress("CONFLICTING_OVERLOADS")
private class WebSocketDelegate(
    private val ws: IosWebSocket,
) : NSObject(),
    NSURLSessionWebSocketDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        webSocketTask: NSURLSessionWebSocketTask,
        didOpenWithProtocol: String?,
    ) {
        ws.onOpen()
    }

    override fun URLSession(
        session: NSURLSession,
        webSocketTask: NSURLSessionWebSocketTask,
        didCloseWithCode: NSURLSessionWebSocketCloseCode,
        reason: NSData?,
    ) {
        ws.onClosed(didCloseWithCode.toInt(), "")
    }
}
