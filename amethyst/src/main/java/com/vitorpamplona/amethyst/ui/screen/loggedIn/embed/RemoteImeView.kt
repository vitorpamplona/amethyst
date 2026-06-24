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

    // Deferred blur: web frameworks (React controlled inputs) often fire blur+focus in rapid succession
    // when they re-render an input after the first character. Delaying the actual hide/clearFocus lets a
    // following ime.focus cancel the blur without the 1-2 second IME restart penalty.
    private val pendingBlur =
        Runnable {
            clearFocus()
            imm.hideSoftInputFromWindow(windowToken, 0)
        }

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
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {}

                override fun afterTextChanged(s: Editable?) = schedule()
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

    /** A page field focused: configure the keyboard, seed the buffer, and raise the IME. */
    fun onPageFocus(focus: ImeEvent.Focus) {
        // Cancel any pending blur so a rapid blur→focus cycle (React re-rendering a controlled input
        // after the first character) doesn't trigger an unnecessary restartInput.
        removeCallbacks(pendingBlur)
        val alreadyFocused = hasFocus()
        configureFor(focus)
        applyRemote(focus.text, focus.selStart, focus.selEnd)
        requestFocus()
        // Only restart when we weren't already the IME target. Restarting on every re-focus (e.g. the
        // blur→focus cycle from a framework re-render) forces the IME to tear down and rebuild its
        // InputConnection, causing a 1-2 second stall on the next keypress.
        if (!alreadyFocused) imm.restartInput(this)
        // Post the show so it runs after focus/attachment has settled (showSoftInput can no-op otherwise).
        post {
            if (hasFocus()) imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** The page changed the field itself (its JS, autofill): mirror it without echoing back. */
    fun onPageState(state: ImeEvent.State) {
        if (text?.toString() == state.text && selectionStart == state.selStart && selectionEnd == state.selEnd) return
        applyRemote(state.text, state.selStart, state.selEnd)
    }

    /** The page field blurred: drop the keyboard after a short grace period. */
    fun onPageBlur() {
        // Short delay: if a focus event arrives within this window (blur→focus from a re-render cycle),
        // the pendingBlur is cancelled and we avoid the unnecessary restartInput + keyboard flicker.
        removeCallbacks(pendingBlur)
        postDelayed(pendingBlur, 150)
    }

    private fun applyRemote(
        newText: String,
        selStart: Int,
        selEnd: Int,
    ) {
        applyingRemote = true
        // Skip setText when content is unchanged — calling it unconditionally moves the cursor to the
        // end internally and notifies the IME of a spurious selection change, which makes the keyboard
        // cursor jump to the end of the word when the page reports a selection-only update (e.g. the
        // word selection produced by a long-press).
        if (text?.toString() != newText) setText(newText)
        val len = text?.length ?: 0
        setSelection(selStart.coerceIn(0, len), selEnd.coerceIn(0, len))
        applyingRemote = false
        lastSent = stateJson().toString()
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
}
