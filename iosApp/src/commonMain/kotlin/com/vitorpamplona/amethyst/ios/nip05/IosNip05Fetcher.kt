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
package com.vitorpamplona.amethyst.ios.nip05

import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Fetcher
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
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS-specific NIP-05 fetcher using NSURLSession.
 *
 * Uses the completion-handler variant of dataTask which works reliably
 * from Kotlin/Native (unlike the delegate-based pattern that can have
 * K/N interop issues).
 */
class IosNip05Fetcher : Nip05Fetcher {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun fetch(url: String): String =
        suspendCancellableCoroutine { cont ->
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl == null) {
                cont.resumeWithException(IllegalArgumentException("Invalid NIP-05 URL: $url"))
                return@suspendCancellableCoroutine
            }

            val request =
                NSMutableURLRequest(nsUrl).apply {
                    setHTTPMethod("GET")
                    setValue("application/json", forHTTPHeaderField = "Accept")
                    setTimeoutInterval(15.0)
                }

            val task =
                NSURLSession.sharedSession.dataTaskWithRequest(
                    request,
                ) { data: NSData?, response: NSURLResponse?, error: NSError? ->
                    if (error != null) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                RuntimeException("NIP-05 fetch failed for $url: " + (error.localizedDescription ?: "unknown error")),
                            )
                        }
                        return@dataTaskWithRequest
                    }

                    val httpResponse = response as? NSHTTPURLResponse
                    if (httpResponse != null && httpResponse.statusCode !in 200L..299L) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                RuntimeException("NIP-05 HTTP ${httpResponse.statusCode} for $url"),
                            )
                        }
                        return@dataTaskWithRequest
                    }

                    if (data == null) {
                        if (cont.isActive) {
                            cont.resumeWithException(RuntimeException("NIP-05 empty response for $url"))
                        }
                        return@dataTaskWithRequest
                    }

                    val body = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                    if (body == null) {
                        if (cont.isActive) {
                            cont.resumeWithException(RuntimeException("NIP-05 response not UTF-8 for $url"))
                        }
                        return@dataTaskWithRequest
                    }

                    if (cont.isActive) {
                        cont.resume(body)
                    }
                }

            cont.invokeOnCancellation {
                task.cancel()
            }

            task.resume()
        }
}
