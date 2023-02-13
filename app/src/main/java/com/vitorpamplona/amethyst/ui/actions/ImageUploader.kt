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
  ) {
    val contentType = contentResolver.getType(uri)

    val client = OkHttpClient.Builder().build()

    val body: RequestBody = MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart(
        "image",
        "${UUID.randomUUID()}",
        object : RequestBody() {
          override fun contentType(): MediaType? =
            contentType?.toMediaType()

          override fun writeTo(sink: BufferedSink) {
            contentResolver.openInputStream(uri)!!.use { inputStream ->
              sink.writeAll(inputStream.source())
            }
          }
        }
      )
      .build()

    val request: Request = Request.Builder()
      .url("https://api.imgur.com/3/image")
      .header("Authorization", "Client-ID e6aea87296f3f96")
      .post(body)
      .build()

    client.newCall(request).enqueue(object : Callback {
      override fun onResponse(call: Call, response: Response) {
        response.use {
          val body = response.body
          if (body != null) {
            val tree = jacksonObjectMapper().readTree(body.string())
            val url = tree?.get("data")?.get("link")?.asText()
            if (url != null)
              onSuccess(url)
          }
        }
      }

      override fun onFailure(call: Call, e: IOException) {
        e.printStackTrace()
      }
    })
  }
}