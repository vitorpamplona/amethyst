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

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileNotFoundException
import java.io.FileInputStream
import java.io.InputStreamReader

private const val STACK_TRACE_FILENAME = "stack.trace"

class CrashReportCache(
    val appContext: Context,
) {
    private fun outputStream() = appContext.openFileOutput(STACK_TRACE_FILENAME, Context.MODE_PRIVATE)

    private fun deleteReport() = appContext.deleteFile(STACK_TRACE_FILENAME)

    private fun inputStreamOrNull(): FileInputStream? =
        try {
            appContext.openFileInput(STACK_TRACE_FILENAME)
        } catch (_: FileNotFoundException) {
            null
        }

    fun writeReport(report: String) {
        val trace = outputStream()
        trace.write(report.toByteArray())
        trace.close()
    }

    suspend fun loadAndDelete(): String? =
        withContext(Dispatchers.IO) {
            val stack =
                inputStreamOrNull()?.use { inStream ->
                    InputStreamReader(inStream).use { reader ->
                        reader.readText()
                    }
                }
            deleteReport()
            stack
        }
}
