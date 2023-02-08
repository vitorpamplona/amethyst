package com.vitorpamplona.amethyst.lnurl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.math.ln
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nostr.postr.Bech32
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class LightningAddressResolver {
  val client = OkHttpClient.Builder().build()

  fun assembleUrl(lnaddress: String): String? {
    val parts = lnaddress.split("@")

    if (parts.size != 2) {
      return null
    }

    return "https://${parts[1]}/.well-known/lnurlp/${parts[0]}"
  }

  fun fetchLightningAddressJson(lnaddress: String, onSucess: (String) -> Unit) {
    val scope = CoroutineScope(Job() + Dispatchers.IO)
    scope.launch {
      fetchLightningAddressJsonSuspend(lnaddress, onSucess)
    }
  }

  private suspend fun fetchLightningAddressJsonSuspend(lnaddress: String, onSucess: (String) -> Unit) {
    val url = assembleUrl(lnaddress) ?: return

    withContext(Dispatchers.IO) {
      val request: Request = Request.Builder().url(url).build()

      client.newCall(request).enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
          response.use {
            onSucess(response.body.string())
          }
        }

        override fun onFailure(call: Call, e: java.io.IOException) {
          e.printStackTrace()
        }
      })
    }
  }

  fun fetchLightningInvoice(lnCallback: String, milliSats: Long, message: String, onSucess: (String) -> Unit) {
    val scope = CoroutineScope(Job() + Dispatchers.IO)
    scope.launch {
      fetchLightningInvoiceSuspend(lnCallback, milliSats, message, onSucess)
    }
  }

  private suspend fun fetchLightningInvoiceSuspend(lnCallback: String, milliSats: Long, message: String, onSucess: (String) -> Unit) {
    val urlBinder = if (lnCallback.contains("?")) "&" else "?"
    val url = "$lnCallback${urlBinder}amount=$milliSats&comment=$message"

    withContext(Dispatchers.IO) {
      val request: Request = Request.Builder().url(url).build()

      client.newCall(request).enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
          response.use {
            onSucess(response.body.string())
          }
        }

        override fun onFailure(call: Call, e: java.io.IOException) {
          e.printStackTrace()
        }
      })
    }
  }

  fun lnAddressToLnUrl(lnaddress: String, onSucess: (String) -> Unit) {
    fetchLightningAddressJson(lnaddress) {
      onSucess(Bech32.encodeBytes("lnurl",it.toByteArray(), Bech32.Encoding.Bech32))
    }
  }

  fun lnAddressInvoice(lnaddress: String, milliSats: Long, message: String, onSucess: (String) -> Unit) {
    val mapper = jacksonObjectMapper()

    fetchLightningAddressJson(lnaddress) {
      mapper.readTree(it)?.get("callback")?.asText()?.let { callback ->
        fetchLightningInvoice(callback, milliSats, message,
          onSucess = {
            mapper.readTree(it)?.get("pr")?.asText()?.let { pr ->
              onSucess(pr)
            }
          }
        )
      }
    }
  }
}