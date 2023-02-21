package com.vitorpamplona.amethyst.ui.dal

import android.util.Log
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class FeedFilter<T>() {
  @OptIn(ExperimentalTime::class)
  fun loadTop(): List<T> {
    val (feed, elapsed) = measureTimedValue {
      feed().take(1000)
    }

    Log.d("Time","${this.javaClass.simpleName} Feed in ${elapsed}")
    return feed
  }

  abstract fun feed(): List<T>
}