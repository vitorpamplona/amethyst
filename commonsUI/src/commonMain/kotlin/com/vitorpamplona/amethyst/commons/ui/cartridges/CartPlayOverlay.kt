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
package com.vitorpamplona.amethyst.commons.ui.cartridges

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.cartridges.CartDetector
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cart_play
import com.vitorpamplona.amethyst.commons.resources.cart_play_description
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri

/**
 * A Play button over an image in a note when that image is a game cartridge. [content] has
 * already passed [CartDetector.mightBeCart]; this confirms it (reading only the PNG header, and
 * only when the user lets images load) and opens [CartPlayerDialog].
 */
@Composable
fun CartPlayOverlay(
    content: MediaUrlImage,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val blossom = remember(content.url) { if (content.url.startsWith("blossom:")) BlossomUri.parse(content.url) else null }
    val url = if (blossom != null) rememberBlossomServerUrl(content.url) else content.url
    val httpUrl = url ?: return
    val mayProbe = content.dim != null || accountViewModel.settings.showImages()
    val isCart by produceState(CartDetector.known(httpUrl), httpUrl, mayProbe) {
        if (value == null && mayProbe) value = CartDetector.hasCartShape(httpUrl, content.dim, accountViewModel.httpClientBuilder)
    }
    if (isCart != true) return

    var playing by rememberSaveable(httpUrl) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        PlayButton { playing = true }
    }
    if (playing) {
        CartPlayerDialog(
            url = httpUrl,
            sha256 = content.hash ?: blossom?.sha256,
            accountViewModel = accountViewModel,
            onDismiss = { playing = false },
        )
    }
}

/** The server URL a `blossom:` URI resolves to through the app's BUD-10 finder, null until one answers. */
@Composable
private fun rememberBlossomServerUrl(uri: String): String? {
    val finder = LocalAppServices.current.blossomServerFinder
    val url by produceState(finder.cachedServerUrl(uri), uri, finder) {
        if (value == null) value = finder.findServerUrl(uri)
    }
    return url
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    val description = stringRes(Res.string.cart_play_description)
    Button(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.7f), contentColor = Color.White),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(symbol = MaterialSymbols.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(Res.string.cart_play), modifier = Modifier.padding(end = 4.dp))
        }
    }
}
