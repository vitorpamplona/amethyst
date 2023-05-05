package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.model.RecommendationRequestEvent
import com.vitorpamplona.amethyst.service.model.RecommendationResponseEvent
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import kotlinx.coroutines.Dispatchers

class Recommendation(val pubKeyHex: HexKey) {
    // These fields are only available after the Text Note event is received.
    // They are immutable after that.
    var response: RecommendationResponseEvent? = null
    var author: User? = null

    var requests = mapOf<HexKey, ((RecommendationResponseEvent) -> Unit)>()

    fun updateRequest(event: RecommendationRequestEvent, author: User, onResponse: (RecommendationResponseEvent) -> Unit) {
        this.author = author

        requests = requests + Pair(event.id, onResponse)
    }

    fun updateResponse(event: RecommendationResponseEvent, author: User) {
        this.response = event
        this.author = author

        val requestId = event.requestEvent()
        if (requestId != null) {
            requests.get(requestId)?.invoke(event)
            requests = requests.minus(requestId)
        }

        live.invalidateData()
    }

    // Observers line up here.
    val live: RecommendationLiveData = RecommendationLiveData(this)
}

class RecommendationLiveData(val recomm: Recommendation) : LiveData<RecommendationState>(RecommendationState(recomm)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.Main) {
        if (hasActiveObservers()) {
            refresh()
        }
    }

    fun invalidateData() {
        bundler.invalidate()
    }

    private fun refresh() {
        postValue(RecommendationState(recomm))
    }
}

class RecommendationState(val recomm: Recommendation)
