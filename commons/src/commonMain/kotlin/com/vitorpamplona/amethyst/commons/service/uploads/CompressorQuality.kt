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
    /** Whether the video table has a row for this level; the slider offers only these when a video is selected. */
    val hasVideoRule: Boolean,
) {
    VERY_LOW(640, 50, false),
    LOW(640, 65, true),

    // Keep JPEG detail while retaining Medium's original 1280 px size limit.
    MEDIUM(1280, 85, true),
    HIGH(1920, 85, true),
    VERY_HIGH(2560, 90, false),
    UNCOMPRESSED(null, 100, true),
    ;

    /**
     * The level the video encoder uses: VERY_HIGH → HIGH, VERY_LOW → LOW, everything else itself.
     * A getter rather than a constructor argument because VERY_LOW is declared before LOW.
     */
    val videoLevel: CompressorQuality
        get() =
            when (this) {
                VERY_HIGH -> HIGH
                VERY_LOW -> LOW
                else -> this
            }

    companion object {
        /** The levels the media-quality slider offers on this platform, lowest first, ending with [UNCOMPRESSED]. */
        val sliderSteps: List<CompressorQuality> get() = platformSliderSteps

        /** Where the slider starts: each post starts here, nothing is remembered. */
        val defaultSliderPosition: Int get() = platformSliderSteps.indexOf(platformDefaultQuality)

        /** The slider's [UNCOMPRESSED] end. */
        val uncompressedSliderPosition: Int get() = platformSliderSteps.lastIndex

        /** The media-quality slider's position as a level. */
        fun fromSlider(position: Int): CompressorQuality = platformSliderSteps.getOrElse(position) { platformDefaultQuality }

        /** The stops the slider shows: the platform list, or only the levels with a video rule when [hasVideo]. */
        fun stepsFor(hasVideo: Boolean): List<CompressorQuality> = VideoAwareSliderSteps.stepsFor(platformSliderSteps, hasVideo)

        /** The platform position a video-mode slider lands on from [position]: VERY_HIGH → HIGH, VERY_LOW → LOW. */
        fun snapForVideo(position: Int): Int = VideoAwareSliderSteps.snapForVideo(platformSliderSteps, position)

        /** The index of platform [position] among [stepsFor]. */
        fun toDisplayedIndex(
            position: Int,
            hasVideo: Boolean,
        ): Int = VideoAwareSliderSteps.toDisplayedIndex(platformSliderSteps, position, hasVideo)

        /** The platform position of displayed [index] among [stepsFor]. */
        fun fromDisplayedIndex(
            index: Int,
            hasVideo: Boolean,
        ): Int = VideoAwareSliderSteps.fromDisplayedIndex(platformSliderSteps, index, hasVideo)
    }
}

/**
 * The slider's video mode as pure functions of an explicit step list, so both the 5-stop desktop
 * list and the 4-stop phone list are testable. The dialog state stays a platform position; only
 * the displayed stops change.
 */
object VideoAwareSliderSteps {
    fun stepsFor(
        steps: List<CompressorQuality>,
        hasVideo: Boolean,
    ): List<CompressorQuality> = steps.filter { !hasVideo || it.hasVideoRule }

    fun snapForVideo(
        steps: List<CompressorQuality>,
        position: Int,
    ): Int = steps.indexOf(steps[position].videoLevel)

    fun toDisplayedIndex(
        steps: List<CompressorQuality>,
        position: Int,
        hasVideo: Boolean,
    ): Int = stepsFor(steps, hasVideo).indexOf(steps[if (hasVideo) snapForVideo(steps, position) else position])

    fun fromDisplayedIndex(
        steps: List<CompressorQuality>,
        index: Int,
        hasVideo: Boolean,
    ): Int = steps.indexOf(stepsFor(steps, hasVideo)[index])
}

/** Phones get 640, 1280 and 1920 px; large screens add 2560 px. */
internal expect val platformSliderSteps: List<CompressorQuality>

internal expect val platformDefaultQuality: CompressorQuality
