package com.vitorpamplona.amethyst.service

import android.content.Context
import android.net.Uri
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.ImageRequest
import coil.request.Options
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.roundToInt

class BlurHashFetcher(
    private val options: Options,
    private val data: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        checkNotInMainThread()

        val encodedHash = data.toString().removePrefix("bluehash:")
        val hash = URLDecoder.decode(encodedHash, "utf-8")

        val aspectRatio = BlurHashDecoder.aspectRatio(hash) ?: 1.0f

        val preferredWidth = 100

        val bitmap = BlurHashDecoder.decode(
            hash,
            preferredWidth,
            (preferredWidth * (1 / aspectRatio)).roundToInt()
        )

        if (bitmap == null) {
            throw Exception("Unable to convert Bluehash $hash")
        }

        return DrawableResult(
            drawable = bitmap.toDrawable(options.context.resources),
            isSampled = false,
            dataSource = DataSource.MEMORY
        )
    }

    object Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher {
            return BlurHashFetcher(options, data)
        }
    }
}

object BlurHashRequester {
    fun imageRequest(context: Context, message: String): ImageRequest {
        val encodedMessage = URLEncoder.encode(message, "utf-8")

        return ImageRequest
            .Builder(context)
            .data("bluehash:$encodedMessage")
            .fetcherFactory(BlurHashFetcher.Factory)
            .build()
    }
}
