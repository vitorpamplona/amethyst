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
package com.vitorpamplona.amethyst.napplethost

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.net.toUri

/**
 * Shows a web page's `alert()` / `confirm()` / `prompt()` / `beforeunload` dialogs for the full-screen
 * browser, the way Chrome does: titled with the page's origin ("example.com says") so a page can't pass
 * its dialog off as Amethyst's own, and — from the second dialog on the same page — offering "Block
 * dialogs from this page", so a script looping on alert() can't trap the user. The block lasts until the
 * next main-frame navigation ([onMainFrameNavigation]).
 *
 * Every [JsResult] handed in is answered exactly once on every path (button, back, dismissal, a dialog
 * already showing, the Activity going away): until it is, the page's JavaScript stays blocked.
 */
internal class BrowserJsDialogs(
    private val activity: Activity,
) {
    private var showing: AlertDialog? = null
    private var shownOnThisPage = 0
    private var blocked = false

    /** A new main-frame load: the page that earned the block is gone. */
    fun onMainFrameNavigation() {
        shownOnThisPage = 0
        blocked = false
    }

    /** Closes a dialog still up (the Activity is being destroyed); its result is cancelled. */
    fun dismiss() {
        showing?.dismiss()
        showing = null
    }

    fun alert(
        url: String?,
        message: String?,
        result: JsResult,
    ): Boolean =
        show(result, onBlocked = { result.cancel() }) { answer ->
            setTitle(titleFor(url))
            setMessage(message.orEmpty())
            setPositiveButton(android.R.string.ok) { _, _ -> answer { result.confirm() } }
        }

    fun confirm(
        url: String?,
        message: String?,
        result: JsResult,
    ): Boolean =
        show(result, onBlocked = { result.cancel() }) { answer ->
            setTitle(titleFor(url))
            setMessage(message.orEmpty())
            setPositiveButton(android.R.string.ok) { _, _ -> answer { result.confirm() } }
            setNegativeButton(android.R.string.cancel) { _, _ -> answer { result.cancel() } }
        }

    fun prompt(
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult,
    ): Boolean {
        val field =
            EditText(activity).apply {
                setText(defaultValue.orEmpty())
                setSelectAllOnFocus(true)
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT
            }
        val container =
            FrameLayout(activity).apply {
                val pad = (20 * activity.resources.displayMetrics.density).toInt()
                setPadding(pad, 0, pad, 0)
                addView(field)
            }
        return show(result, onBlocked = { result.cancel() }) { answer ->
            setTitle(titleFor(url))
            setMessage(message.orEmpty())
            setView(container)
            setPositiveButton(android.R.string.ok) { _, _ -> answer { result.confirm(field.text?.toString().orEmpty()) } }
            setNegativeButton(android.R.string.cancel) { _, _ -> answer { result.cancel() } }
        }
    }

    /**
     * "Leave site?" — confirm lets the navigation proceed, cancel keeps the user on the page. The page's own
     * message is ignored, as in every current browser (it was abused for scare text). A blocked page may
     * no longer hold the user hostage, so blocked means "leave".
     */
    fun beforeUnload(result: JsResult): Boolean =
        show(result, onBlocked = { result.confirm() }) { answer ->
            setTitle(R.string.napplet_js_leave_title)
            setMessage(R.string.napplet_js_leave_message)
            setPositiveButton(R.string.napplet_js_leave) { _, _ -> answer { result.confirm() } }
            setNegativeButton(android.R.string.cancel) { _, _ -> answer { result.cancel() } }
        }

    /**
     * Builds and shows one dialog. [build] wires the buttons through `answer`, which records that the
     * result was settled so the dismiss listener doesn't cancel it a second time. Always returns true: the
     * result is ours from here on.
     */
    private fun show(
        result: JsResult,
        onBlocked: () -> Unit,
        build: AlertDialog.Builder.(answer: (() -> Unit) -> Unit) -> Unit,
    ): Boolean {
        if (blocked) {
            onBlocked()
            return true
        }
        // One at a time, and never onto a window that is going away.
        if (showing != null || activity.isFinishing || activity.isDestroyed) {
            result.cancel()
            return true
        }
        var settled = false
        val answer: (() -> Unit) -> Unit = { action ->
            if (!settled) {
                settled = true
                action()
            }
        }
        shownOnThisPage++
        val builder = AlertDialog.Builder(activity).apply { build(answer) }
        if (shownOnThisPage > 1) {
            builder.setNeutralButton(R.string.napplet_js_dialog_block) { _, _ ->
                blocked = true
                answer { onBlocked() }
            }
        }
        val dialog =
            builder
                .setOnDismissListener {
                    showing = null
                    // Back, a tap outside, or teardown: no button answered, so the page gets a cancel.
                    answer { result.cancel() }
                }.create()
        showing = dialog
        dialog.show()
        return true
    }

    private fun titleFor(url: String?): String {
        val uri = url?.let { runCatching { it.toUri() }.getOrNull() }
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host?.takeIf { it.isNotBlank() }
        return if (host != null && (scheme == "http" || scheme == "https")) {
            activity.getString(R.string.napplet_js_dialog_title, host)
        } else {
            activity.getString(R.string.napplet_js_dialog_title_generic)
        }
    }
}
