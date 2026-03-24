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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client

@Composable
fun NamecoinIdentifierLink(
    identifier: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var resolvedPubkey by remember(identifier) { mutableStateOf<String?>(null) }

    LaunchedEffect(identifier) {
        try {
            val trimmed = identifier.trimEnd('.', ',', '!', '?', ')', ']')
            val client = accountViewModel.nip05Client
            if (client is Nip05Client) {
                val resolver = client.namecoinResolver
                if (resolver != null) {
                    val result = resolver.resolve(trimmed)
                    resolvedPubkey = result?.pubkey
                }
            }
        } catch (_: Exception) {
            resolvedPubkey = null
        }
    }

    val pubkey = resolvedPubkey
    if (pubkey != null) {
        CreateClickableText(
            clickablePart = identifier,
            suffix = null,
            route = Route.Profile(pubkey),
            nav = nav,
        )
    } else {
        Text(
            text = identifier,
            color = Color(0xFF4A90D9),
            style = LocalTextStyle.current,
        )
    }
}
