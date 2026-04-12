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
package com.vitorpamplona.amethyst.commons.viewmodels.posting

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Common post options shared across posting ViewModels:
 * content warning, geohash, expiration, zap raiser, and zap splits.
 *
 * Platform ViewModels can sync their Compose state to this object,
 * then use [applyTo] to add the relevant tags to an event builder.
 */
@Stable
class PostOptions {
    // NSFW / Sensitive content
    private val _wantsContentWarning = MutableStateFlow(false)
    val wantsContentWarning: StateFlow<Boolean> = _wantsContentWarning.asStateFlow()

    private val _contentWarningReason = MutableStateFlow("")
    val contentWarningReason: StateFlow<String> = _contentWarningReason.asStateFlow()

    // GeoHash
    private val _wantsGeoHash = MutableStateFlow(false)
    val wantsGeoHash: StateFlow<Boolean> = _wantsGeoHash.asStateFlow()

    private val _geoHash = MutableStateFlow<String?>(null)
    val geoHash: StateFlow<String?> = _geoHash.asStateFlow()

    // Expiration (NIP-40)
    private val _wantsExpiration = MutableStateFlow(false)
    val wantsExpiration: StateFlow<Boolean> = _wantsExpiration.asStateFlow()

    private val _expirationDate = MutableStateFlow(TimeUtils.oneDayAhead())
    val expirationDate: StateFlow<Long> = _expirationDate.asStateFlow()

    // Zap Raiser
    private val _wantsZapRaiser = MutableStateFlow(false)
    val wantsZapRaiser: StateFlow<Boolean> = _wantsZapRaiser.asStateFlow()

    private val _zapRaiserAmount = MutableStateFlow<Long?>(null)
    val zapRaiserAmount: StateFlow<Long?> = _zapRaiserAmount.asStateFlow()

    // Zap Splits (kept as a list of setups; platform fills from its SplitBuilder)
    private val _zapSplits = MutableStateFlow<List<ZapSplitSetup>?>(null)
    val zapSplits: StateFlow<List<ZapSplitSetup>?> = _zapSplits.asStateFlow()

    // -- Mutators --

    fun updateContentWarning(
        wants: Boolean,
        reason: String = "",
    ) {
        _wantsContentWarning.value = wants
        _contentWarningReason.value = reason
    }

    fun updateGeoHash(
        wants: Boolean,
        hash: String? = null,
    ) {
        _wantsGeoHash.value = wants
        _geoHash.value = hash
    }

    fun updateExpiration(
        wants: Boolean,
        date: Long = TimeUtils.oneDayAhead(),
    ) {
        _wantsExpiration.value = wants
        _expirationDate.value = date
    }

    fun updateZapRaiser(
        wants: Boolean,
        amount: Long? = null,
    ) {
        _wantsZapRaiser.value = wants
        _zapRaiserAmount.value = amount
    }

    fun updateZapSplits(splits: List<ZapSplitSetup>?) {
        _zapSplits.value = splits
    }

    /**
     * Resolved values for template building.
     */
    fun resolvedContentWarning(): String? = if (_wantsContentWarning.value) _contentWarningReason.value else null

    fun resolvedGeoHash(): String? = if (_wantsGeoHash.value) _geoHash.value else null

    fun resolvedExpiration(): Long? = if (_wantsExpiration.value) _expirationDate.value else null

    fun resolvedZapRaiser(): Long? = if (_wantsZapRaiser.value) _zapRaiserAmount.value else null

    fun resolvedZapSplits(): List<ZapSplitSetup>? = _zapSplits.value

    /**
     * Apply resolved options as tags to an event builder.
     * Call this inside the TagArrayBuilder lambda of an event build method.
     */
    fun <T : Event> applyTo(builder: TagArrayBuilder<T>) {
        resolvedGeoHash()?.let { builder.geohash(it) }
        resolvedZapRaiser()?.let { builder.zapraiser(it) }
        resolvedZapSplits()?.let { builder.zapSplits(it) }
        resolvedContentWarning()?.let { builder.contentWarning(it) }
        resolvedExpiration()?.let { builder.expiration(it) }
    }

    fun reset() {
        _wantsContentWarning.value = false
        _contentWarningReason.value = ""
        _wantsGeoHash.value = false
        _geoHash.value = null
        _wantsExpiration.value = false
        _expirationDate.value = TimeUtils.oneDayAhead()
        _wantsZapRaiser.value = false
        _zapRaiserAmount.value = null
        _zapSplits.value = null
    }
}
