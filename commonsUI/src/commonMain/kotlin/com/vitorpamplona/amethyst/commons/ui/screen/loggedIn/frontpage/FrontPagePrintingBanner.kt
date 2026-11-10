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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageRun
import com.vitorpamplona.amethyst.commons.frontpage.PrintStep
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_check
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_probe
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_pull
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_write
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * A slim pill over the app while the reader's paper prints somewhere they are not looking: the
 * print belongs to the account, so it keeps going after they leave the front page. Tapping it
 * goes back there; the front page screen shows its own progress, so the pill hides there.
 */
@Composable
fun FrontPagePrintingBanner(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val printer = LocalAppServices.current.frontPagePrinters?.of(accountViewModel.account) ?: return
    val run by printer.run.collectAsStateWithLifecycle()
    val watched by printer.watched.collectAsStateWithLifecycle()
    val step = (run as? FrontPageRun.Running)?.step

    AnimatedVisibility(
        visible = step != null && watched == 0,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.clip(CircleShape).clickable { nav.nav(Route.FrontPage()) },
        ) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.inversePrimary)
                Spacer(Modifier.width(10.dp))
                Text(
                    when (step) {
                        is PrintStep.Pull -> stringRes(Res.string.front_page_banner_pull, step.desksDone, step.desksTotal)
                        is PrintStep.Write -> stringRes(Res.string.front_page_banner_write)
                        is PrintStep.Check -> stringRes(Res.string.front_page_banner_check)
                        else -> stringRes(Res.string.front_page_banner_probe)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(onClick = printer::cancel, modifier = Modifier.size(36.dp)) {
                    Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.cancel), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
