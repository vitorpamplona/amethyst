package com.vitorpamplona.amethyst.ui.actions

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.FileOutputStream
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
        val myServer = if (server == ServersAvailable.IMGUR) {
            ImgurServer()
        } else if (server == ServersAvailable.NOSTRIMG) {
            NostrImgServer()
        } else if (server == ServersAvailable.NOSTR_BUILD) {
            NostrBuildServer()
        } else {
            ImgurServer()
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

        val client = OkHttpClient.Builder().build()
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

fun getRealPathFromURI(uri: Uri, context: Context): String? {
    val returnCursor = context.contentResolver.query(uri, null, null, null, null)
    val nameIndex = returnCursor!!.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    returnCursor.moveToFirst()
    val name = returnCursor.getString(nameIndex)
    val file = File(context.filesDir, name)
    try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val outputStream = FileOutputStream(file)
        var read = 0
        val maxBufferSize = 1 * 1024 * 1024
        val bytesAvailable: Int = inputStream?.available() ?: 0
        val bufferSize = Math.min(bytesAvailable, maxBufferSize)
        val buffers = ByteArray(bufferSize)
        while (inputStream?.read(buffers).also {
            if (it != null) {
                read = it
            }
        } != -1
        ) {
            outputStream.write(buffers, 0, read)
        }
        Log.e("File Size", "Size " + file.length())
        inputStream?.close()
        outputStream.close()
        Log.e("File Path", "Path " + file.path)
    } catch (e: java.lang.Exception) {
        Log.e("Exception", e.message!!)
    }
    return file.path
}

abstract class FileServer {
    abstract fun postUrl(contentType: String?): String
    abstract fun parseUrlFromSucess(body: String): String?
    abstract fun inputParameterName(contentType: String?): String

    open fun clientID(): String? = null
}

class NostrImgServer : FileServer() {
    override fun postUrl(contentType: String?) = "https://nostrimg.com/api/upload"

    override fun parseUrlFromSucess(body: String): String? {
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

    override fun parseUrlFromSucess(body: String): String? {
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
    override fun parseUrlFromSucess(body: String): String? {
        val url = jacksonObjectMapper().readTree(body) // return url.toString()
        return url.toString().replace("\"", "")
    }

    override fun inputParameterName(contentType: String?): String {
        return "fileToUpload"
    }

    override fun clientID() = null
}
