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
package com.vitorpamplona.amethyst.ios.playback.cache

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.amethyst.commons.video.cache.NotCacheableException
import com.vitorpamplona.amethyst.commons.video.cache.VideoContentType
import com.vitorpamplona.amethyst.commons.video.cache.VideoHttpException
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.AVFoundation.AVAssetResourceLoader
import platform.AVFoundation.AVAssetResourceLoaderDelegateProtocol
import platform.AVFoundation.AVAssetResourceLoadingRequest
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSLocalizedDescriptionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorBadServerResponse
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorFileDoesNotExist
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLRequest
import platform.Foundation.create
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject

private const val TAG = "VideoResourceLoader"

// Enough of the start of a file to tell its format.
private const val HEAD_BYTES = 16

/**
 * Answers AVFoundation's requests for the bytes of a cached asset (see [IosVideoCache]). Each
 * loading request runs as a coroutine: the content information (type, length, range support)
 * comes from the cache's index or a small probe, the bytes from [IosVideoCache.reader], handed
 * to AVFoundation piece by piece as they come off the disk or the network. A request AVFoundation
 * cancels (a seek, the player released) cancels its coroutine, which stops its download; the bytes
 * that arrived stay cached.
 *
 * A failure finishes the request with an NSURLErrorDomain error, which the player reports as a
 * network problem. A URL that is not a plain file (a playlist, no length) is redirected to its
 * plain URL.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class VideoResourceLoader(
    private val scope: CoroutineScope,
) : NSObject(),
    AVAssetResourceLoaderDelegateProtocol {
    private val lock = KmpLock()

    // The coroutine answering each pending request. Guarded by [lock].
    private val jobs = HashMap<AVAssetResourceLoadingRequest, Job>()

    @ObjCSignatureOverride
    override fun resourceLoader(
        resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource: AVAssetResourceLoadingRequest,
    ): Boolean {
        val request = shouldWaitForLoadingOfRequestedResource
        val asked = request.request.URL?.absoluteString ?: return false
        if (!asked.startsWith(IosVideoCache.SCHEME_PREFIX)) return false
        val url = asked.removePrefix(IosVideoCache.SCHEME_PREFIX)
        val job = scope.launch { serve(request, url) }
        lock.withLock { jobs[request] = job }
        job.invokeOnCompletion { lock.withLock { jobs.remove(request) } }
        return true
    }

    @ObjCSignatureOverride
    override fun resourceLoader(
        resourceLoader: AVAssetResourceLoader,
        didCancelLoadingRequest: AVAssetResourceLoadingRequest,
    ) {
        lock.withLock { jobs.remove(didCancelLoadingRequest) }?.cancel()
    }

    private suspend fun serve(
        request: AVAssetResourceLoadingRequest,
        url: String,
    ) {
        try {
            request.contentInformationRequest?.let { info ->
                val known = IosVideoCache.reader.info(url)
                val head = IosVideoCache.cache.read(url, 0, HEAD_BYTES)
                if (VideoContentType.isPlaylist(head)) throw NotCacheableException("$url is an HLS playlist")
                val mimeType = VideoContentType.of(known.mimeType, IosVideoCache.declaredType(url), url, head)
                info.contentType = utiOf(mimeType)
                info.contentLength = known.length
                info.byteRangeAccessSupported = true
            }
            request.dataRequest?.let { data ->
                val start = data.requestedOffset
                val end = if (data.requestsAllDataToEndOfResource) null else start + data.requestedLength
                IosVideoCache.reader.read(url, start, end) { bytes, offset, count ->
                    if (!request.isCancelled()) data.respondWithData(bytes.toNSData(offset, count))
                }
            }
            if (!request.isCancelled()) request.finishLoading()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotCacheableException) {
            IosVideoCache.refuse(url)
            // The plain URL is AVFoundation's own fetch, which no proxy covers: a video that must
            // go through Tor stops here instead (plans/2026-11-25-ios-tor.md, B12e).
            if (!request.isCancelled()) {
                if (IosVideoCache.mustUseTor(url)) request.finishLoadingWithError(IosVideoCache.torRefusalError(url)) else redirect(request, url)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load $url", e)
            if (!request.isCancelled()) request.finishLoadingWithError(errorOf(e, url))
        }
    }

    /** Sends AVFoundation to the plain URL, which it loads itself. */
    private fun redirect(
        request: AVAssetResourceLoadingRequest,
        url: String,
    ) {
        val plain = NSURL.URLWithString(url) ?: return request.finishLoadingWithError(errorOf(NotCacheableException(url), url))
        request.redirect = NSURLRequest.requestWithURL(plain)
        request.response = NSHTTPURLResponse(uRL = plain, statusCode = 302, HTTPVersion = null, headerFields = mapOf<Any?, Any?>("Location" to url))
        request.finishLoading()
    }

    private fun errorOf(
        e: Exception,
        url: String,
    ): NSError {
        (e as? DarwinHttpRequestException)?.origin?.let { return it }
        val code =
            when {
                e is VideoHttpException && e.status == 404 -> NSURLErrorFileDoesNotExist
                e is VideoHttpException || e is NotCacheableException -> NSURLErrorBadServerResponse
                else -> NSURLErrorNetworkConnectionLost
            }
        return NSError.errorWithDomain(NSURLErrorDomain, code, mapOf<Any?, Any?>(NSLocalizedDescriptionKey to "${e.message ?: e::class.simpleName} ($url)"))
    }

    private fun utiOf(mimeType: String): String = UTType.typeWithMIMEType(mimeType)?.identifier ?: UTType.typeWithMIMEType(VideoContentType.DEFAULT)?.identifier ?: "public.mpeg-4"

    private fun ByteArray.toNSData(
        offset: Int,
        count: Int,
    ): NSData = if (count == 0) NSData() else usePinned { NSData.create(bytes = it.addressOf(offset), length = count.toULong()) }
}
