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
package com.vitorpamplona.amethyst.desktop.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrintJob
import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrinters
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.desktop_tray_explain_keep
import com.vitorpamplona.amethyst.commons.resources.desktop_tray_explain_text
import com.vitorpamplona.amethyst.commons.resources.desktop_tray_explain_title
import com.vitorpamplona.amethyst.commons.resources.desktop_tray_quit
import com.vitorpamplona.amethyst.commons.resources.front_page_keep_printing
import com.vitorpamplona.amethyst.commons.resources.front_page_quit_and_cancel
import com.vitorpamplona.amethyst.commons.resources.front_page_quit_text
import com.vitorpamplona.amethyst.commons.resources.front_page_quit_title
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import java.io.File

/** What closing the main window, or Quit, does now. */
enum class CloseAction {
    /** Nothing is running that closing would lose: quit. */
    QUIT,

    /** Screen windows are still open: the main window only hides, and the app goes on. */
    HIDE,

    /** A print is running, and quitting would throw it away (maybe paid for): ask first. */
    ASK,
}

/**
 * The desktop's rules for leaving while a front page prints. On Android the print lives in a
 * foreground service and the reader can leave the app; here the process is the print, so closing
 * the last window or quitting would stop it. The reader is asked: keep printing (the window hides
 * and the app runs until the print ends) or quit and cancel.
 */
object PrintQuitRules {
    /**
     * Whether any print is running, the reader's or the morning's: either may have cost money. A
     * press still held ([pressHeld], [FrontPagePrinters.pressHeld]) counts too: a scheduled run
     * holds it until its `ran` is written, after its job says the print ended, and quitting before
     * then leaves its claim unfinished, for another device to claim and pay for again.
     */
    fun printing(
        jobs: Collection<FrontPagePrintJob>,
        pressHeld: Collection<HexKey> = emptyList(),
    ): Boolean = pressHeld.isNotEmpty() || jobs.any { it.running }

    /** Closing the main window: hides while screen windows stay open, else quits, asking first while a print runs. */
    fun onClose(
        printing: Boolean,
        screenWindowsOpen: Boolean,
    ): CloseAction =
        when {
            screenWindowsOpen -> CloseAction.HIDE
            printing -> CloseAction.ASK
            else -> CloseAction.QUIT
        }

    /** Quit from the menu, its shortcut or the updater: asks first while a print runs. */
    fun onQuit(printing: Boolean): CloseAction = if (printing) CloseAction.ASK else CloseAction.QUIT

    /**
     * The print the reader kept going with the window closed ([keptForPrint]) has ended: quit, as
     * they asked when they closed it, once the "ready" notification is out. Not while another print
     * runs, nor once any window shows again (the reader came back and is using the app).
     */
    fun quitsAfterPrint(
        keptForPrint: Boolean,
        printing: Boolean,
        anyWindowShowing: Boolean,
    ): Boolean = keptForPrint && !printing && !anyWindowShowing
}

/**
 * The file that says the main window is hidden while the app runs on (for a print, or in the tray
 * for the NIP-46 signer), so launching the app again brings the running copy back
 * ([DesktopDeepLinks.forwardToRunningInstance]) instead of opening a second.
 */
class HiddenWindowMarker(
    dataDir: File,
    fileName: String,
) {
    private val file = File(dataDir, fileName)

    val present: Boolean get() = file.exists()

    fun set(kept: Boolean) {
        try {
            if (kept) {
                file.writeText("1")
            } else if (!file.delete() && file.exists()) {
                Log.w(TAG) { "Could not delete $file" }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not mark the window kept hidden", e)
        }
    }

    companion object {
        private const val TAG = "HiddenWindowMarker"

        /** Hidden for a print. */
        const val PRINT_FILE = "kept-for-print"

        /** What a relaunch asks the copy kept for a print to open: The Observer, where the print is. */
        const val PRINT_RESTORE_URI = "amethyst:frontpage"

        /** Hidden in the tray. */
        const val TRAY_FILE = "kept-in-tray"

        /** What a relaunch asks the copy in the tray to open: nothing but its window, as it was left. */
        const val TRAY_RESTORE_URI = "amethyst:open"
    }
}

/** "Your Observer is still printing": Keep printing (the window hides) or Quit and cancel. */
@Composable
fun PrintQuitDialog(
    onKeepPrinting: () -> Unit,
    onQuit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.front_page_quit_title)) },
        text = { Text(stringRes(Res.string.front_page_quit_text)) },
        confirmButton = { TextButton(onClick = onKeepPrinting) { Text(stringRes(Res.string.front_page_keep_printing)) } },
        dismissButton = { TextButton(onClick = onQuit) { Text(stringRes(Res.string.front_page_quit_and_cancel)) } },
    )
}

/**
 * The file that says the user was told, once, that closing the window keeps Amethyst running in the
 * tray while a NIP-46 signer runs ([TrayExplainDialog]). Missing until they answer it.
 */
class TrayExplainedMarker(
    dataDir: File,
) {
    private val file = File(dataDir, FILE)

    val present: Boolean get() = file.exists()

    fun set() {
        try {
            file.writeText("1")
        } catch (e: Exception) {
            Log.w(TAG, "Could not remember the tray was explained", e)
        }
    }

    private companion object {
        const val TAG = "TrayExplainedMarker"
        const val FILE = "tray-explained"
    }
}

/**
 * Shown the first time closing the window would send Amethyst to the tray: where it went, why, and a
 * way to quit instead. Only Back, the buttons or a click outside decide; dismissing it keeps the
 * window open.
 */
@Composable
fun TrayExplainDialog(
    onKeepRunning: () -> Unit,
    onQuit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.desktop_tray_explain_title)) },
        text = { Text(stringRes(Res.string.desktop_tray_explain_text)) },
        confirmButton = { TextButton(onClick = onKeepRunning) { Text(stringRes(Res.string.desktop_tray_explain_keep)) } },
        dismissButton = { TextButton(onClick = onQuit) { Text(stringRes(Res.string.desktop_tray_quit)) } },
    )
}
