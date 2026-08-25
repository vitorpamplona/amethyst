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
package com.vitorpamplona.amethyst.napplethost

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient.FileChooserParams
import com.vitorpamplona.amethyst.commons.browser.FileChooserAccept
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * The picker behind an HTML `<input type="file">` in any of Amethyst's WebViews.
 *
 * A WebView shows no picker of its own: unless the app overrides
 * `WebChromeClient.onShowFileChooser`, tapping a file input is a silent no-op. Every host here does
 * override it, and they all build their Intent through this so the browser, the full-screen napplet /
 * nSite sandbox, and both embedded surfaces filter and multi-select identically.
 *
 * The request is described by plain data (accept list, multi-select, title) rather than by a
 * ready-made Intent, because the two embedded hosts have no Activity of their own and must ask the
 * main process to launch the picker for them. Shipping data keeps the keyless `:napplet` process
 * unable to hand the trusted process an arbitrary Intent to start — it can only ask for a file picker.
 */
object NappletFileChooser {
    /**
     * Builds the picker for a request described by [acceptTypes] / [allowMultiple].
     *
     * `ACTION_GET_CONTENT` (rather than `ACTION_OPEN_DOCUMENT`) so gallery and camera-roll apps that
     * are not document providers still show up — the same trade-off Chrome makes; the page only ever
     * needs to read the bytes once, not to hold a persistable grant. [pageTitle] is the page-supplied
     * chooser title, used when it set one.
     */
    fun buildIntent(
        context: Context,
        acceptTypes: List<String>,
        allowMultiple: Boolean,
        pageTitle: CharSequence? = null,
    ): Intent {
        val mimeMap = MimeTypeMap.getSingleton()
        val resolved = FileChooserAccept.resolve(acceptTypes) { ext -> mimeMap.getMimeTypeFromExtension(ext) }

        val pick =
            Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(resolved.primaryType)
                // The provider sets this on the result too; asking for it up front makes the read grant
                // explicit. Grants are per-UID, so a URI picked by the main process is readable by the
                // WebView in `:napplet` without any re-granting.
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // Only worth sending when it says more than `type` already does.
        if (resolved.mimeTypes.size > 1) {
            pick.putExtra(Intent.EXTRA_MIME_TYPES, resolved.mimeTypes.toTypedArray())
        }
        if (allowMultiple) pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        val title = pageTitle?.takeIf { it.isNotBlank() } ?: context.getString(CommonsR.string.browser_file_chooser_title)
        return Intent.createChooser(pick, title)
    }

    /** Convenience overload for the two Activity hosts, which hold the real [FileChooserParams]. */
    fun buildIntent(
        context: Context,
        params: FileChooserParams,
    ): Intent =
        buildIntent(
            context = context,
            acceptTypes = params.acceptTypes?.toList().orEmpty(),
            allowMultiple = params.mode == FileChooserParams.MODE_OPEN_MULTIPLE,
            pageTitle = params.title,
        )

    /**
     * Turns an activity result into the array the page's `filePathCallback` expects, or null when the
     * user backed out. Handles both the single-URI and the `ClipData` multi-select shapes.
     */
    fun parseResult(
        resultCode: Int,
        data: Intent?,
    ): Array<Uri>? = FileChooserParams.parseResult(resultCode, data)
}
