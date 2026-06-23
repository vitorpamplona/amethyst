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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.browser

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * The in-app web browser tab. The page renders in the keyless `:napplet` process and is streamed into
 * this (key-holding) main process as a surface (see [EmbeddedBrowserController]); the trusted address
 * bar is drawn here, around the embedded surface, so the sandbox can never spoof the URL. NIP-07
 * `window.nostr` is injected in the sandbox, consent-gated and scoped per visited origin.
 *
 * Requires API 30+ (SurfaceControlViewHost); below that the Browser nav item is hidden, so this screen
 * is unreachable — the fallback message is just defense in depth.
 */
@Composable
fun BrowserScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        EmbeddedBrowser()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.browser_unsupported),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun EmbeddedBrowser() {
    val context = LocalContext.current
    val proxyPort = remember { Amethyst.instance.torManager.activePortOrNull.value ?: -1 }

    var address by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var torOn by remember { mutableStateOf(proxyPort > 0) }

    val controller =
        remember {
            EmbeddedBrowserController(context.applicationContext, proxyPort, proxyPort > 0).apply {
                onUrlChanged = { url, back ->
                    if (url != "about:blank") address = url
                    canGoBack = back
                }
            }
        }

    DisposableEffect(Unit) {
        controller.bind("about:blank")
        onDispose { controller.unbind() }
    }

    BackHandler(enabled = canGoBack) { controller.back() }

    Scaffold(
        topBar = {
            BrowserAddressBar(
                address = address,
                onAddressChange = { address = it },
                onGo = { controller.navigate(address) },
                onReload = { controller.reload() },
                onBack = { controller.back() },
                canGoBack = canGoBack,
                showTor = proxyPort > 0,
                torOn = torOn,
                onToggleTor = {
                    torOn = !torOn
                    controller.setTor(torOn)
                },
            )
        },
    ) { padding ->
        AndroidView(
            factory = { ctx -> SandboxedSdkView(ctx).also { controller.attachView(it) } },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }
}

@Composable
private fun BrowserAddressBar(
    address: String,
    onAddressChange: (String) -> Unit,
    onGo: () -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    showTor: Boolean,
    torOn: Boolean,
    onToggleTor: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        TextField(
            value = address,
            onValueChange = onAddressChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.browser_address_hint)) },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
        if (showTor) {
            IconButton(onClick = onToggleTor) {
                Icon(
                    MaterialSymbols.Security,
                    contentDescription = stringResource(if (torOn) R.string.browser_tor_on else R.string.browser_tor_off),
                    tint = if (torOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onReload) {
            Icon(MaterialSymbols.Refresh, contentDescription = stringResource(R.string.browser_reload))
        }
    }
}
