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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common

import android.content.Context
import android.content.Intent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.export_relay_settings
import com.vitorpamplona.amethyst.commons.resources.relay_settings
import com.vitorpamplona.amethyst.commons.ui.loadStringRes

class RelayExporter(
    val context: Context,
) {
    suspend fun export(collection: RelayListCollection) {
        val text = buildExportText(collection)

        val sendIntent =
            Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_TITLE, loadStringRes(Res.string.export_relay_settings))
            }

        val shareIntent =
            Intent.createChooser(
                sendIntent,
                loadStringRes(Res.string.export_relay_settings),
            )
        context.startActivity(shareIntent)
    }

    suspend fun buildExportText(collection: RelayListCollection): String {
        val builder = StringBuilder()
        builder.appendLine("# ${loadStringRes(Res.string.relay_settings)}")
        builder.appendLine()

        collection.sections().forEach { section ->
            formatSection(section, builder)
        }

        return builder.toString().trimEnd()
    }

    private fun formatSection(
        section: RelaySection,
        builder: StringBuilder,
    ) {
        if (section.relays.isEmpty()) return
        builder.appendLine("## ${loadStringRes(section.titleRes)}")
        builder.appendLine("# ${loadStringRes(section.descriptionRes)}")
        builder.appendLine()
        section.relays.forEach { relay ->
            builder.appendLine(relay.relay.url)
        }
        builder.appendLine()
    }
}
