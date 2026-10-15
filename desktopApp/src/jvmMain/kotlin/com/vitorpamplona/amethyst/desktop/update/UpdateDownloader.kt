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
package com.vitorpamplona.amethyst.desktop.update

import com.vitorpamplona.amethyst.commons.appupdate.UpdateAsset
import com.vitorpamplona.amethyst.commons.appupdate.UpdateFailure
import com.vitorpamplona.amethyst.commons.appupdate.UpdateInstallException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads a release file to [destination] and accepts it only when its SHA-256 (and size, when
 * the release gives one) match the signed asset event. A file that does not match is deleted.
 */
class UpdateDownloader(
    private val clientFor: (String) -> OkHttpClient,
) {
    suspend fun download(
        asset: UpdateAsset,
        destination: File,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            destination.parentFile?.mkdirs()
            val partial = File(destination.parentFile, destination.name + ".part")
            partial.delete()
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                val request = Request.Builder().url(asset.url).build()
                clientFor(asset.url).newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${asset.url}")
                    val body = response.body
                    val total = asset.sizeInBytes ?: body.contentLength().takeIf { it > 0 }
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var lastReport = 0L
                            while (true) {
                                ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                digest.update(buffer, 0, n)
                                read += n
                                if (asset.sizeInBytes != null && read > asset.sizeInBytes!!) {
                                    throw UpdateInstallException(UpdateFailure.VERIFICATION_FAILED, "More bytes than the release names")
                                }
                                if (read - lastReport >= 256 * 1024) {
                                    lastReport = read
                                    onProgress(read, total)
                                }
                            }
                        }
                    }
                    onProgress(read, total)
                }

                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (hash != asset.sha256.lowercase()) {
                    throw UpdateInstallException(UpdateFailure.VERIFICATION_FAILED, "SHA-256 $hash, the release names ${asset.sha256}")
                }
                if (asset.sizeInBytes != null && read != asset.sizeInBytes) {
                    throw UpdateInstallException(UpdateFailure.VERIFICATION_FAILED, "$read bytes, the release names ${asset.sizeInBytes}")
                }

                destination.delete()
                if (!partial.renameTo(destination)) throw IOException("Could not move the download to $destination")
                destination
            } catch (e: CancellationException) {
                partial.delete()
                throw e
            } catch (e: UpdateInstallException) {
                partial.delete()
                throw e
            } catch (e: IOException) {
                partial.delete()
                throw UpdateInstallException(UpdateFailure.DOWNLOAD_FAILED, e.message ?: "download failed", e)
            }
        }
}
