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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrintJob
import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrinter
import com.vitorpamplona.amethyst.commons.frontpage.PrintJobEnd
import com.vitorpamplona.amethyst.commons.frontpage.PrintJobStage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.dismiss
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_check
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_fix
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_probe
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_pull
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_write
import com.vitorpamplona.amethyst.commons.resources.front_page_job_failed
import com.vitorpamplona.amethyst.commons.resources.front_page_job_tap_to_open
import com.vitorpamplona.amethyst.commons.resources.front_page_ready_title
import com.vitorpamplona.amethyst.commons.ui.broadcast.formatElapsedTime
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay

/** How long the popup shows how a print ended before it goes on its own. */
private const val END_SHOWN_MS = 8_000L

/**
 * The account's print as background work, for the app-wide popup: while it runs and the reader
 * is not on the front page, then how it ended, briefly. On the front page itself it is null (the
 * screen shows the print and the paper), and an ended print is let go there.
 */
@Composable
fun rememberBackgroundPrint(accountViewModel: AccountViewModel): Pair<FrontPagePrinter, FrontPagePrintJob>? {
    val printer = LocalAppServices.current.frontPagePrinters?.of(accountViewModel.account) ?: return null
    val job by printer.job.collectAsStateWithLifecycle()
    val watched by printer.watched.collectAsStateWithLifecycle()

    // Seen on the front page, or shown here long enough: an ended print goes.
    val ended = job?.takeIf { !it.running }
    LaunchedEffect(ended, watched) {
        if (ended == null) return@LaunchedEffect
        if (watched == 0) delay(END_SHOWN_MS)
        printer.dismissJob()
    }
    return job?.takeIf { watched == 0 }?.let { printer to it }
}

/**
 * One print in the background-work popup: its step and how long it has run (a bar that fills while
 * the desks are read), or how it ended. Tapping it opens the front page; × cancels a running
 * print, or puts away an ended one.
 */
@Composable
fun FrontPagePrintJobContent(
    job: FrontPagePrintJob,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                when (val end = job.end) {
                    null -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = scheme.primary)
                    is PrintJobEnd.Ready -> Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = scheme.allGoodColor, modifier = Modifier.size(18.dp), filled = true)
                    else -> Icon(MaterialSymbols.Error, contentDescription = null, tint = scheme.error, modifier = Modifier.size(18.dp), filled = true)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    headline(job),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail =
                    when (val end = job.end) {
                        null -> {
                            val elapsed by rememberElapsed(job.startedAt)
                            "${formatElapsedTime(elapsed)} • ${stringRes(Res.string.front_page_job_tap_to_open)}"
                        }
                        is PrintJobEnd.Ready -> end.headline?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.front_page_job_tap_to_open)
                        else -> stringRes(Res.string.front_page_job_tap_to_open)
                    }
                Text(detail, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val running = job.running
            IconButton(onClick = if (running) onCancel else onDismiss, modifier = Modifier.size(22.dp)) {
                Icon(
                    MaterialSymbols.Close,
                    contentDescription = stringRes(if (running) Res.string.cancel else Res.string.dismiss),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (job.running) {
            Spacer(Modifier.height(6.dp))
            val stage = job.stage
            if (stage is PrintJobStage.Reading && stage.total > 0) {
                LinearProgressIndicator(progress = { stage.fraction }, modifier = Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.surfaceVariant)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.surfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun headline(job: FrontPagePrintJob): String =
    when (val end = job.end) {
        null -> printStageText(job.stage)
        is PrintJobEnd.Ready -> stringRes(Res.string.front_page_ready_title)
        else -> stringRes(Res.string.front_page_job_failed, printEndShort(end).orEmpty())
    }

/** "Writing your Observer…": a print's step in the words the popup and the screen share. */
@Composable
fun printStageText(stage: PrintJobStage): String =
    when (stage) {
        PrintJobStage.Probe -> stringRes(Res.string.front_page_banner_probe)
        is PrintJobStage.Reading -> stringRes(Res.string.front_page_banner_pull, stage.done, stage.total)
        is PrintJobStage.Writing -> stringRes(if (stage.round > 1) Res.string.front_page_banner_fix else Res.string.front_page_banner_write)
        PrintJobStage.Checking -> stringRes(Res.string.front_page_banner_check)
    }

/** Seconds since [startedAt] (unix seconds), ticking once a second while shown. */
@Composable
private fun rememberElapsed(startedAt: Long): State<Long> =
    produceState((TimeUtils.now() - startedAt).coerceAtLeast(0), startedAt) {
        while (true) {
            value = (TimeUtils.now() - startedAt).coerceAtLeast(0)
            delay(1_000)
        }
    }
