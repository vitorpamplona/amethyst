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
package com.vitorpamplona.amethyst.ui.actions

import com.vitorpamplona.amethyst.service.HttpClientManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL

class ImageDownloader {
    suspend fun waitAndGetImage(imageUrl: String): ByteArray? {
        var imageData: ByteArray? = null
        var tentatives = 0

        // Servers are usually not ready.. so tries to download it for 15 times/seconds.
        while (imageData == null && tentatives < 15) {
            imageData =
                try {
                    HttpURLConnection.setFollowRedirects(true)
                    var url = URL(imageUrl)
                    var huc =
                        if (HttpClientManager.getDefaultProxy() != null) {
                            url.openConnection(HttpClientManager.getDefaultProxy()) as HttpURLConnection
                        } else {
                            url.openConnection() as HttpURLConnection
                        }
                    huc.instanceFollowRedirects = true
                    var responseCode = huc.responseCode

                    if (responseCode in 300..400) {
                        val newUrl: String = huc.getHeaderField("Location")

                        // open the new connnection again
                        url = URL(newUrl)
                        huc =
                            if (HttpClientManager.getDefaultProxy() != null) {
                                url.openConnection(HttpClientManager.getDefaultProxy()) as HttpURLConnection
                            } else {
                                url.openConnection() as HttpURLConnection
                            }
                        responseCode = huc.responseCode
                    }

                    if (responseCode in 200..300) {
                        huc.inputStream.use { it.readBytes() }
                    } else {
                        tentatives++
                        delay(1000)

                        null
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    tentatives++
                    delay(1000)
                    null
                }
        }

        return imageData
    }
}
