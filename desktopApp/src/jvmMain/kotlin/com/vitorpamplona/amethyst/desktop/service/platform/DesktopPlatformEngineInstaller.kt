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
package com.vitorpamplona.amethyst.desktop.service.platform

import com.vitorpamplona.amethyst.commons.platform.PlatformEngineInstaller
import com.vitorpamplona.amethyst.commons.uploads.GifToVideoConverter
import com.vitorpamplona.amethyst.commons.uploads.VideoTranscoder
import com.vitorpamplona.amethyst.commons.uploads.hls.HlsTranscoder
import com.vitorpamplona.amethyst.desktop.service.media.FfmpegBinary
import com.vitorpamplona.amethyst.desktop.service.uploads.FfmpegGifConverter
import com.vitorpamplona.amethyst.desktop.service.uploads.FfmpegHlsTranscoder
import com.vitorpamplona.amethyst.desktop.service.uploads.FfmpegVideoTranscoder
import com.vitorpamplona.quartz.utils.Log
import java.io.File

/**
 * Puts ffmpeg behind the upload seams on the desktop.
 *
 * The counterpart to Android's installer, found the same way — through
 * `META-INF/services` — so neither platform's startup path names the other's
 * engine, or its own.
 */
class DesktopPlatformEngineInstaller : PlatformEngineInstaller {
    override fun install(context: Any?) {
        val transcodeDir = File(System.getProperty("user.home"), ".cache/amethyst-desktop/transcode")

        VideoTranscoder.installed = FfmpegVideoTranscoder(transcodeDir)
        GifToVideoConverter.installed = FfmpegGifConverter(transcodeDir)
        HlsTranscoder.installed = FfmpegHlsTranscoder(transcodeDir)

        if (!FfmpegBinary.isAvailable) {
            Log.w("DesktopPlatformEngines") { "No ffmpeg found; videos will be uploaded without re-encoding" }
        }
    }
}
