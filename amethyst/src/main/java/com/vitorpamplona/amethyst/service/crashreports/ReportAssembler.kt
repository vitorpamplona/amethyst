/**
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
package com.vitorpamplona.amethyst.service.crashreports

import android.os.Build
import com.vitorpamplona.amethyst.BuildConfig

class ReportAssembler {
    fun buildReport(e: Throwable): String =
        buildString {
            // Device and Product Information
            append("Amethyst Version: ")
            appendLine(BuildConfig.VERSION_NAME + "-" + BuildConfig.FLAVOR.uppercase())
            appendLine()

            // Device and Product Information
            append("Manufacturer: ")
            appendLine(Build.MANUFACTURER)
            append("Model: ")
            appendLine(Build.MODEL)
            append("Product: ")
            appendLine(Build.PRODUCT)
            appendLine()

            // OS Information
            append("Android Version: ")
            appendLine(Build.VERSION.RELEASE)
            append("SDK Int: ")
            appendLine(Build.VERSION.SDK_INT.toString())
            append("Build ID: ")
            appendLine(Build.ID)
            appendLine()

            // Hardware Information
            append("Brand: ")
            appendLine(Build.BRAND)
            append("Hardware: ")
            appendLine(Build.HARDWARE)
            appendLine()

            // Other Useful Information
            append("Device: ")
            appendLine(Build.DEVICE)
            append("Host: ")
            appendLine(Build.HOST)
            append("User: ")
            appendLine(Build.USER)
            appendLine()

            append(e.toString())
            append("\n")
            e.stackTrace.forEach {
                append("    ")
                append(it.toString())
                append("\n")
            }
            val cause = e.cause
            if (cause != null) {
                append("\n\nCause:\n")
                append("    ")
                append(cause.toString())
                append("\n")
                cause.stackTrace.forEach {
                    append("        ")
                    append(it.toString())
                    append("\n")
                }
            }
        }
}
