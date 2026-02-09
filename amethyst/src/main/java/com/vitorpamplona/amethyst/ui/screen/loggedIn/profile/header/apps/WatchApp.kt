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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WatchApp(
    baseApp: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val appState by observeNote(baseApp, accountViewModel)

    var appLogo by remember(baseApp) { mutableStateOf<String?>(null) }
    var appName by remember(baseApp) { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = appState) {
        withContext(Dispatchers.IO) {
            (appState?.note?.event as? AppDefinitionEvent)?.appMetaData()?.let { metaData ->
                metaData.picture?.ifBlank { null }?.let { newLogo ->
                    if (newLogo != appLogo) appLogo = newLogo
                }
                metaData.name?.ifBlank { null }?.let { newName ->
                    if (newName != appName) appName = newName
                }
            }
        }
    }

    appLogo?.let {
        Box(
            remember {
                Modifier
                    .size(Size35dp)
                    .clickable { nav.nav(Route.Note(baseApp.idHex)) }
            },
        ) {
            AsyncImage(
                model = appLogo,
                contentDescription = appName,
                modifier =
                    remember {
                        Modifier
                            .size(Size35dp)
                            .clip(shape = CircleShape)
                    },
            )
        }
    }
}
