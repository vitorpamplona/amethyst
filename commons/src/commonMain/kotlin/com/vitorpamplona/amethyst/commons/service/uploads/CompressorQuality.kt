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
package com.vitorpamplona.amethyst.commons.service.uploads

/**
 * How hard an upload is compressed. For still images each level is a size limit
 * ([imageMaxDimension], the longer edge in pixels, never upscaled) and a JPEG quality
 * ([imageQuality], 0..100); videos map the level to their own resolution and bitrate table.
 */
enum class CompressorQuality(
    val imageMaxDimension: Int?,
    val imageQuality: Int,
) {
    VERY_LOW(640, 50),
    LOW(640, 65),
    MEDIUM(1280, 75),
    HIGH(1920, 85),
    VERY_HIGH(2560, 90),
    UNCOMPRESSED(null, 100),
    ;

    companion object {
        /** The levels the media-quality slider offers on this platform, lowest first, ending with [UNCOMPRESSED]. */
        val sliderSteps: List<CompressorQuality> get() = platformSliderSteps

        /** Where the slider starts: each post starts here, nothing is remembered. */
        val defaultSliderPosition: Int get() = platformSliderSteps.indexOf(platformDefaultQuality)

        /** The slider's [UNCOMPRESSED] end. */
        val uncompressedSliderPosition: Int get() = platformSliderSteps.lastIndex

        /** The media-quality slider's position as a level. */
        fun fromSlider(position: Int): CompressorQuality = platformSliderSteps.getOrElse(position) { platformDefaultQuality }
    }
}

/** Phones get 640, 1280 and 1920 px; large screens add 2560 px. */
internal expect val platformSliderSteps: List<CompressorQuality>

internal expect val platformDefaultQuality: CompressorQuality
