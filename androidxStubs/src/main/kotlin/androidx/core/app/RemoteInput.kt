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
package androidx.core.app

import android.content.Intent
import android.os.Bundle

/**
 * JVM stand-in for androidx.core.app.RemoteInput — the declaration of a text
 * field the user can fill in from inside a notification.
 *
 * The declaration and the intent plumbing are platform-neutral and are
 * implemented exactly: [addResultsToIntent] writes the typed text into the
 * intent's results bundle and [getResultsFromIntent] reads it back, which is
 * the whole contract the app's reply receiver depends on.
 *
 * What varies is who does the typing. Android's shade renders the field
 * itself; a desktop tray balloon cannot. Until a presenter offers its own
 * input surface, a reply action arrives with no results and the receiver's
 * existing "blank reply, do nothing" path handles it — so the worst case is
 * the button doing nothing, not a reply going out empty. A presenter that can
 * collect text (a small reply window, or a platform that supports inline
 * replies) fills the bundle through [addResultsToIntent] and everything
 * downstream works unchanged.
 */
class RemoteInput private constructor(
    val resultKey: String,
    val label: CharSequence?,
    val choices: Array<CharSequence>,
    val allowFreeFormInput: Boolean,
    val extras: Bundle,
) {
    class Builder(
        private val resultKey: String,
    ) {
        private var label: CharSequence? = null
        private var choices: Array<CharSequence> = emptyArray()
        private var allowFreeFormInput = true
        private val extras = Bundle()

        fun setLabel(value: CharSequence?) = apply { label = value }

        fun setChoices(value: Array<CharSequence>?) = apply { choices = value ?: emptyArray() }

        fun setAllowFreeFormInput(value: Boolean) = apply { allowFreeFormInput = value }

        fun addExtras(value: Bundle?) = apply { if (value != null) extras.putAll(value) }

        fun build() = RemoteInput(resultKey, label, choices, allowFreeFormInput, extras)
    }

    companion object {
        /** Matches the platform key, so an intent round-trips through either. */
        const val EXTRA_RESULTS_DATA = "android.remoteinput.resultsData"

        /** The text the user typed, or null when nothing was collected. */
        @JvmStatic
        fun getResultsFromIntent(intent: Intent?): Bundle? = intent?.getBundleExtra(EXTRA_RESULTS_DATA)

        /** Called by a presenter that managed to collect input. */
        @JvmStatic
        fun addResultsToIntent(
            remoteInputs: Array<RemoteInput>?,
            intent: Intent,
            results: Bundle,
        ) {
            val existing = intent.getBundleExtra(EXTRA_RESULTS_DATA) ?: Bundle()
            existing.putAll(results)
            intent.putExtra(EXTRA_RESULTS_DATA, existing)
        }
    }
}
