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

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Simple async HTTP client for iOS using NSURLSession.
 *
 * Uses the completion-handler pattern which works reliably from K/N.
 * Shared across NIP-05 fetcher, LNURL zap flow, and any other
 * HTTP needs on iOS.
 */
object IosHttpClient {
    /**
     * Performs an async HTTP GET request.
     *
     * @param url The URL to fetch
     * @param headers Optional additional headers
     * @param timeoutSeconds Request timeout (default 15s)
     * @return Response body as String
     * @throws RuntimeException on network errors, non-2xx responses, or empty bodies
     */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Double = 15.0,
    ): String =
        suspendCancellableCoroutine { cont ->
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl == null) {
                cont.resumeWithException(IllegalArgumentException("Invalid URL: $url"))
                return@suspendCancellableCoroutine
            }

            val request =
                NSMutableURLRequest(nsUrl).apply {
                    setHTTPMethod("GET")
                    setValue("application/json", forHTTPHeaderField = "Accept")
                    setTimeoutInterval(timeoutSeconds)
                    headers.forEach { (key, value) ->
                        setValue(value, forHTTPHeaderField = key)
                    }
                }

            val task =
                NSURLSession.sharedSession.dataTaskWithRequest(
                    request,
                ) { data: NSData?, response: NSURLResponse?, error: NSError? ->
                    if (!cont.isActive) return@dataTaskWithRequest

                    if (error != null) {
                        cont.resumeWithException(
                            RuntimeException("HTTP GET failed for $url: " + (error.localizedDescription ?: "unknown")),
                        )
                        return@dataTaskWithRequest
                    }

                    val httpResponse = response as? NSHTTPURLResponse
                    if (httpResponse != null && httpResponse.statusCode !in 200L..299L) {
                        cont.resumeWithException(
                            RuntimeException("HTTP ${httpResponse.statusCode} for $url"),
                        )
                        return@dataTaskWithRequest
                    }

                    if (data == null) {
                        cont.resumeWithException(RuntimeException("Empty response for $url"))
                        return@dataTaskWithRequest
                    }

                    val body = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                    if (body == null) {
                        cont.resumeWithException(RuntimeException("Non-UTF-8 response for $url"))
                        return@dataTaskWithRequest
                    }

                    cont.resume(body)
                }

            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }

    /**
     * Performs an async HTTP POST request with a JSON body.
     *
     * @param url The URL to post to
     * @param body JSON string body
     * @param headers Optional additional headers
     * @param timeoutSeconds Request timeout (default 15s)
     * @return Response body as String
     */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Double = 15.0,
    ): String =
        suspendCancellableCoroutine { cont ->
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl == null) {
                cont.resumeWithException(IllegalArgumentException("Invalid URL: $url"))
                return@suspendCancellableCoroutine
            }

            val request =
                NSMutableURLRequest(nsUrl).apply {
                    setHTTPMethod("POST")
                    setValue("application/json", forHTTPHeaderField = "Content-Type")
                    setValue("application/json", forHTTPHeaderField = "Accept")
                    setTimeoutInterval(timeoutSeconds)
                    headers.forEach { (key, value) ->
                        setValue(value, forHTTPHeaderField = key)
                    }
                    setHTTPBody(
                        NSString.create(string = body).dataUsingEncoding(NSUTF8StringEncoding),
                    )
                }

            val task =
                NSURLSession.sharedSession.dataTaskWithRequest(
                    request,
                ) { data: NSData?, response: NSURLResponse?, error: NSError? ->
                    if (!cont.isActive) return@dataTaskWithRequest

                    if (error != null) {
                        cont.resumeWithException(
                            RuntimeException("HTTP POST failed for $url: " + (error.localizedDescription ?: "unknown")),
                        )
                        return@dataTaskWithRequest
                    }

                    val httpResponse = response as? NSHTTPURLResponse
                    if (httpResponse != null && httpResponse.statusCode !in 200L..299L) {
                        cont.resumeWithException(
                            RuntimeException("HTTP ${httpResponse.statusCode} for $url"),
                        )
                        return@dataTaskWithRequest
                    }

                    if (data == null) {
                        cont.resumeWithException(RuntimeException("Empty response for $url"))
                        return@dataTaskWithRequest
                    }

                    val responseBody = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                    if (responseBody == null) {
                        cont.resumeWithException(RuntimeException("Non-UTF-8 response for $url"))
                        return@dataTaskWithRequest
                    }

                    cont.resume(responseBody)
                }

            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }
}
