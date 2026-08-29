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
package androidx.media3.common

/** JVM stand-ins for the media3 value types the playback UI passes around. */
class MediaItem private constructor(
    val mediaId: String,
    val uri: String?,
    val mimeType: String?,
    val mediaMetadata: MediaMetadata,
) {
    class Builder {
        private var mediaId: String = ""
        private var uri: String? = null
        private var mimeType: String? = null
        private var mediaMetadata: MediaMetadata = MediaMetadata.Builder().build()

        fun setMediaId(mediaId: String) = apply { this.mediaId = mediaId }

        fun setUri(uri: String?) = apply { this.uri = uri }

        fun setMimeType(mimeType: String?) = apply { this.mimeType = mimeType }

        fun setMediaMetadata(mediaMetadata: MediaMetadata) = apply { this.mediaMetadata = mediaMetadata }

        fun build() = MediaItem(mediaId, uri, mimeType, mediaMetadata)
    }

    companion object {
        fun fromUri(uri: String) = Builder().setMediaId(uri).setUri(uri).build()
    }
}

class MediaMetadata private constructor(
    val title: CharSequence?,
    val artist: CharSequence?,
    val artworkUri: String?,
    val mediaType: Int?,
) {
    class Builder {
        private var title: CharSequence? = null
        private var artist: CharSequence? = null
        private var artworkUri: String? = null
        private var mediaType: Int? = null

        fun setTitle(title: CharSequence?) = apply { this.title = title }

        fun setArtist(artist: CharSequence?) = apply { this.artist = artist }

        fun setArtworkUri(uri: String?) = apply { this.artworkUri = uri }

        fun setMediaType(mediaType: Int?) = apply { this.mediaType = mediaType }

        fun build() = MediaMetadata(title, artist, artworkUri, mediaType)
    }

    companion object {
        const val MEDIA_TYPE_MOVIE = 20
        const val MEDIA_TYPE_MUSIC = 1
        const val KEY_TITLE = "android.media.metadata.TITLE"
    }
}

/** A frame's pixel dimensions, plus the pixel aspect ratio media3 reports. */
class VideoSize(
    val width: Int = 0,
    val height: Int = 0,
    val pixelWidthHeightRatio: Float = 1f,
) {
    companion object {
        val UNKNOWN = VideoSize()
    }
}

/**
 * The engine's view of what tracks a source carries. Desktop engines expose far
 * less detail than ExoPlayer, so the default is an empty set and the UI's
 * "unknown" branches take over — which they already have to for a source whose
 * tracks have not loaded yet.
 */
class Tracks(
    val groups: List<Group> = emptyList(),
) {
    class Group(
        val length: Int = 0,
        val type: Int = C.TRACK_TYPE_UNKNOWN,
        val isSelected: Boolean = false,
    ) {
        fun isTrackSelected(index: Int) = isSelected

        fun getTrackFormat(index: Int): Format = Format()
    }

    fun isTypeSelected(trackType: Int) = groups.any { it.type == trackType && it.isSelected }

    fun containsType(trackType: Int) = groups.any { it.type == trackType }

    companion object {
        val EMPTY = Tracks()
    }
}

class Format(
    val width: Int = -1,
    val height: Int = -1,
    val bitrate: Int = -1,
    val frameRate: Float = -1f,
    val sampleMimeType: String? = null,
    val label: String? = null,
)

class Timeline {
    val windowCount: Int = 0

    val isEmpty: Boolean get() = windowCount == 0

    companion object {
        val EMPTY = Timeline()
    }
}

class TrackSelectionParameters private constructor(
    val maxVideoWidth: Int,
    val maxVideoHeight: Int,
    val overrides: Map<Any, TrackSelectionOverride>,
) {
    fun buildUpon() = Builder(this)

    class Builder(
        source: TrackSelectionParameters? = null,
    ) {
        private var maxVideoWidth = source?.maxVideoWidth ?: Int.MAX_VALUE
        private var maxVideoHeight = source?.maxVideoHeight ?: Int.MAX_VALUE
        private val overrides = source?.overrides.orEmpty().toMutableMap()

        fun setMaxVideoSize(
            width: Int,
            height: Int,
        ) = apply {
            maxVideoWidth = width
            maxVideoHeight = height
        }

        fun setMaxVideoSizeSd() = setMaxVideoSize(1279, 719)

        fun clearVideoSizeConstraints() = setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)

        fun addOverride(override: TrackSelectionOverride) = apply { overrides[override.trackGroup] = override }

        fun clearOverrides() = apply { overrides.clear() }

        fun build() = TrackSelectionParameters(maxVideoWidth, maxVideoHeight, overrides)
    }

    companion object {
        val DEFAULT = Builder().build()
    }
}

class TrackSelectionOverride(
    val trackGroup: Any,
    val trackIndices: List<Int>,
)

/** Mirrors media3's error codes, which the UI maps to user-facing copy. */
class PlaybackException(
    message: String?,
    cause: Throwable? = null,
    val errorCode: Int = ERROR_CODE_UNSPECIFIED,
) : Exception(message, cause) {
    val errorCodeName: String get() = "ERROR_CODE_$errorCode"

    companion object {
        const val ERROR_CODE_UNSPECIFIED = 1000
        const val ERROR_CODE_IO_UNSPECIFIED = 2000
        const val ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001
        const val ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002
        const val ERROR_CODE_IO_BAD_HTTP_STATUS = 2004
        const val ERROR_CODE_IO_FILE_NOT_FOUND = 2005
        const val ERROR_CODE_PARSING_CONTAINER_MALFORMED = 3001
        const val ERROR_CODE_PARSING_MANIFEST_MALFORMED = 3002
        const val ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED = 3003
        const val ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED = 3004
        const val ERROR_CODE_DECODER_INIT_FAILED = 4001
        const val ERROR_CODE_DECODING_FAILED = 4003
        const val ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 4005
        const val ERROR_CODE_BEHIND_LIVE_WINDOW = 1002
    }
}
