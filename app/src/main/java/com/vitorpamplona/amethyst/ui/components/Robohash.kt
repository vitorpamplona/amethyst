package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.ImageRequest
import coil.request.Options
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.utils.Robohash
import okio.Buffer
import java.nio.charset.Charset

@Stable
class HashImageFetcher(
    private val context: Context,
    private val isLightTheme: Boolean,
    private val data: Uri
) : Fetcher {

    override suspend fun fetch(): SourceResult {
        checkNotInMainThread()
        val source = try {
            val buffer = Buffer()
            buffer.writeString(Robohash.assemble(data.toString(), isLightTheme), Charset.defaultCharset())
            buffer
        } finally {
        }

        return SourceResult(
            source = ImageSource(source, context),
            mimeType = "image/svg+xml",
            dataSource = DataSource.MEMORY
        )
    }

    object Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher {
            return HashImageFetcher(options.context, options.parameters.value("isLightTheme") ?: true, data)
        }
    }
}

object RobohashImageRequest {
    fun build(context: Context, message: String, isLightTheme: Boolean): ImageRequest {
        return ImageRequest
            .Builder(context)
            .data(message)
            .fetcherFactory(HashImageFetcher.Factory)
            .setParameter("isLightTheme", isLightTheme)
            .addHeader("Cache-Control", "max-age=31536000")
            .build()
    }
}
