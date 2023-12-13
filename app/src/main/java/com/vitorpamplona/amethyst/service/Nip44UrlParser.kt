package com.vitorpamplona.amethyst.service

import java.net.URI
import java.net.URLDecoder

class Nip44UrlParser {
    fun parse(url: String): Map<String, String> {
        return try {
            fragments(URI(url))
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun fragments(uri: URI): Map<String, String> {
        if (uri.rawFragment == null) return emptyMap()
        return uri.rawFragment.split('&').associate { keyValuePair ->
            val parts = keyValuePair.split('=')
            val name = parts.firstOrNull() ?: ""
            val value = parts.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            Pair(name, value)
        }
    }
}
