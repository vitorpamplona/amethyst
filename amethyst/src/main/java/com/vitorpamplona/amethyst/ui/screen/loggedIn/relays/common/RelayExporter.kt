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
import com.vitorpamplona.amethyst.R

class RelayExporter(
    val context: Context,
) {
    fun export(
        homeRelays: List<BasicRelaySetupInfo>,
        notifRelays: List<BasicRelaySetupInfo>,
        dmRelays: List<BasicRelaySetupInfo>,
        privateOutboxRelays: List<BasicRelaySetupInfo>,
        proxyRelays: List<BasicRelaySetupInfo>,
        broadcastRelays: List<BasicRelaySetupInfo>,
        indexerRelays: List<BasicRelaySetupInfo>,
        searchRelays: List<BasicRelaySetupInfo>,
        localRelays: List<BasicRelaySetupInfo>,
        trustedRelays: List<BasicRelaySetupInfo>,
        favoriteRelays: List<BasicRelaySetupInfo>,
        blockedRelays: List<BasicRelaySetupInfo>,
    ) {
        val text =
            buildExportText(
                homeRelays,
                notifRelays,
                dmRelays,
                privateOutboxRelays,
                proxyRelays,
                broadcastRelays,
                indexerRelays,
                searchRelays,
                localRelays,
                trustedRelays,
                favoriteRelays,
                blockedRelays,
            )

        val sendIntent =
            Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_TITLE, context.getString(R.string.export_relay_settings))
            }

        val shareIntent =
            Intent.createChooser(
                sendIntent,
                context.getString(R.string.export_relay_settings),
            )
        context.startActivity(shareIntent)
    }

    fun buildExportText(
        homeRelays: List<BasicRelaySetupInfo>,
        notifRelays: List<BasicRelaySetupInfo>,
        dmRelays: List<BasicRelaySetupInfo>,
        privateOutboxRelays: List<BasicRelaySetupInfo>,
        proxyRelays: List<BasicRelaySetupInfo>,
        broadcastRelays: List<BasicRelaySetupInfo>,
        indexerRelays: List<BasicRelaySetupInfo>,
        searchRelays: List<BasicRelaySetupInfo>,
        localRelays: List<BasicRelaySetupInfo>,
        trustedRelays: List<BasicRelaySetupInfo>,
        favoriteRelays: List<BasicRelaySetupInfo>,
        blockedRelays: List<BasicRelaySetupInfo>,
    ): String {
        val builder = StringBuilder()
        builder.appendLine("# ${context.getString(R.string.relay_settings)}")
        builder.appendLine()

        formatSection(R.string.public_home_section, R.string.public_home_section_explainer, homeRelays, builder)
        formatSection(R.string.public_notif_section, R.string.public_notif_section_explainer, notifRelays, builder)
        formatSection(R.string.private_inbox_section, R.string.private_inbox_section_explainer, dmRelays, builder)
        formatSection(R.string.private_outbox_section, R.string.private_outbox_section_explainer, privateOutboxRelays, builder)
        formatSection(R.string.proxy_section, R.string.proxy_section_explainer, proxyRelays, builder)
        formatSection(R.string.broadcast_section, R.string.broadcast_section_explainer, broadcastRelays, builder)
        formatSection(R.string.indexer_section, R.string.indexer_section_explainer, indexerRelays, builder)
        formatSection(R.string.search_section, R.string.search_section_explainer, searchRelays, builder)
        formatSection(R.string.local_section, R.string.local_section_explainer, localRelays, builder)
        formatSection(R.string.trusted_section, R.string.trusted_section_explainer, trustedRelays, builder)
        formatSection(R.string.favorite_section, R.string.favorite_section_explainer, favoriteRelays, builder)
        formatSection(R.string.blocked_section, R.string.blocked_section_explainer, blockedRelays, builder)

        return builder.toString().trimEnd()
    }

    private fun formatSection(
        titleRes: Int,
        descriptionRes: Int,
        relays: List<BasicRelaySetupInfo>,
        builder: StringBuilder,
    ) {
        if (relays.isEmpty()) return
        builder.appendLine("## ${context.getString(titleRes)}")
        builder.appendLine("# ${context.getString(descriptionRes)}")
        builder.appendLine()
        relays.forEach { relay ->
            builder.appendLine(relay.relay.url)
        }
        builder.appendLine()
    }
}
