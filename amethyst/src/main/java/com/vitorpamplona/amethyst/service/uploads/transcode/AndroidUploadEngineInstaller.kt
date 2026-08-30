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
package com.vitorpamplona.amethyst.service.uploads.transcode

import android.content.Context
import com.vitorpamplona.amethyst.commons.uploads.GifToVideoConverter
import com.vitorpamplona.amethyst.commons.uploads.UploadEngineInstaller
import com.vitorpamplona.amethyst.commons.uploads.VideoTranscoder
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsTranscoder

/**
 * Puts LightCompressor behind the upload seams on Android.
 *
 * Found through `META-INF/services`, so the shared startup path never names any
 * of these classes — which is the point: they encode through `MediaCodec` and
 * exist only here, and a line naming them in `AppModules` would put an
 * Android-only type back into code that also compiles for the desktop.
 */
class AndroidUploadEngineInstaller : UploadEngineInstaller {
    override fun install(context: Any?) {
        val appContext = context as? Context ?: return
        VideoTranscoder.installed = LightCompressorTranscoder(appContext)
        GifToVideoConverter.installed = LightCompressorGifConverter(appContext)
        HlsTranscoder.installed = LightCompressorHlsTranscoder(appContext)
    }
}
