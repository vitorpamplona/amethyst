package com.vitorpamplona.amethyst.ui.actions

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.service.HttpClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream

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

        val length = contentResolver.query(uri, null, null, null, null)?.use {
            it.moveToFirst()
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            it.getLong(sizeIndex)
        } ?: 0

        checkNotNull(imageInputStream) {
            "Can't open the image input stream"
        }
        val myServer = when (server) {
            // ServersAvailable.IMGUR, ServersAvailable.IMGUR_NIP_94 -> {
            //    ImgurServer()
            // }
            ServersAvailable.NOSTRIMG, ServersAvailable.NOSTRIMG_NIP_94 -> {
                NostrImgServer()
            }
            ServersAvailable.NOSTR_BUILD, ServersAvailable.NOSTR_BUILD_NIP_94 -> {
                NostrBuildServer()
            }
            ServersAvailable.NOSTRFILES_DEV, ServersAvailable.NOSTRFILES_DEV_NIP_94 -> {
                NostrFilesDevServer()
            }
            else -> {
                NostrBuildServer()
            }
        }

        uploadImage(imageInputStream, length, contentType, myServer, onSuccess, onError)
    }

    fun uploadImage(
        inputStream: InputStream,
        length: Long,
        contentType: String?,
        server: FileServer,
        onSuccess: (String, String?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val fileName = randomChars()
        val extension = contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""

        val client = HttpClient.getHttpClient()
        val requestBody: RequestBody
        val requestBuilder = Request.Builder()

        requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                server.inputParameterName(contentType),
                "$fileName.$extension",

                object : RequestBody() {
                    override fun contentType() = contentType?.toMediaType()

                    override fun contentLength() = length

                    override fun writeTo(sink: BufferedSink) {
                        inputStream.source().use(sink::writeAll)
                    }
                }
            )
            .build()

        server.clientID()?.let {
            requestBuilder.addHeader("Authorization", it)
        }

        requestBuilder
            .addHeader("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
            .url(server.postUrl(contentType))
            .post(requestBody)
        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    check(response.isSuccessful)
                    response.body.use { body ->
                        val url = server.parseUrlFromSuccess(body.string())
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
    abstract fun parseUrlFromSuccess(body: String): String?
    abstract fun inputParameterName(contentType: String?): String

    open fun clientID(): String? = null
}

class NostrImgServer : FileServer() {
    override fun postUrl(contentType: String?) = "https://nostrimg.com/api/upload"

    override fun parseUrlFromSuccess(body: String): String? {
        val tree = jacksonObjectMapper().readTree(body)
        val url = tree?.get("data")?.get("link")?.asText()
        return url
    }

    override fun inputParameterName(contentType: String?): String {
        return contentType?.toMediaType()?.toString()?.split("/")?.get(0) ?: "image"
    }

    override fun clientID() = null
}

class ImgurServer : FileServer() {
    override fun postUrl(contentType: String?): String {
        val category = contentType?.toMediaType()?.toString()?.split("/")?.get(0) ?: "image"
        return if (category == "image") "https://api.imgur.com/3/image" else "https://api.imgur.com/3/upload"
    }

    override fun parseUrlFromSuccess(body: String): String? {
        val tree = jacksonObjectMapper().readTree(body)
        val url = tree?.get("data")?.get("link")?.asText()
        return url
    }

    override fun inputParameterName(contentType: String?): String {
        return contentType?.toMediaType()?.toString()?.split("/")?.get(0) ?: "image"
    }

    override fun clientID() = "Client-ID e6aea87296f3f96"
}

class NostrBuildServer : FileServer() {
    override fun postUrl(contentType: String?) = "https://nostr.build/api/upload/android.php"
    override fun parseUrlFromSuccess(body: String): String? {
        val url = jacksonObjectMapper().readTree(body) // return url.toString()
        return url.toString().replace("\"", "")
    }

    override fun inputParameterName(contentType: String?): String {
        return "fileToUpload"
    }

    override fun clientID() = null
}

class NostrFilesDevServer : FileServer() {
    override fun postUrl(contentType: String?) = "https://nostrfiles.dev/upload_image"
    override fun parseUrlFromSuccess(body: String): String? {
        val tree = jacksonObjectMapper().readTree(body)
        return tree?.get("url")?.asText()
    }

    override fun inputParameterName(contentType: String?): String {
        return "file"
    }

    override fun clientID() = null
}
