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
package androidx.activity.result.contract

import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.DesktopFilePicker
import androidx.activity.result.ImageAndVideo
import androidx.activity.result.ImageOnly
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.VideoOnly
import androidx.activity.result.VisualMediaType

object ActivityResultContracts {
    abstract class ActivityResultContract<I, O> {
        abstract fun launch(input: I): O
    }

    /** Mirrors `GetContent`: one file of a given MIME type, or null. */
    class GetContent : ActivityResultContract<String, Uri?>() {
        override fun launch(input: String): Uri? = DesktopFilePicker.pick(listOf(input), allowMultiple = false).firstOrNull()
    }

    class GetMultipleContents : ActivityResultContract<String, List<Uri>>() {
        override fun launch(input: String): List<Uri> = DesktopFilePicker.pick(listOf(input), allowMultiple = true)
    }

    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun launch(input: Array<String>): Uri? = DesktopFilePicker.pick(input.toList(), allowMultiple = false).firstOrNull()
    }

    class CreateDocument(
        private val mimeType: String,
    ) : ActivityResultContract<String, Uri?>() {
        override fun launch(input: String): Uri? = DesktopFilePicker.pick(listOf(mimeType), allowMultiple = false).firstOrNull()
    }

    class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
        companion object {
            val ImageOnly: VisualMediaType = androidx.activity.result.ImageOnly
            val VideoOnly: VisualMediaType = androidx.activity.result.VideoOnly
            val ImageAndVideo: VisualMediaType = androidx.activity.result.ImageAndVideo
        }

        override fun launch(input: PickVisualMediaRequest): Uri? = DesktopFilePicker.pick(mimeTypesFor(input.mediaType), allowMultiple = false).firstOrNull()
    }

    class PickMultipleVisualMedia(
        private val maxItems: Int = Int.MAX_VALUE,
    ) : ActivityResultContract<PickVisualMediaRequest, List<Uri>>() {
        override fun launch(input: PickVisualMediaRequest): List<Uri> = DesktopFilePicker.pick(mimeTypesFor(input.mediaType), allowMultiple = true).take(maxItems)
    }

    /** Desktop grants what it can access; there is no runtime permission model. */
    class RequestPermission : ActivityResultContract<String, Boolean>() {
        override fun launch(input: String): Boolean = true
    }

    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() {
        override fun launch(input: Array<String>): Map<String, Boolean> = input.associateWith { true }
    }

    class StartActivityForResult : ActivityResultContract<android.content.Intent, ActivityResult>() {
        override fun launch(input: android.content.Intent) = ActivityResult(ActivityResult.RESULT_CANCELED)
    }

    private fun mimeTypesFor(type: VisualMediaType): List<String> =
        when (type) {
            ImageOnly -> listOf("image/*")
            VideoOnly -> listOf("video/*")
            else -> listOf("image/*", "video/*")
        }
}
