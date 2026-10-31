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

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs a NAP-FS picker (`fs.pickFile`, `pickFiles`, `pickSaveFile`) for the broker, which lives in a
 * Service and has no Activity to start one from. The request waits here under a one-time token and
 * [NappletFsPickerActivity] shows the system's document picker for it, as [WebFileChooserCoordinator]
 * does for a page's `<input type="file">`.
 *
 * The pickers are the Storage Access Framework's (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`):
 * their grant belongs to the app, not to the Activity that asked, so the files stay readable from the
 * broker's Service after the picker host has finished.
 */
object NappletFsPickerCoordinator {
    enum class Kind {
        OPEN,
        OPEN_MULTIPLE,
        SAVE,
    }

    /**
     * One picker to show: [accept] holds the napplet's advisory filters as HTML `accept` tokens (MIME
     * types or `.ext`; empty shows everything), [title] names a new file.
     */
    class Pending(
        val kind: Kind,
        val accept: List<String>,
        val title: String?,
        val wantsWrite: Boolean,
        val result: CompletableDeferred<List<Uri>?>,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    /** Shows the picker and returns what the user chose, or null when they cancelled or no picker could run. */
    suspend fun pick(
        context: Context,
        kind: Kind,
        accept: List<String>,
        title: String?,
        wantsWrite: Boolean,
    ): List<Uri>? {
        val token = UUID.randomUUID().toString()
        val request = Pending(kind, accept, title, wantsWrite, CompletableDeferred())
        pending[token] = request
        try {
            val launch =
                Intent(context, NappletFsPickerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_TOKEN, token)
            val started = runCatching { context.startActivity(launch) }
            if (started.isFailure) {
                Log.w(TAG, "Could not start the NAP-FS picker host", started.exceptionOrNull())
                return null
            }
            return request.result.await()
        } finally {
            // Cancelled (the surface went away) or answered: nothing is left waiting under the token.
            pending.remove(token)
        }
    }

    /** Called by [NappletFsPickerActivity] to learn what to show. */
    fun pendingFor(token: String): Pending? = pending[token]

    /** Called by [NappletFsPickerActivity] with the outcome; null = nothing chosen. Resolves at most once. */
    fun complete(
        token: String,
        uris: List<Uri>?,
    ) {
        pending.remove(token)?.result?.complete(uris)
    }

    const val EXTRA_TOKEN = "napplet_fs_picker_token"

    private const val TAG = "NappletFsPicker"
}
