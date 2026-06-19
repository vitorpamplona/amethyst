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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest

/**
 * Opens a napplet/nsite in the sandboxed [NappletHostActivity] (the `:napplet` process). Only
 * the verified manifest data the host needs to render and broker for the applet is passed —
 * the declared `path → hash` map, the Blossom servers, the applet's identity coordinate, and a
 * display title. No account state crosses into the sandbox process.
 */
object NappletLauncher {
    const val EXTRA_PATHS = "napplet_paths"
    const val EXTRA_HASHES = "napplet_hashes"
    const val EXTRA_SERVERS = "napplet_servers"
    const val EXTRA_AUTHOR = "napplet_author"
    const val EXTRA_IDENTIFIER = "napplet_identifier"
    const val EXTRA_AGGREGATE_HASH = "napplet_aggregate_hash"
    const val EXTRA_TITLE = "napplet_title"

    fun launch(
        context: Context,
        manifest: NappletManifest,
        authorPubKey: HexKey,
        identifier: String,
    ) {
        val pathTags = manifest.paths()
        val intent =
            Intent(context, NappletHostActivity::class.java).apply {
                putExtra(EXTRA_PATHS, ArrayList(pathTags.map { it.path }))
                putExtra(EXTRA_HASHES, ArrayList(pathTags.map { it.hash }))
                putExtra(EXTRA_SERVERS, ArrayList(manifest.servers()))
                putExtra(EXTRA_AUTHOR, authorPubKey)
                putExtra(EXTRA_IDENTIFIER, identifier)
                putExtra(EXTRA_AGGREGATE_HASH, manifest.declaredAggregateHash() ?: manifest.computeAggregateHash())
                putExtra(EXTRA_TITLE, manifest.title() ?: identifier.ifBlank { "Napplet" })
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }
}
