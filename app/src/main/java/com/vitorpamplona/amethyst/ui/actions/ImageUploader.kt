package com.vitorpamplona.amethyst.ui.actions

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.ServersAvailable
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.IOException
import okio.source
import java.io.InputStream
import java.time.Duration
import java.util.Base64

val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

fun randomChars() = List(16) { charPool.random() }.joinToString("")
class ImageUploader(val account: Account?) {
    fun uploadImage(
        uri: Uri,
        contentType: String?,
        size: Long?,
        server: ServersAvailable,
        contentResolver: ContentResolver,
        onSuccess: (String, String?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val myContentType = contentType ?: contentResolver.getType(uri)
        val imageInputStream = contentResolver.openInputStream(uri)

        val length = size ?: contentResolver.query(uri, null, null, null, null)?.use {
            it.moveToFirst()
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            it.getLong(sizeIndex)
        } ?: kotlin.runCatching {
            uri.toFile().length()
        }.getOrNull() ?: 0

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
            ServersAvailable.NOSTRCHECK_ME, ServersAvailable.NOSTRCHECK_ME_NIP_94 -> {
                NostrCheckMeServer()
            }
            else -> {
                NostrBuildServer()
            }
        }

        uploadImage(imageInputStream, length, myContentType, myServer, onSuccess, onError)
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

        val client = HttpClient.getHttpClient(Duration.ofSeconds(30L))
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

        server.authorizationToken(account, requestBody.toString()) { authorizationToken ->
            if (authorizationToken != null) {
                requestBuilder.addHeader("Authorization", authorizationToken)
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
                            val url = server.parseUrlFromSuccess(body.string(), authorizationToken)
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

    fun NIP98Header(url: String, method: String, body: String, onReady: (String?) -> Unit) {
        val myAccount = account

        if (myAccount == null) {
            onReady(null)
            return
        }

        myAccount.createHTTPAuthorization(url, method, body) {
            val noteJson = it.toJson()
            val encodedNIP98Event: String = Base64.getEncoder().encodeToString(noteJson.toByteArray())
            onReady("Nostr $encodedNIP98Event")
        }
    }
}

abstract class FileServer {
    abstract fun postUrl(contentType: String?): String
    abstract fun parseUrlFromSuccess(body: String, authorizationToken: String?): String?
    abstract fun inputParameterName(contentType: String?): String

    open fun authorizationToken(account: Account?, info: String, onReady: (String?) -> Unit) {
        onReady(null)
    }
}

class NostrImgServer : FileServer() {
    override fun postUrl(contentType: String?) = "https://nostrimg.com/api/upload"

    override fun parseUrlFromSuccess(body: String, authorizationToken: String?): String? {
        val tree = jacksonObjectMapper().readTree(body)
        val url = tree?.get("data")?.get("link")?.asText()
        return url
    }

    override fun inputParameterName(contentType: String?): String {
        return contentType?.toMediaType()?.toString()?.split("/")?.get(0) ?: "image"
    }
}

class ImgurServer : FileServer() {
    override fun postUrl(contentType: String?): String {
        val category = contentType?.toMediaType()?.toString()?.split("/")?.get(0) ?: "image"
        return if (category == "image") "https://api.imgur.com/3/image" else "https://api.imgur.com/3/upload"
    }

    override fun parseUrlFromSuccess(body: String, authorizationToken: String?): String? {
        val tree = jacksonObjectMapper().readTree(body)
        val url = tree?.get("data")?.get("link")?.asText()
        return url
    }

    override fun inputParameterName(contentType: String?): String {
        return contentType?.toMediaType()?.toString()?.split("/")?.get(0) ?: "image"
    }

    override fun authorizationToken(account: Account?, info: String, onReady: (String?) -> Unit) {
        onReady("Client-ID e6aea87296f3f96")
    }
}

class NostrBuildServer : FileServer() {
    override fun postUrl(contentType: String?) = "https://nostr.build/api/v2/upload/files"
    override fun parseUrlFromSuccess(body: String, authorizationToken: String?): String? {
        val tree = jacksonObjectMapper().readTree(body)
        val data = tree?.get("data")
        val data0 = data?.get(0)
        val url = data0?.get("url")
        return url.toString().replace("\"", "")
    }

    override fun inputParameterName(contentType: String?): String {
        return "file"
    }

    override fun authorizationToken(account: Account?, info: String, onReady: (String?) -> Unit) {
        ImageUploader(account).NIP98Header("https://nostr.build/api/v2/upload/files", "POST", info, onReady)
    }
}

class NostrFilesDevServer : FileServer() {
    override fun postUrl(contentType: String?) = "https://nostrfiles.dev/upload_image"
    override fun parseUrlFromSuccess(body: String, authorizationToken: String?): String? {
        val tree = jacksonObjectMapper().readTree(body)
        return tree?.get("url")?.asText()
    }

    override fun inputParameterName(contentType: String?): String {
        return "file"
    }
}

class NostrCheckMeServer : FileServer() {
    override fun postUrl(contentType: String?) = "https://nostrcheck.me/api/v1/media"
    override fun parseUrlFromSuccess(body: String, authorizationToken: String?): String? {
        checkNotInMainThread()

        val tree = jacksonObjectMapper().readTree(body)
        val url = tree?.get("url")?.asText()
        val id = tree?.get("id")?.asText()
        var isCompleted = false

        val client = HttpClient.getHttpClient()
        val requrl =
            "https://nostrcheck.me/api/v1/media?id=$id" // + "&apikey=26d075787d261660682fb9d20dbffa538c708b1eda921d0efa2be95fbef4910a"

        val requestBuilder = Request.Builder().url(requrl)

        if (authorizationToken != null) {
            requestBuilder.addHeader("Authorization", authorizationToken)
        }

        val request = requestBuilder.get().build()

        while (!isCompleted) {
            client.newCall(request).execute().use {
                val tree = jacksonObjectMapper().readTree(it.body.string())
                isCompleted = tree?.get("status")?.asText() == "completed"
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
        return url
    }

    override fun inputParameterName(contentType: String?): String {
        return "mediafile"
    }

    override fun authorizationToken(account: Account?, body: String, onReady: (String?) -> Unit) {
        ImageUploader(account).NIP98Header("https://nostrcheck.me/api/v1/media", "POST", body, onReady)
    }
}
