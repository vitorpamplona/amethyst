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
package com.vitorpamplona.amethyst.service.uploads.hls

import com.vitorpamplona.amethyst.service.uploads.hls.HlsUploadPipeline.Companion.CONTENT_TYPE_HLS
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoMeta
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip71Video.duration
import com.vitorpamplona.quartz.nip71Video.title
import com.vitorpamplona.quartz.nip71Video.videoIMetas
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class HlsVideoPublishInput(
    val bundle: HlsBundle,
    val uploadResult: HlsUploadResult,
    val title: String,
    val description: String,
    val alt: String? = null,
    val durationSeconds: Int? = null,
    val contentWarning: String? = null,
    val dTag: String? = null,
    val createdAt: Long? = null,
)

sealed class HlsVideoEventTemplate {
    data class Horizontal(
        val template: EventTemplate<VideoHorizontalEvent>,
    ) : HlsVideoEventTemplate()

    data class Vertical(
        val template: EventTemplate<VideoVerticalEvent>,
    ) : HlsVideoEventTemplate()
}

/**
 * Assembles a NIP-71 VideoHorizontalEvent / VideoVerticalEvent template from an HLS upload
 * result. Orientation is decided from the first `#EXT-X-STREAM-INF RESOLUTION` in the bundle's
 * master playlist: portrait (height > width) selects kind 34236, otherwise 34235.
 *
 * The template carries one `imeta` tag for the master playlist (primary) plus one per rendition
 * so HLS-unaware clients can still pick a specific variant. Every imeta is marked
 * `m application/vnd.apple.mpegurl`.
 *
 * Returns the unsigned template wrapped in a sealed [HlsVideoEventTemplate]; the caller signs
 * via the account's signer and publishes via the relay client.
 */
@OptIn(ExperimentalUuidApi::class)
object HlsVideoEventBuilder {
    private val streamInfRegex = Regex("""#EXT-X-STREAM-INF:[^\n]*RESOLUTION=(\d+)x(\d+)""")

    fun build(input: HlsVideoPublishInput): HlsVideoEventTemplate {
        val renditionDimensions = parseRenditionDimensions(input.bundle.masterPlaylist)
        val isVertical = renditionDimensions.firstOrNull()?.let { it.height > it.width } ?: false

        val masterDimension = renditionDimensions.maxByOrNull { it.width * it.height }?.toDimensionTag()
        val masterVideoMeta =
            VideoMeta(
                url = input.uploadResult.masterUrl,
                mimeType = CONTENT_TYPE_HLS,
                hash = input.uploadResult.masterSha256,
                dimension = masterDimension,
                alt = input.alt,
            )

        val renditionMetas =
            input.uploadResult.renditions.mapIndexed { index, uploaded ->
                VideoMeta(
                    url = uploaded.playlistUrl,
                    mimeType = CONTENT_TYPE_HLS,
                    hash = uploaded.combinedSha256,
                    size = uploaded.combinedSize?.toInt(),
                    dimension = renditionDimensions.getOrNull(index)?.toDimensionTag(),
                )
            }

        val videoMetas = listOf(masterVideoMeta) + renditionMetas
        val dTag = input.dTag ?: Uuid.random().toString()
        val createdAt = input.createdAt ?: TimeUtils.now()

        return if (isVertical) {
            HlsVideoEventTemplate.Vertical(
                VideoVerticalEvent.build(input.description, dTag, createdAt) {
                    videoIMetas(videoMetas)
                    title(input.title)
                    input.durationSeconds?.let { duration(it) }
                    input.contentWarning?.let { contentWarning(it) }
                },
            )
        } else {
            HlsVideoEventTemplate.Horizontal(
                VideoHorizontalEvent.build(input.description, dTag, createdAt) {
                    videoIMetas(videoMetas)
                    title(input.title)
                    input.durationSeconds?.let { duration(it) }
                    input.contentWarning?.let { contentWarning(it) }
                },
            )
        }
    }

    private data class RenditionDimension(
        val width: Int,
        val height: Int,
    ) {
        fun toDimensionTag(): DimensionTag = DimensionTag(width, height)
    }

    private fun parseRenditionDimensions(masterPlaylist: String): List<RenditionDimension> =
        streamInfRegex
            .findAll(masterPlaylist)
            .map { RenditionDimension(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
            .toList()
}
