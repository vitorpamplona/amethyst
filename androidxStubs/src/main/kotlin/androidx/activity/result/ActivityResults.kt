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
package androidx.activity.result

import android.net.Uri

/**
 * JVM stand-ins for the Activity Result APIs the app uses to pick media and
 * documents.
 *
 * These carry real behaviour rather than being inert: picking a file is
 * something desktop genuinely does. The contracts describe *what* is being
 * asked for, and [DesktopFilePicker] — installed by the desktop app — decides
 * how to ask. With no picker installed a launch resolves to no selection,
 * which every caller already handles (the user can always cancel a picker).
 */
class ActivityResult(
    val resultCode: Int,
    val data: Any? = null,
) {
    companion object {
        const val RESULT_OK = -1
        const val RESULT_CANCELED = 0
    }
}

/**
 * What kind of media a picker should offer. Declared here rather than nested in
 * the contract so `result` does not have to depend on `result.contract`.
 */
sealed interface VisualMediaType

object ImageOnly : VisualMediaType

object VideoOnly : VisualMediaType

object ImageAndVideo : VisualMediaType

class PickVisualMediaRequest(
    val mediaType: VisualMediaType = ImageAndVideo,
)

/** What the desktop shell must provide to make pickers work. */
interface DesktopFilePickerHost {
    /** `mimeTypes` is advisory; return an empty list when the user cancels. */
    fun pickFiles(
        mimeTypes: List<String>,
        allowMultiple: Boolean,
    ): List<Uri>
}

object DesktopFilePicker {
    @Volatile
    var host: DesktopFilePickerHost? = null

    fun pick(
        mimeTypes: List<String>,
        allowMultiple: Boolean,
    ): List<Uri> = host?.pickFiles(mimeTypes, allowMultiple).orEmpty()
}

class ActivityResultLauncher<I, O>(
    private val launcher: (I) -> Unit,
) {
    fun launch(input: I) = launcher(input)

    fun unregister() = Unit
}
