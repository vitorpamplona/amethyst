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
package com.vitorpamplona.amethyst.desktop.app

import androidx.compose.ui.platform.UriHandler
import com.vitorpamplona.quartz.utils.Log
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Hands a URI to the operating system: the browser for web links, the registered app for the rest.
 * AWT's BROWSE is missing on many Linux desktops (anything without GNOME's libraries), where the
 * freedesktop `xdg-open` does the same job.
 *
 * [open] is false when nothing could take the link. AWT can't tell: on macOS its browse returns
 * normally for a scheme no app claims (a `lightning:` invoice with no wallet installed), so links
 * outside the web ask the OS for a handler first.
 */
object DesktopBrowser {
    private val os = System.getProperty("os.name").orEmpty().lowercase()
    private val isMac = os.contains("mac")
    private val isWindows = os.contains("windows")
    private val isLinux = os.contains("linux")

    // Kept to RFC 3986's scheme characters: the scheme becomes a registry key or a MIME type below.
    private val schemePattern = Regex("[a-z][a-z0-9+.-]*")

    fun open(uri: String): Boolean {
        // A URI never starts with a dash; something that does would reach xdg-open as an option.
        if (uri.startsWith("-")) return false
        val scheme = scheme(uri).lowercase()
        if (scheme != "http" && scheme != "https") {
            when (hasHandler(uri, scheme)) {
                false -> {
                    Log.w("DesktopBrowser") { "No app can open a $scheme link" }
                    return false
                }

                // macOS's `open` already handed the link over while answering.
                true -> if (isMac) return true

                null -> {}
            }
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(uri))
                return true
            }
        } catch (e: Exception) {
            Log.w("DesktopBrowser") { "Could not open a ${scheme(uri)} link: ${e::class.simpleName}" }
        }
        if (!isLinux) return false
        return try {
            ProcessBuilder("xdg-open", uri)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            true
        } catch (e: Exception) {
            Log.w("DesktopBrowser") { "Could not open a ${scheme(uri)} link with xdg-open: ${e::class.simpleName}" }
            false
        }
    }

    /**
     * Whether an app is registered for [scheme]; null when the OS can't be asked. On macOS the
     * question is `open` itself, which opens the link when it can and fails with
     * kLSApplicationNotFoundErr when no app claims the scheme.
     */
    private fun hasHandler(
        uri: String,
        scheme: String,
    ): Boolean? {
        if (!schemePattern.matches(scheme)) return false
        return try {
            when {
                isMac -> run("open", uri) == 0

                isWindows -> run("reg", "query", "HKEY_CLASSES_ROOT\\$scheme", "/v", "URL Protocol") == 0

                isLinux -> {
                    val process =
                        ProcessBuilder("xdg-mime", "query", "default", "x-scheme-handler/$scheme")
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start()
                    val handler = process.inputStream.bufferedReader().readText()
                    if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroy()
                    handler.isNotBlank()
                }

                else -> null
            }
        } catch (e: Exception) {
            Log.w("DesktopBrowser") { "Could not look up a handler for $scheme: ${e::class.simpleName}" }
            null
        }
    }

    /** Runs [command] and returns its exit code, or -1 when it hangs. */
    private fun run(vararg command: String): Int {
        val process =
            ProcessBuilder(*command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroy()
            return -1
        }
        return process.exitValue()
    }

    /**
     * Only the scheme goes to the log. The rest can be a secret: a nostrconnect:// code carries
     * the pairing secret, a lightning: link the invoice, a web link a token. The exception stays
     * out for the same reason, since its message usually quotes the URI (AWT's browse does).
     */
    private fun scheme(uri: String) = uri.substringBefore(':', missingDelimiterValue = "schemeless")
}

/**
 * The window's [UriHandler]. Like Android's, it throws when no app can open the link, so a caller
 * can say so: "No wallet found" instead of a payment link that silently goes nowhere.
 */
object DesktopUriHandler : UriHandler {
    override fun openUri(uri: String) {
        require(DesktopBrowser.open(uri)) { "No app can open a ${uri.substringBefore(':')} link" }
    }
}
