package com.vitorpamplona.amethyst.ui.actions

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
import java.util.UUID
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import com.vitorpamplona.amethyst.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source

object ImageUploader {
  fun uploadImage(
    uri: Uri,
    contentResolver: ContentResolver,
    onSuccess: (String) -> Unit,
    onError: (Throwable) -> Unit,
    context : Context
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
              context.getString(R.string.can_t_open_the_image_input_stream)
            }

            imageInputStream.source().use(sink::writeAll)
          }
        }
      )
      .build()

    val request: Request = Request.Builder()
      .url("https://api.imgur.com/3/image")
      .header("Authorization", "Client-ID e6aea87296f3f96")
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
              context.getString(R.string.there_must_be_an_uploaded_image_url_in_the_response)
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