package com.vitorpamplona.amethyst.ui.actions

import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL

class ImageDownloader {
    suspend fun waitAndGetImage(imageUrl: String): ByteArray? {
        var imageData: ByteArray? = null
        var tentatives = 0

        // Servers are usually not ready.. so tries to download it for 15 times/seconds.
        while (imageData == null && tentatives < 15) {
            imageData = try {
                HttpURLConnection.setFollowRedirects(true)
                var url = URL(imageUrl)
                var huc = url.openConnection() as HttpURLConnection
                huc.instanceFollowRedirects = true
                var responseCode = huc.responseCode

                if (responseCode in 300..400) {
                    val newUrl: String = huc.getHeaderField("Location")

                    // open the new connnection again
                    url = URL(newUrl)
                    huc = url.openConnection() as HttpURLConnection
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
                tentatives++
                delay(1000)
                null
            }
        }

        return imageData
    }
}
