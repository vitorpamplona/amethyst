package com.vitorpamplona.amethyst.ui.actions

import android.content.ContentResolver
import android.net.Uri
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.util.*

object ImageUploader {
    fun uploadImage(
        uri: Uri,
        contentResolver: ContentResolver,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val contentType = contentResolver.getType(uri)

        val client = OkHttpClient.Builder().build()

        val requestBody: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                "${UUID.randomUUID()}",
                object : RequestBody() {
                    override fun contentType(): MediaType? =
                        contentType?.toMediaType()

                    override fun writeTo(sink: BufferedSink) {
                        val imageInputStream = contentResolver.openInputStream(uri)
                        checkNotNull(imageInputStream) {
                            "Can't open the image input stream"
                        }

                        imageInputStream.source().use(sink::writeAll)
                    }
                }
            )
            .build()

        val request: Request = Request.Builder()
            .header("Authorization", "Client-ID e6aea87296f3f96")
            .header("User-Agent", "Amethyst")
            .url("https://api.imgur.com/3/image")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    check(response.isSuccessful)
                    response.body.use { body ->
                        val tree = jacksonObjectMapper().readTree(body.string())
                        val url = tree?.get("data")?.get("link")?.asText()
                        checkNotNull(url) {
                            "There must be an uploaded image URL in the response"
                        }

                        onSuccess(url)
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
