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
import androidx.activity.ComponentActivity
import com.vitorpamplona.amethyst.napplethost.WebFileChooserLauncher

/**
 * Invisible main-process host for one file pick made on behalf of an **embedded** WebView surface.
 *
 * The surface renders from the keyless `:napplet` process, which has no Activity to start a picker or
 * a permission prompt from, so [WebFileChooserCoordinator] launches this instead. It exists only long
 * enough to run the pick and report the result, and it reports on every exit — a chosen file, a
 * cancel, a system teardown — because the page's `<input type="file">` stays busy until it hears
 * something back.
 */
class WebFileChooserActivity : ComponentActivity() {
    private var token: String? = null
    private var reported = false

    // Field, not a local: registerForActivityResult must run before this activity reaches STARTED.
    private val chooser =
        WebFileChooserLauncher(this) { uris ->
            report(uris?.map { it.toString() }?.toTypedArray())
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = intent.getStringExtra(WebFileChooserCoordinator.EXTRA_TOKEN)
        this.token = token
        val ask = token?.let { WebFileChooserCoordinator.pendingFor(it) }
        if (ask == null) {
            // No pending request under this token: the surface went away, or the process restarted and
            // the request died with it. Nothing to report to.
            reported = true
            finish()
            return
        }

        // A recreated instance has lost the in-flight request that its result would be matched against
        // (configChanges keeps this rare — a system kill, not a rotation), and relaunching would stack a
        // second picker on the first. Release the page's input now rather than let it wait on a result
        // that can no longer be routed anywhere.
        if (savedInstanceState != null) {
            finish()
            return
        }

        chooser.launch(
            acceptTypes = ask.acceptTypes,
            allowMultiple = ask.allowMultiple,
            captureEnabled = ask.captureEnabled,
            pageTitle = ask.pageTitle,
        )
    }

    /** Fail-open toward the page: any unreported teardown still releases its file input. */
    override fun finish() {
        report(null)
        super.finish()
    }

    /**
     * The system can destroy this host without ever calling [finish] — a low-memory kill while the
     * picker is on top. Without this the page's file input would wait on a result nobody is left to
     * send, dead for the life of the page, and the coordinator would hold the reply callback (and the
     * controller behind it) forever.
     */
    override fun onDestroy() {
        chooser.teardown()
        report(null)
        super.onDestroy()
    }

    private fun report(uris: Array<String>?) {
        if (reported) return
        reported = true
        token?.let { WebFileChooserCoordinator.complete(it, uris) }
    }
}
