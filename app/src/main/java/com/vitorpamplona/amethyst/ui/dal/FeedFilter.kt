package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class FeedFilter<T>() {
  fun loadTop(): List<T> {
    return feed().take(1000)
  }

  abstract fun feed(): List<T>
}