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
package com.vitorpamplona.amethyst.ios

import com.vitorpamplona.quartz.utils.Log
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ReportUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook

/** Application Support/amethyst: kept across launches and in backups, hidden from the Files app. */
internal fun appFilesPath(): Path = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String).toPath() / "amethyst"

/**
 * The last crash's report, as on Android and the desktop: an uncaught Kotlin exception is written
 * to Application Support/amethyst/stack.trace as the app dies, and the next start offers it to the
 * user (AppServices.takeCrashReport), who decides whether to send it.
 */
internal object IosCrashReports {
    private const val TAG = "IosCrashReports"
    private const val FILE_NAME = "stack.trace"
    private const val MAX_HEADLINE_LENGTH = 1_000

    private val file get() = appFilesPath() / FILE_NAME

    /** Installs the hook. Called before the service graph is built, so a crash building it is kept too. */
    @OptIn(ExperimentalNativeApi::class)
    private var previousHook: ReportUnhandledExceptionHook? = null

    @OptIn(ExperimentalNativeApi::class)
    fun install(appVersion: String) {
        previousHook =
            setUnhandledExceptionHook { e ->
                // Out-of-memory reports are junk, as on Android.
                if (e !is OutOfMemoryError) runCatching { write(report(e, appVersion)) }
                previousHook?.invoke(e)
            }
    }

    /** The kept report, removed as it is read; null when the last run did not crash. */
    fun takeReport(): String? =
        try {
            val path = file
            if (!FileSystem.SYSTEM.exists(path)) {
                null
            } else {
                FileSystem.SYSTEM.read(path) { readUtf8() }.also { FileSystem.SYSTEM.delete(path) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the crash report", e)
            null
        }

    /** Written to a temp file first and moved into place, so a write cut short leaves no garbled report. */
    private fun write(report: String) {
        val dir = appFilesPath()
        FileSystem.SYSTEM.createDirectories(dir)
        val temp = dir / "$FILE_NAME.tmp"
        FileSystem.SYSTEM.write(temp) { writeUtf8(report) }
        FileSystem.SYSTEM.atomicMove(temp, dir / FILE_NAME)
    }

    private fun Throwable.headline(): String {
        val full = toString()
        if (full.length <= MAX_HEADLINE_LENGTH) return full
        val cut = if (full[MAX_HEADLINE_LENGTH - 1].isHighSurrogate()) MAX_HEADLINE_LENGTH - 1 else MAX_HEADLINE_LENGTH
        return full.take(cut) + "… (${full.length} chars)"
    }

    /** The same layout as the JVM's ReportAssembler: a headline, a device table, the stack. */
    @OptIn(ExperimentalNativeApi::class)
    private fun report(
        e: Throwable,
        appVersion: String,
    ): String =
        buildString {
            append(e::class.qualifiedName ?: e::class.simpleName ?: "Throwable")
            append(": ")
            appendLine("$appVersion-IOS")
            appendLine()
            val device = UIDevice.currentDevice
            appendLine("| Prop | Value |")
            appendLine("|------|-------|")
            appendLine("| OS | ${device.systemName} ${device.systemVersion} |")
            appendLine("| Model | ${device.model} |")
            appendLine()
            appendLine("```")
            appendLine(e.headline())
            e.getStackTrace().forEach {
                append("    at ")
                appendLine(it)
            }
            val cause = e.cause
            if (cause != null) {
                appendLine("\n\nCause:")
                append("    ")
                appendLine(cause.headline())
                cause.getStackTrace().forEach {
                    append("        at ")
                    appendLine(it)
                }
            }
            appendLine("```")
        }
}
