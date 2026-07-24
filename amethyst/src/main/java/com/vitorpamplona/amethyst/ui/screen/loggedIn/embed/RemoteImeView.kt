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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import org.json.JSONObject

/**
 * The main-app-window home for the soft keyboard when a field is focused inside an embedded WebView. The
 * embedded surface is a cross-process [android.view.SurfaceControlViewHost] window that can't be an IME
 * target, so this invisible [EditText] takes the keyboard in the main window instead and relays editing
 * to the page.
 *
 * Modeled on Flutter's `TextInputPlugin`/`InputConnectionAdaptor`: a real local [Editable] is the source
 * of truth (the platform handles composing regions, suggestions, selection, spell-check, and answers
 * `getTextBeforeCursor`/`getExtractedText` for free), edits are **coalesced across batch boundaries**,
 * and the whole **editing state** — text, selection, composing region — is shipped to the page (not
 * individual ops). Going beyond Flutter (whose consumer is a Dart widget), the shim then synthesizes the
 * matching DOM `input`/composition events so web frameworks react.
 *
 * It covers the soft keyboard, hardware keyboards, autofill, paste, and context-menu edits uniformly,
 * because every one of them mutates the same [Editable] and we flush the resulting state.
 */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
class RemoteImeView(
    context: Context,
) : EditText(context) {
    private var bridge: EmbeddedImeBridge? = null

    // True while we apply a page-side update, so the resulting local edits aren't echoed back (loop).
    private var applyingRemote = false

    // IME batch-edit depth (Flutter's batchEditNestDepth): coalesce a batch into one state flush.
    private var batchDepth = 0

    // The last state we sent, so we never ship a no-op (avoids feedback churn with the page).
    private var lastSent: String? = null

    private val imm get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private val flush = Runnable { flushState() }

    init {
        // Invisible but focusable: the IME needs a laid-out, visible target, but the user must never see
        // this field or its cursor/selection handles — only the embedded page.
        isFocusableInTouchMode = true
        alpha = 0f
        background = null
        setTextColor(0x00000000)
        setCursorVisible(false)
        setPadding(0, 0, 0, 0)
        addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                    // No-op: TextWatcher requires this override, but only afterTextChanged drives our flush.
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    // No-op: TextWatcher requires this override, but only afterTextChanged drives our flush.
                }

                override fun afterTextChanged(s: Editable?) {
                    if (!applyingRemote) onEdited?.invoke()
                    schedule()
                }
            },
        )
        // The IME's "Go/Search/Send/Done" — the page submits/handles it (single-line has no newline).
        setOnEditorActionListener { _, _, _ ->
            bridge?.sendImeOp(JSONObject().put("type", "ime.action").toString())
            true
        }
    }

    /** Binds the controller of whatever embedded tab is active; null unbinds (no relay target). */
    fun bind(bridge: EmbeddedImeBridge?) {
        this.bridge = bridge
    }

    /**
     * Notified when the mirrored selection becomes (true) or stops being (false) a non-empty range. The
     * embedded WebView can't present Chrome's copy/paste toolbar in its cross-process surface, so the host
     * shows its own over the page and routes the actions back through [copy]/[cut]/[paste]/[selectAll],
     * which run on this EditText's Editable (and so relay to the page through the normal edit path).
     */
    var onRangeSelectionChanged: ((Boolean) -> Unit)? = null
    private var hadRange = false

    // Off-window Chrome abandons a selection by momentarily collapsing the caret to an endpoint, which we (or
    // the page shim) re-assert right back — so the mirrored selection flickers range→caret→range within a few
    // ms during a long-press/double-tap. The host-drawn handles/toolbar are gated on [hadRange], so reporting
    // every flip blinks them off and back on, in lock-step with each collapse cycle. Native never shows that
    // churn. So we DEFER the "range lost" signal by [RANGE_LOSS_DEBOUNCE_MS]: a re-assert that restores the
    // range first cancels the pending hide, and only a selection that truly STAYS collapsed hides the overlays.
    // Gaining a range is always reported immediately.
    private val reportRangeLost =
        Runnable {
            if (selectionStart == selectionEnd && hadRange) {
                hadRange = false
                onRangeSelectionChanged?.invoke(false)
            }
        }

    /** Fired when the user edits text via the keyboard (not on programmatic page-state applies). The host
     *  hides the insertion handle while typing, the way Android does. */
    var onEdited: (() -> Unit)? = null

    fun copySelection(): Boolean = onTextContextMenuItem(android.R.id.copy)

    fun cutSelection(): Boolean = onTextContextMenuItem(android.R.id.cut)

    fun pasteClipboard(): Boolean = onTextContextMenuItem(android.R.id.paste)

    fun selectAllText(): Boolean = onTextContextMenuItem(android.R.id.selectAll)

    /** A page field focused: configure the keyboard, seed the buffer, and raise the IME. */
    @Suppress("DEPRECATION") // InputMethodManager.SHOW_IMPLICIT is deprecated; no equivalent flag on the newer API.
    fun onPageFocus(focus: ImeEvent.Focus) {
        configureFor(focus)
        // Focus the EditText BEFORE seeding text/selection. An EditText jumps its caret to the end when it
        // gains focus; if we seed first, that end-position then overrides the seed and gets shipped to the
        // page — so a tap mid-text lands the caret at the end of the field. Seeding AFTER focus makes the
        // tap position the final state (the focus-induced end-position only schedules a flush that then
        // coalesces to this seed, a no-op).
        requestFocus()
        imm.restartInput(this)
        applyRemote(focus.text, focus.selStart, focus.selEnd)
        // Post the show so it runs after focus/attachment has settled (showSoftInput can no-op otherwise).
        post {
            if (hasFocus()) imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // When the current selection first became a range, and how many of its collapse-abandonments we've
    // re-asserted. Chrome abandons a selection *immediately* (~60ms); a deliberate user tap-to-collapse comes
    // later — so we only re-assert within a short window of the range forming, bounded for safety.
    private var rangeBecameAt = 0L
    private var reassertCount = 0

    /** The page changed the field itself (its JS, autofill): mirror it without echoing back. */
    fun onPageState(state: ImeEvent.State) {
        val curText = text?.toString() ?: ""
        // Chrome can't present selection handles in the embedded surface, so it abandons a selection by
        // collapsing the caret to one of the range's endpoints, right after it forms. We hold the
        // authoritative selection here, so a collapse to an endpoint of our current range (same text) within
        // the window is that abandonment, not a user action: re-assert our range instead of accepting it.
        val collapsedToEndpoint =
            selectionStart != selectionEnd &&
                state.selStart == state.selEnd &&
                state.text == curText &&
                (state.selStart == selectionStart || state.selStart == selectionEnd)
        if (collapsedToEndpoint && reassertCount < MAX_REASSERT &&
            SystemClock.uptimeMillis() - rangeBecameAt < REASSERT_WINDOW_MS
        ) {
            reassertCount++
            // Keep the window alive across the fight: Chrome re-abandons the selection every ~600ms while the
            // gesture is held, so each re-assert restarts the clock — bounded by reassertCount so a page that
            // truly keeps the caret collapsed still wins.
            rangeBecameAt = SystemClock.uptimeMillis()
            lastSent = null // force a non-no-op flush so the range re-ships
            flushState()
            return
        }
        reassertCount = 0
        if (curText == state.text && selectionStart == state.selStart && selectionEnd == state.selEnd) return
        applyRemote(state.text, state.selStart, state.selEnd)
    }

    /** The page field blurred: drop the keyboard. */
    fun onPageBlur() {
        removeCallbacks(reportRangeLost)
        if (hadRange) {
            hadRange = false
            onRangeSelectionChanged?.invoke(false)
        }
        clearFocus()
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun applyRemote(
        newText: String,
        selStart: Int,
        selEnd: Int,
    ) {
        applyingRemote = true
        // Only replace the buffer when the text actually changed. A page that rewrites its field's
        // selection on a timer (without changing the text) would otherwise force a full setText on every
        // update — clearing the composing region and restarting the IME — which needlessly churns the
        // keyboard and contends with the user's own typing. Reposition the cursor without touching the buffer.
        if (text?.toString() != newText) setText(newText)
        val len = text?.length ?: 0
        setSelection(selStart.coerceIn(0, len), selEnd.coerceIn(0, len))
        applyingRemote = false
        lastSent = stateJson().toString()
        // Start the abandonment window when a fresh range appears, so onPageState can tell Chrome's instant
        // collapse from a later user tap-to-collapse.
        if (selectionStart != selectionEnd) rangeBecameAt = SystemClock.uptimeMillis()
    }

    private fun schedule() {
        if (applyingRemote || batchDepth > 0) return
        removeCallbacks(flush)
        post(flush)
    }

    override fun onSelectionChanged(
        selStart: Int,
        selEnd: Int,
    ) {
        super.onSelectionChanged(selStart, selEnd)
        val isRange = selStart != selEnd
        if (isRange) {
            // A range is back (or still here): cancel any pending hide and show immediately.
            removeCallbacks(reportRangeLost)
            if (!hadRange) {
                hadRange = true
                onRangeSelectionChanged?.invoke(true)
            }
        } else if (hadRange) {
            // Collapsed — but this may be Chrome's transient abandonment we're about to re-assert. Defer the
            // hide; if the range returns within the window, the show branch above cancels this.
            removeCallbacks(reportRangeLost)
            postDelayed(reportRangeLost, RANGE_LOSS_DEBOUNCE_MS)
        }
        schedule()
    }

    private fun stateJson(): JSONObject {
        val editable = text
        val composingStart = if (editable != null) BaseInputConnection.getComposingSpanStart(editable) else -1
        val composingEnd = if (editable != null) BaseInputConnection.getComposingSpanEnd(editable) else -1
        return JSONObject()
            .put("type", "ime.set")
            .put("text", editable?.toString() ?: "")
            .put("selStart", selectionStart)
            .put("selEnd", selectionEnd)
            .put("composingStart", composingStart)
            .put("composingEnd", composingEnd)
    }

    private fun flushState() {
        if (applyingRemote) return
        val json = stateJson()
        val str = json.toString()
        if (str == lastSent) return
        lastSent = str
        bridge?.sendImeOp(str)
    }

    private fun configureFor(focus: ImeEvent.Focus) {
        inputType =
            when (focus.inputType) {
                "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                "email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                "url" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                "number" -> InputType.TYPE_CLASS_NUMBER
                "tel" -> InputType.TYPE_CLASS_PHONE
                else ->
                    InputType.TYPE_CLASS_TEXT or
                        (if (focus.multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_VARIATION_NORMAL)
            }
        imeOptions = editorActionFor(focus) or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
    }

    private fun editorActionFor(focus: ImeEvent.Focus): Int =
        when (focus.enterKeyHint) {
            "go" -> EditorInfo.IME_ACTION_GO
            "search" -> EditorInfo.IME_ACTION_SEARCH
            "send" -> EditorInfo.IME_ACTION_SEND
            "next" -> EditorInfo.IME_ACTION_NEXT
            "done" -> EditorInfo.IME_ACTION_DONE
            else -> if (focus.multiline) EditorInfo.IME_ACTION_NONE else EditorInfo.IME_ACTION_GO
        }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return BatchAwareConnection(base)
    }

    /**
     * Tracks batch-edit nesting so a multi-op keystroke (e.g. delete-then-insert during composing) is
     * shipped to the page as ONE coalesced state, exactly like Flutter's `InputConnectionAdaptor`.
     */
    private inner class BatchAwareConnection(
        target: InputConnection,
    ) : InputConnectionWrapper(target, true) {
        override fun beginBatchEdit(): Boolean {
            batchDepth++
            return super.beginBatchEdit()
        }

        override fun endBatchEdit(): Boolean {
            val result = super.endBatchEdit()
            if (batchDepth > 0) batchDepth--
            // Flush synchronously at the outermost batch close (don't post): a composing keystroke is a
            // delete+insert inside one batch, and shipping the coalesced state now — within the same frame
            // — preserves the composing region. A posted flush would race the next batch and could observe
            // an already-committed (composing-cleared) Editable, breaking the composition lifecycle.
            if (batchDepth == 0) {
                removeCallbacks(flush)
                flushState()
            }
            return result
        }
    }

    private companion object {
        // Chrome re-abandons a held selection repeatedly; cover a long-press hold (each re-assert restarts the
        // window) while still giving up if the page truly keeps re-collapsing forever.
        private const val MAX_REASSERT = 12

        // Wider than Chrome's ~600ms re-collapse interval so consecutive abandonments stay inside the window,
        // yet short enough that a deliberate user tap-to-collapse (well after the gesture) is accepted.
        private const val REASSERT_WINDOW_MS = 800L

        // How long a collapse must persist before we hide the host-drawn selection overlays. The shim/host
        // re-assert restores an abandonment collapse within a frame or two (one IPC hop), so this only needs to
        // outlast that round-trip — comfortably short enough that a genuine tap-to-collapse still feels instant.
        private const val RANGE_LOSS_DEBOUNCE_MS = 250L
    }
}
