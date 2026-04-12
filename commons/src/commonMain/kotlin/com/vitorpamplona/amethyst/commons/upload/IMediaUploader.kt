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
package com.vitorpamplona.amethyst.commons.upload

/**
 * Platform-abstracted media upload interface.
 *
 * Encapsulates the full upload pipeline: metadata stripping, compression,
 * and upload via the user's configured file server (NIP-96 or Blossom).
 *
 * Android implementation wraps MetadataStripper, MediaCompressor, and
 * Nip96Uploader/BlossomUploader. iOS implementation will provide native equivalents.
 *
 * Used by metadata ViewModels (BookmarkGroupMetadata, FollowPackMetadata,
 * PeopleListMetadata, ChannelMetadata, NewUserMetadata) to upload profile
 * pictures and banners without depending on Android Context.
 */
interface IMediaUploader {
    /**
     * Upload media (image/video) through the full pipeline:
     * 1. Strip location metadata (if user setting enabled)
     * 2. Compress media
     * 3. Upload to the user's configured file server
     *
     * @param mediaUri Platform-opaque URI string identifying the media to upload
     * @param mimeType MIME type of the media (e.g. "image/jpeg"), or null if unknown
     * @param onProgress Optional progress callback (0.0 to 1.0)
     * @return [MediaUploadResult] with the uploaded media URL and metadata
     * @throws MediaUploadException on failure
     */
    suspend fun uploadMedia(
        mediaUri: String,
        mimeType: String?,
        onProgress: ((Float) -> Unit)? = null,
    ): MediaUploadResult
}

/**
 * Result of a successful media upload.
 */
data class MediaUploadResult(
    /** Publicly accessible URL of the uploaded media. */
    val url: String,
    /** SHA-256 hash of the uploaded blob, if provided by the server. */
    val sha256: String? = null,
    /** Size of the uploaded blob in bytes. */
    val size: Long? = null,
    /** MIME type of the uploaded blob. */
    val type: String? = null,
)

/**
 * Exception thrown when media upload fails.
 */
class MediaUploadException(
    /** Short error title suitable for UI display. */
    val title: String,
    /** Detailed error message. */
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)
