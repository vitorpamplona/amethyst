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
package com.vitorpamplona.amethyst.service.crashreports

import android.os.Build
import com.vitorpamplona.amethyst.BuildConfig

/**
 * Longest headline (`ClassName: message`) a report will carry for the throwable or its cause.
 *
 * An exception message is usually one line, but it doesn't have to be: a failed navigation quotes
 * the entire route it could not match, and that route may itself hold a whole previous report.
 * Without a cap each crash report would embed the last one, tripling in size every round (the route
 * re-encodes `%` as `%25`) until the report is too big to send — which is a crash of its own. The
 * stack trace below the headline is what makes a report useful, and it stays whole.
 */
private const val MAX_HEADLINE_LENGTH = 1_000

private fun Throwable.headline(): String {
    val full = toString()
    if (full.length <= MAX_HEADLINE_LENGTH) return full
    // Never cut between the halves of a surrogate pair.
    val cut = if (Character.isHighSurrogate(full[MAX_HEADLINE_LENGTH - 1])) MAX_HEADLINE_LENGTH - 1 else MAX_HEADLINE_LENGTH
    return full.take(cut) + "… (${full.length} chars)"
}

class ReportAssembler {
    fun buildReport(
        e: Throwable,
        threadName: String = Thread.currentThread().name,
    ): String =
        buildString {
            append(e.javaClass.simpleName)
            append(": ")
            appendLine(BuildConfig.VERSION_NAME + "-" + BuildConfig.FLAVOR.uppercase())
            appendLine()

            // Device and Product Information
            appendLine("| Prop | Value |")
            appendLine("|------|-------|")
            append("| Manuf |")
            append(Build.MANUFACTURER)
            appendLine(" |")
            append("| Model |")
            append(Build.MODEL)
            appendLine(" |")
            append("| Prod |")
            append(Build.PRODUCT)
            appendLine(" |")

            // OS Information
            append("| Android |")
            append(Build.VERSION.RELEASE)
            appendLine(" |")
            append("| SDK Int |")
            append(Build.VERSION.SDK_INT.toString())
            appendLine(" |")

            // Hardware Information
            append("| Brand |")
            append(Build.BRAND)
            appendLine(" |")
            append("| Hardware |")
            append(Build.HARDWARE)
            appendLine(" |")

            // Other Useful Information
            append("| Device | ")
            append(Build.DEVICE)
            appendLine(" |")
            append("| Host | ")
            append(Build.HOST)
            appendLine(" |")
            append("| User | ")
            append(Build.USER)
            appendLine(" |")
            append("| Thread | ")
            append(threadName)
            appendLine(" |")
            appendLine()

            appendLine("```")
            append("Thread: ")
            appendLine(threadName)
            appendLine(e.headline())
            e.stackTrace.forEach {
                append("    ")
                appendLine(it.toString())
            }
            val cause = e.cause
            if (cause != null) {
                appendLine("\n\nCause:")
                append("    ")
                appendLine(cause.headline())
                cause.stackTrace.forEach {
                    append("        ")
                    appendLine(it.toString())
                }
            }
            appendLine("```")
        }
}
