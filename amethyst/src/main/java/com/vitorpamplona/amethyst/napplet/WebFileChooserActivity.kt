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

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.vitorpamplona.amethyst.napplethost.NappletFileChooser
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * Invisible main-process host for one file pick made on behalf of an **embedded** WebView surface.
 *
 * The surface renders from the keyless `:napplet` process, which has no Activity to start a picker
 * from, so [WebFileChooserCoordinator] launches this instead. It exists only long enough to run the
 * picker and report the result, and it reports on every exit — a chosen file, a cancel, a system
 * teardown — because the page's `<input type="file">` stays busy until it hears something back.
 */
class WebFileChooserActivity : ComponentActivity() {
    private var token: String? = null
    private var reported = false

    private val picker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            report(NappletFileChooser.parseResult(result.resultCode, result.data)?.map { it.toString() }?.toTypedArray())
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = intent.getStringExtra(WebFileChooserCoordinator.EXTRA_TOKEN)
        this.token = token
        val chooser = token?.let { WebFileChooserCoordinator.chooserFor(it) }
        if (chooser == null) {
            // No pending request under this token: the surface went away, or the process was restarted
            // and the request died with it. Nothing to report to.
            reported = true
            finish()
            return
        }

        // A recreated instance (rotation) already has its pick in flight; re-launching would stack a
        // second picker on top of the first.
        if (savedInstanceState != null) return

        runCatching { picker.launch(chooser) }
            .onFailure { e ->
                Log.w(TAG, "No activity available to pick a file", e)
                Toast.makeText(this, getString(CommonsR.string.browser_file_chooser_unavailable), Toast.LENGTH_LONG).show()
                report(null)
                finish()
            }
    }

    /** Fail-open toward the page: any unreported teardown still releases its file input. */
    override fun finish() {
        report(null)
        super.finish()
    }

    private fun report(uris: Array<String>?) {
        if (reported) return
        reported = true
        token?.let { WebFileChooserCoordinator.complete(it, uris) }
    }

    private companion object {
        private const val TAG = "WebFileChooser"
    }
}
