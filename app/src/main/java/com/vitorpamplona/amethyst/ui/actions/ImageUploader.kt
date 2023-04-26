package com.vitorpamplona.amethyst.ui.actions

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.util.*

val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

fun randomChars() = List(16) { charPool.random() }.joinToString("")

object ImageUploader {

    fun uploadImage(
        uri: Uri,
        server: ServersAvailable,
        contentResolver: ContentResolver,
        onSuccess: (String, String?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val contentType = contentResolver.getType(uri)
        val imageInputStream = contentResolver.openInputStream(uri)

        checkNotNull(imageInputStream) {
            "Can't open the image input stream"
        }

        val myServer = if (server == ServersAvailable.IMGUR) {
            ImgurServer()
        } else if (server == ServersAvailable.NOSTR_IMG) {
            NostrImgServer()
        } else {
            ImgurServer()
        }

        uploadImage(imageInputStream, contentType, myServer, onSuccess, onError)
    }

    fun uploadImage(
        inputStream: InputStream,
        contentType: String?,
        server: FileServer,
        onSuccess: (String, String?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val category = contentType?.toMediaType()?.toString()?.split("/")?.get(0) ?: "image"
        val fileName = randomChars()
        val extension = contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""

        val client = OkHttpClient.Builder().build()

        val requestBody: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                category,
                "$fileName.$extension",
                object : RequestBody() {
                    override fun contentType(): MediaType? =
                        contentType?.toMediaType()

                    override fun writeTo(sink: BufferedSink) {
                        inputStream.source().use(sink::writeAll)
                    }
                }
            )
            .build()

        val requestBuilder = Request.Builder()

        server.clientID()?.let {
            requestBuilder.header("Authorization", it)
        }

        requestBuilder
            .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
            .url(server.postUrl(contentType))
            .post(requestBody)

        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    check(response.isSuccessful)
                    response.body.use { body ->
                        val url = server.parseUrlFromSucess(body.string())
                        checkNotNull(url) {
                            "There must be an uploaded image URL in the response"
                        }

                        onSuccess(url, contentType)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    onError(e)
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                onError(e)
            }
        })
    }
}

abstract class FileServer {
    abstract fun postUrl(contentType: String?): String
    abstract fun parseUrlFromSucess(body: String): String?

    open fun clientID(): String? = null
}

class NostrImgServer : FileServer() {
    override fun postUrl(contentType: String?) = "https://nostrimg.com/api/upload"

    override fun parseUrlFromSucess(body: String): String? {
        val tree = jacksonObjectMapper().readTree(body)
        val url = tree?.get("data")?.get("link")?.asText()
        return url
    }

    override fun clientID() = null
}

class ImgurServer : FileServer() {
    override fun postUrl(contentType: String?): String {
        val category = contentType?.toMediaType()?.toString()?.split("/")?.get(0) ?: "image"
        return if (category == "image") "https://api.imgur.com/3/image" else "https://api.imgur.com/3/upload"
    }

    override fun parseUrlFromSucess(body: String): String? {
        val tree = jacksonObjectMapper().readTree(body)
        val url = tree?.get("data")?.get("link")?.asText()
        return url
    }

    override fun clientID() = "Client-ID e6aea87296f3f96"
}

class NostrBuildServer : FileServer() {
    override fun postUrl(contentType: String?) = "https://nostr.build/api/upload/amethyst.php"

    override fun parseUrlFromSucess(body: String): String? {
        val tree = jacksonObjectMapper().readTree(body)
        val url = tree?.get("data")?.get("link")?.asText()
        return url
    }

    override fun clientID() = null
}
