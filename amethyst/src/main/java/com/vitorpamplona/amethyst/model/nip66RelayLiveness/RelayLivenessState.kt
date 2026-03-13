/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.model.nip66RelayLiveness

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isOnion
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import org.json.JSONArray

class RelayLivenessState(
    val scope: CoroutineScope,
) {
    private val _aliveRelaysFlow = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
    val aliveRelaysFlow: StateFlow<Set<NormalizedRelayUrl>> = _aliveRelaysFlow.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val alive = fetchAliveRelays()
                    if (alive.isNotEmpty()) {
                        _aliveRelaysFlow.value = alive
                        Log.d("RelayLivenessState", "Loaded ${alive.size} alive relays")
                    }
                } catch (e: Exception) {
                    Log.e("RelayLivenessState", "Failed to fetch alive relays", e)
                }
                delay(Nip66Constants.REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchAliveRelays(): Set<NormalizedRelayUrl> {
        try {
            return fetchFromHttpApi()
        } catch (e: Exception) {
            Log.e("RelayLivenessState", "HTTP API fetch failed", e)
        }
        return emptySet()
    }

    private suspend fun fetchFromHttpApi(): Set<NormalizedRelayUrl> {
        val client = OkHttpClient.Builder().build()
        val request =
            Request
                .Builder()
                .url(Nip66Constants.HTTP_API_URL)
                .get()
                .build()

        client.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body.string()
            val jsonArray = JSONArray(body)
            val result = mutableSetOf<NormalizedRelayUrl>()
            for (i in 0 until jsonArray.length()) {
                val url = jsonArray.getString(i)
                RelayUrlNormalizer.normalizeOrNull(url)?.let { result.add(it) }
            }
            return result
        }
    }

    fun filterAlive(candidates: Set<NormalizedRelayUrl>): Set<NormalizedRelayUrl> {
        val alive = aliveRelaysFlow.value
        if (alive.isEmpty()) return candidates
        return candidates.filter { it in alive || it.isOnion() }.toSet()
    }
}
