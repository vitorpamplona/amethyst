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
package com.vitorpamplona.amethyst.favorites

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Device-local favicon store for browsed sites, keyed by host. Favicons are **captured from the WebView
 * that already loaded the page** in the keyless `:napplet` browser host (where they ride the page's own —
 * Tor-routed — network path) and relayed here as PNG bytes over IPC; this is the privacy-preserving
 * alternative to the main app fetching `host/favicon.ico` itself, which would bypass Tor and leak the
 * visit. Used to decorate favorite cards and omnibox suggestion rows.
 *
 * Lives only in the **main process**. Bytes are persisted as one small PNG per host under
 * `filesDir/browser_icons`; the deterministic path means the only in-memory state is [keys] — the set of
 * hosts that currently have an icon — which exists purely to drive Compose recomposition (and to keep
 * `File.exists()` disk checks out of composition).
 */
object BrowserIconRegistry {
    private const val DIR = "browser_icons"

    private val _keys = MutableStateFlow<Set<String>>(emptySet())

    /** Sanitized host keys that currently have a stored icon. Observe to recompose when an icon arrives. */
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    @Volatile private var iconDir: File? = null

    /** Binds the app context and indexes already-stored icons. Idempotent. */
    fun init(context: Context) {
        if (iconDir != null) return
        val dir = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }
        iconDir = dir
        _keys.value = dir.listFiles()?.mapNotNull { it.name.removeSuffix(PNG).takeIf { n -> n.isNotBlank() } }?.toSet() ?: emptySet()
    }

    /** Persists [bytes] as the favicon for [host] and marks it available. Called from the broker on IPC. */
    fun record(
        host: String,
        bytes: ByteArray,
    ) {
        val dir = iconDir ?: return
        if (host.isBlank() || bytes.isEmpty()) return
        val key = sanitize(host)
        try {
            File(dir, key + PNG).writeBytes(bytes)
            _keys.update { it + key }
        } catch (e: Exception) {
            Log.w("BrowserIconRegistry", "Failed to store favicon for $host", e)
        }
    }

    /**
     * A Coil model (`file://…`) for [host]'s favicon, or null when none is stored. Reads [keys] so callers
     * that observe the flow recompose as icons arrive — pass [keys]'s value as a `remember` key.
     */
    fun iconModelFor(host: String): String? {
        val dir = iconDir ?: return null
        val key = sanitize(host)
        if (key !in _keys.value) return null
        return "file://" + File(dir, key + PNG).absolutePath
    }

    // Hosts map to a flat, filesystem-safe filename. Collisions (two hosts → one key) only mean a shared
    // icon file, which is harmless for a decoration.
    private fun sanitize(host: String): String =
        host
            .lowercase()
            .map { if (it.isLetterOrDigit() || it == '.' || it == '-') it else '_' }
            .joinToString("")
            .take(120)

    private const val PNG = ".png"
}
