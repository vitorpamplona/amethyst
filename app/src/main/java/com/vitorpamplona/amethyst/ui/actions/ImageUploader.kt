package com.vitorpamplona.amethyst.ui.actions

import android.graphics.Bitmap
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

object ImageUploader {
  private fun encodeImage(bitmap: Bitmap): ByteArray {
    val byteArrayOutPutStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutPutStream)
    return byteArrayOutPutStream.toByteArray()
  }

  fun uploadImage(bitmap: Bitmap, onSuccess: (String) -> Unit) {
    val client = OkHttpClient.Builder().build()

    val body: RequestBody = MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart(
        "image",
        "${UUID.randomUUID()}.png",
        encodeImage(bitmap).toRequestBody("image/png".toMediaType())
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
          val tree = jacksonObjectMapper().readTree(response.body!!.string())
          val url = tree.get("data").get("link").asText()
          onSuccess(url)
        }
      }

      override fun onFailure(call: Call, e: IOException) {
        e.printStackTrace()
      }
    })
  }
}