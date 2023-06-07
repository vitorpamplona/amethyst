package com.vitorpamplona.amethyst.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

class Nip05Verifier() {
    val client = HttpClient.getHttpClient()

    fun assembleUrl(nip05address: String): String? {
        val parts = nip05address.trim().split("@")

        if (parts.size == 2) {
            return "https://${parts[1]}/.well-known/nostr.json?name=${parts[0]}"
        }

        return null
    }

    fun fetchNip05Json(nip05address: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            fetchNip05JsonSuspend(nip05address, onSuccess, onError)
        }
    }

    private suspend fun fetchNip05JsonSuspend(nip05: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        // checkNotInMainThread()

        val url = assembleUrl(nip05)

        if (url == null) {
            onError("Could not assemble url from Nip05: \"${nip05}\". Check the user's setup")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                    .url(url)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (it.isSuccessful) {
                                onSuccess(it.body.string())
                            } else {
                                onError("Could not resolve $nip05. Error: ${it.code}. Check if the server up and if the address $nip05 is correct")
                            }
                        }
                    }

                    override fun onFailure(call: Call, e: java.io.IOException) {
                        onError("Could not resolve $url. Check if the server up and if the address $nip05 is correct")
                        e.printStackTrace()
                    }
                })
            } catch (e: java.lang.Exception) {
                onError("Could not resolve '$url': ${e.message}")
            }
        }
    }

    fun verifyNip05(nip05: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        // check fails on tests
        // checkNotInMainThread()

        val mapper = jacksonObjectMapper()

        fetchNip05Json(
            nip05,
            onSuccess = {
                // NIP05 usernames are case insensitive, but JSON properties are not
                // converts the json to lowercase and then tries to access the username via a
                // lowercase version of the username.
                val nip05url = try {
                    mapper.readTree(it.lowercase())
                } catch (t: Throwable) {
                    onError("Error Parsing JSON from Lightning Address. Check the user's lightning setup")
                    null
                }

                val user = nip05.split("@")[0].lowercase()

                val hexKey = nip05url?.get("names")?.get(user)?.asText()

                if (hexKey == null) {
                    onError("Username not found in the NIP05 JSON")
                } else {
                    onSuccess(hexKey)
                }
            },
            onError = onError
        )
    }
}
