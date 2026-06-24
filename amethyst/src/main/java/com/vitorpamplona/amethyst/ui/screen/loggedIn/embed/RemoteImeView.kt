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
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import org.json.JSONObject

/**
 * The main-app-window home for the soft keyboard when a field is focused inside an embedded WebView. The
 * embedded surface is a cross-process [android.view.SurfaceControlViewHost] window that can't be an IME
 * target, so this invisible [EditText] takes the keyboard in the main window instead and relays every
 * edit to the page.
 *
 * It keeps a real local [android.text.Editable], so the platform handles composing regions, suggestions,
 * selection, and `getTextBeforeCursor` correctly. A thin [InputConnection] wrapper *also* forwards each
 * op (commit / compose / delete / key / editor-action) to the page as a JSON envelope, where the shim
 * applies it with real input/composition events. Page-side changes come back as [ImeEvent.State] and are
 * mirrored into the local buffer so the keyboard stays in sync.
 */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
class RemoteImeView(
    context: Context,
) : EditText(context) {
    private var bridge: EmbeddedImeBridge? = null

    // True while we mutate our own text from a page-side update, so the resulting edits aren't echoed
    // back to the page (which would loop).
    private var applyingRemote = false

    private val imm get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    init {
        // Invisible but focusable: the IME needs a laid-out, visible target, but the user must never see
        // this field or its cursor/selection handles — only the embedded page.
        isFocusableInTouchMode = true
        alpha = 0f
        background = null
        setTextColor(0x00000000)
        setCursorVisible(false)
        setPadding(0, 0, 0, 0)
    }

    /** Binds the controller of whatever embedded tab is active; null unbinds (no relay target). */
    fun bind(bridge: EmbeddedImeBridge?) {
        this.bridge = bridge
    }

    /** A page field focused: configure the keyboard, seed the buffer, and raise the IME. */
    fun onPageFocus(focus: ImeEvent.Focus) {
        configureFor(focus)
        applyingRemote = true
        setText(focus.text)
        setSelection(clamp(focus.selStart), clamp(focus.selEnd))
        applyingRemote = false
        requestFocus()
        imm.restartInput(this)
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    /** The page changed the field itself (its JS, autofill): mirror it without echoing back. */
    fun onPageState(state: ImeEvent.State) {
        if (text?.toString() == state.text && selectionStart == state.selStart && selectionEnd == state.selEnd) return
        applyingRemote = true
        setText(state.text)
        setSelection(clamp(state.selStart), clamp(state.selEnd))
        applyingRemote = false
    }

    /** The page field blurred: drop the keyboard. */
    fun onPageBlur() {
        clearFocus()
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun clamp(i: Int): Int = i.coerceIn(0, text?.length ?: 0)

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
        return ForwardingConnection(base)
    }

    private fun op(envelope: JSONObject) {
        if (!applyingRemote) bridge?.sendImeOp(envelope.toString())
    }

    /** Wraps the platform connection: edits the local buffer (via super) AND relays each op to the page. */
    private inner class ForwardingConnection(
        target: InputConnection,
    ) : InputConnectionWrapper(target, true) {
        override fun commitText(
            text: CharSequence,
            newCursorPosition: Int,
        ): Boolean {
            op(JSONObject().put("type", "ime.commit").put("text", text.toString()))
            return super.commitText(text, newCursorPosition)
        }

        override fun setComposingText(
            text: CharSequence,
            newCursorPosition: Int,
        ): Boolean {
            op(JSONObject().put("type", "ime.composing").put("text", text.toString()))
            return super.setComposingText(text, newCursorPosition)
        }

        override fun finishComposingText(): Boolean {
            op(JSONObject().put("type", "ime.finishComposing"))
            return super.finishComposingText()
        }

        override fun deleteSurroundingText(
            beforeLength: Int,
            afterLength: Int,
        ): Boolean {
            op(JSONObject().put("type", "ime.delete").put("before", beforeLength).put("after", afterLength))
            return super.deleteSurroundingText(beforeLength, afterLength)
        }

        override fun setSelection(
            start: Int,
            end: Int,
        ): Boolean {
            op(JSONObject().put("type", "ime.setSelection").put("start", start).put("end", end))
            return super.setSelection(start, end)
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) {
                op(JSONObject().put("type", "ime.key").put("keyCode", event.keyCode).put("key", keyLabel(event)))
            }
            return super.sendKeyEvent(event)
        }

        override fun performEditorAction(editorAction: Int): Boolean {
            op(JSONObject().put("type", "ime.action").put("action", editorAction))
            // Don't call super: the local buffer has no "submit"; the page handles the action.
            return true
        }

        private fun keyLabel(event: KeyEvent): String =
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER -> "Enter"
                KeyEvent.KEYCODE_DEL -> "Backspace"
                KeyEvent.KEYCODE_TAB -> "Tab"
                else ->
                    event.unicodeChar
                        .takeIf { it != 0 }
                        ?.toChar()
                        ?.toString() ?: ""
            }
    }
}
