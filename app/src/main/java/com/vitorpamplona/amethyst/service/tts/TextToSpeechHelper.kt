/*
 * Created by Ayaan on 02/02/23, 10:30 pm
 * Copyright (c) 2023 . All rights reserved.
 * Last modified 02/02/23, 10:19 pm
 */

package com.vitorpamplona.amethyst.service.tts

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference
import java.util.Locale

class TextToSpeechHelper private constructor(private val context: WeakReference<Context>) :
    LifecycleEventObserver {
    private val appContext
        get() = context.get()!!.applicationContext

    private var message: String? = null

    private var ttsEngine: TextToSpeechEngine? = null

    private var onStart: (() -> Unit)? = null

    private var onDoneListener: (() -> Unit)? = null

    private var onErrorListener: ((String) -> Unit)? = null

    private var onHighlightListener: ((Pair<Int, Int>) -> Unit)? = null

    private var customActionForDestroy: (() -> Unit)? = null

    init {
        Log.d("Init", "Init TTS")
        initTTS()
    }

    fun registerLifecycle(owner: LifecycleOwner): TextToSpeechHelper {
        owner.lifecycle.addObserver(this)
        return this
    }

    private fun initTTS() = context.get()?.run {
        ttsEngine = TextToSpeechEngine.getInstance()
            .setOnCompletionListener { onDoneListener?.invoke() }
            .setOnErrorListener { onErrorListener?.invoke(it) }
            .setOnStartListener { onStart?.invoke() }
    }

    fun speak(message: String): TextToSpeechHelper {
        if (ttsEngine == null) {
            initTTS()
        }
        this.message = message

        ttsEngine?.initTTS(
            appContext,
            message
        )
        return this
    }

    /**
     * This method will highlight the text in the textView
     *
     * @exception Exception("Message can't be null for highlighting !! Call speak() first")
     */
    fun highlight(): TextToSpeechHelper {
        if (message == null) throw Exception("Message can't be null for highlighting !! Call speak() first")
        ttsEngine?.setHighlightedMessage(message!!)
        ttsEngine?.setOnHighlightListener { i, i2 ->
            onHighlightListener?.invoke(Pair(i, i2))
        }
        return this
    }

    fun removeHighlight(): TextToSpeechHelper {
        message = null
        onHighlightListener = null
        return this
    }

    fun destroy(
        action: (() -> Unit) = {}
    ) {
        ttsEngine?.destroy()
        ttsEngine = null
        action.invoke()
        INSTANCE = null
    }

    fun onStart(onStartListener: () -> Unit): TextToSpeechHelper {
        this.onStart = onStartListener
        return this
    }

    fun onDone(onCompleteListener: () -> Unit): TextToSpeechHelper {
        this.onDoneListener = onCompleteListener
        return this
    }

    fun onError(onErrorListener: (String) -> Unit): TextToSpeechHelper {
        this.onErrorListener = onErrorListener
        return this
    }

    fun onHighlight(onHighlightListener: (Pair<Int, Int>) -> Unit): TextToSpeechHelper {
        this.onHighlightListener = onHighlightListener
        return this
    }

    fun setCustomActionForDestroy(action: () -> Unit): TextToSpeechHelper {
        customActionForDestroy = action
        return this
    }

    fun setLanguage(locale: Locale): TextToSpeechHelper {
        ttsEngine?.setLanguage(locale)
        return this
    }

    fun setPitchAndSpeed(
        pitch: Float = DEF_SPEECH_AND_PITCH,
        speed: Float = DEF_SPEECH_AND_PITCH
    ): TextToSpeechHelper {
        ttsEngine?.setPitchAndSpeed(pitch, speed)
        return this
    }

    fun resetPitchAndSpeed(): TextToSpeechHelper {
        ttsEngine?.resetPitchAndSpeed()
        return this
    }

    companion object {
        private var INSTANCE: TextToSpeechHelper? = null
        fun getInstance(context: Context): TextToSpeechHelper {
            synchronized(TextToSpeechHelper::class.java) {
                if (INSTANCE == null) {
                    INSTANCE = TextToSpeechHelper(WeakReference(context))
                }
                return INSTANCE!!
            }
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY ||
            event == Lifecycle.Event.ON_STOP ||
            event == Lifecycle.Event.ON_PAUSE
        ) {
            destroy {
                customActionForDestroy?.invoke()
            }
        }
    }
}
