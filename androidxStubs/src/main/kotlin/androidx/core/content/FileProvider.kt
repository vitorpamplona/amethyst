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
package androidx.core.content

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * JVM stand-in for androidx.core.content.FileProvider.
 *
 * FileProvider exists on Android to hand another app a readable handle to a
 * file without granting it the filesystem — a `content://` URI carrying a
 * temporary grant. Desktop has no such sandbox between applications: the file
 * path *is* the handle, and every consumer of these URIs here (the share sheet,
 * the camera capture target, the .ics and .zip exports) opens it as a file.
 *
 * So the honest translation is the file's own URI. The authority is accepted
 * and ignored because it names an Android manifest provider, which has no
 * desktop counterpart to name.
 */
object FileProvider {
    @JvmStatic
    fun getUriForFile(
        context: Context,
        authority: String,
        file: File,
    ): Uri = Uri.fromFile(file)

    @JvmStatic
    fun getUriForFile(
        context: Context,
        authority: String,
        file: File,
        displayName: String,
    ): Uri = Uri.fromFile(file)
}
