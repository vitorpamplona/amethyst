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
package com.vitorpamplona.quartz.nip01Core.relay.sockets.ios

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionWebSocketCloseCode
import platform.Foundation.NSURLSessionWebSocketDelegateProtocol
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketMessageTypeString
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Foundation.setHTTPShouldHandleCookies
import platform.darwin.NSObject

/**
 * iOS WebSocket implementation using NSURLSessionWebSocketTask.
 *
 * Uses Apple's native Network.framework-backed WebSocket support
 * available on iOS 13+. Messages are delivered in-order via a
 * recursive receive loop on the NSURLSession delegate queue.
 */
class IosWebSocket(
    private val url: NormalizedRelayUrl,
    private val listener: WebSocketListener,
) : WebSocket {
    private var task: NSURLSessionWebSocketTask? = null
    private var session: NSURLSession? = null
    private var connected = false

    override fun needsReconnect(): Boolean = task == null || !connected

    override fun connect() {
        val nsUrl = NSURL(string = url.url)

        val request =
            NSMutableURLRequest(nsUrl).apply {
                setHTTPShouldHandleCookies(false)
            }

        val delegate = WebSocketDelegate(this)
        val config = NSURLSessionConfiguration.defaultSessionConfiguration
        val urlSession = NSURLSession.sessionWithConfiguration(config, delegate, null)
        session = urlSession

        val wsTask = urlSession.webSocketTaskWithRequest(request)
        task = wsTask
        wsTask.resume()
    }

    internal fun onOpen() {
        connected = true
        listener.onOpen(0, false)
        receiveLoop()
    }

    internal fun onClose(
        code: Int,
        reason: String?,
    ) {
        connected = false
        task = null
        listener.onClosed(code, reason ?: "")
    }

    private fun receiveLoop() {
        val wsTask = task ?: return
        wsTask.receiveMessageWithCompletionHandler { message, error ->
            if (error != null) {
                connected = false
                listener.onFailure(
                    Exception(error.localizedDescription),
                    error.code.toInt(),
                    error.localizedDescription,
                )
                return@receiveMessageWithCompletionHandler
            }

            message?.let { msg ->
                if (msg.type == NSURLSessionWebSocketMessageTypeString) {
                    msg.string?.let { text ->
                        listener.onMessage(text)
                    }
                }
            }

            if (connected) {
                receiveLoop()
            }
        }
    }

    override fun send(msg: String): Boolean {
        val wsTask = task ?: return false
        if (!connected) return false

        val message = NSURLSessionWebSocketMessage(msg)
        wsTask.sendMessage(message) { error ->
            if (error != null) {
                connected = false
                listener.onFailure(
                    Exception(error.localizedDescription),
                    error.code.toInt(),
                    null,
                )
            }
        }
        return true
    }

    override fun disconnect() {
        connected = false
        task?.cancelWithCloseCode(1000, null)
        task = null
        session?.invalidateAndCancel()
        session = null
    }

    /**
     * NSURLSession delegate to receive WebSocket lifecycle callbacks.
     *
     * Using a delegate instead of relying solely on the completion-handler
     * receive loop gives us proper open/close/failure notifications from
     * the transport layer.
     */
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
            reason: platform.Foundation.NSData?,
        ) {
            ws.onClose(didCloseWithCode.toInt(), null)
        }
    }

    class Builder : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket = IosWebSocket(url, out)
    }
}
