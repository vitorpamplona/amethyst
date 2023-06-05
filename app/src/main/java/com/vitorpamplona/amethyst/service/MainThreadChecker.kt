package com.vitorpamplona.amethyst.service

import android.os.Looper
import com.vitorpamplona.amethyst.BuildConfig

fun checkNotInMainThread() {
    if (BuildConfig.DEBUG && isMainThread()) throw OnMainThreadException("It should not be in the MainThread")
}

fun isMainThread() = Looper.myLooper() == Looper.getMainLooper()

class OnMainThreadException(str: String) : RuntimeException(str)
