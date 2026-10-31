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
package com.vitorpamplona.amethyst.napplet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.vitorpamplona.amethyst.commons.browser.FileChooserAccept
import com.vitorpamplona.amethyst.commons.napplet.NappletPickedFiles
import com.vitorpamplona.amethyst.napplethost.NappletFileChooser
import com.vitorpamplona.quartz.utils.Log

/**
 * Invisible main-process host for one NAP-FS pick ([NappletFsPickerCoordinator]). It shows the
 * system's document picker and reports on every exit, a choice, a cancel or a system teardown,
 * because the napplet's `fs.pick*` call waits on it.
 *
 * What comes back is filtered as a page's file input is ([NappletPickedFiles]): only other apps'
 * `content://` documents, never one of this app's own providers, whose files would otherwise be read
 * with this app's permissions and handed to the napplet.
 */
class NappletFsPickerActivity : ComponentActivity() {
    private var token: String? = null
    private var reported = false

    // Field, not a local: registerForActivityResult must run before this activity reaches STARTED.
    private val picker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            report(if (result.resultCode == Activity.RESULT_OK) urisOf(result.data) else null)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = intent.getStringExtra(NappletFsPickerCoordinator.EXTRA_TOKEN)
        this.token = token
        val ask = token?.let { NappletFsPickerCoordinator.pendingFor(it) }
        // Nothing waits under this token (the napplet went away), or this is a recreated instance whose
        // picker result can no longer be matched: report nothing rather than stack a second picker.
        if (ask == null || savedInstanceState != null) {
            finish()
            return
        }

        runCatching { picker.launch(intentFor(ask)) }
            .onFailure { e ->
                Log.w(TAG, "No document picker available", e)
                finish()
            }
    }

    private fun intentFor(ask: NappletFsPickerCoordinator.Pending): Intent {
        val save = ask.kind == NappletFsPickerCoordinator.Kind.SAVE
        val intent =
            Intent(if (save) Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (save || ask.wantsWrite) intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        val mimeMap = MimeTypeMap.getSingleton()
        val resolved = FileChooserAccept.resolve(ask.accept) { ext -> mimeMap.getMimeTypeFromExtension(ext) }
        if (save) {
            // A new document has one type: the one its name implies, else the first the napplet named.
            val fromName =
                ask.title
                    ?.substringAfterLast('.', "")
                    ?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { mimeMap.getMimeTypeFromExtension(it) }
            intent.type = fromName ?: resolved.mimeTypes.firstOrNull { !it.contains('*') } ?: FALLBACK_MIME
            ask.title?.let { intent.putExtra(Intent.EXTRA_TITLE, it) }
        } else {
            intent.type = resolved.primaryType
            if (resolved.mimeTypes.size > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, resolved.mimeTypes.toTypedArray())
            if (ask.kind == NappletFsPickerCoordinator.Kind.OPEN_MULTIPLE) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        return intent
    }

    private fun urisOf(data: Intent?): List<Uri>? {
        if (data == null) return null
        val clip = data.clipData
        val uris =
            if (clip != null && clip.itemCount > 0) {
                List(clip.itemCount) { clip.getItemAt(it).uri }
            } else {
                listOfNotNull(data.data)
            }
        return uris
            .filter { NappletPickedFiles.mayHandToPage(it.scheme, it.authority, isOwnCapture = false) { authority -> NappletFileChooser.isOwnAuthority(this, authority) } }
            .takeIf { it.isNotEmpty() }
    }

    /** Fail-open toward the napplet: any unreported teardown still settles its pick. */
    override fun finish() {
        report(null)
        super.finish()
    }

    /** The system can destroy this host without [finish] (a low-memory kill under the picker). */
    override fun onDestroy() {
        report(null)
        super.onDestroy()
    }

    private fun report(uris: List<Uri>?) {
        if (reported) return
        reported = true
        token?.let { NappletFsPickerCoordinator.complete(it, uris) }
    }

    private companion object {
        const val TAG = "NappletFsPicker"
        const val FALLBACK_MIME = "application/octet-stream"
    }
}
