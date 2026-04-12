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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip75ZapGoals.GoalEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform-independent state for creating a new Zap Goal (NIP-75).
 *
 * Holds draft fields and builds the event template.
 * Platform ViewModels wrap this and provide signing/broadcasting.
 */
@Stable
class NewGoalState {
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary.asStateFlow()

    private val _imageUrl = MutableStateFlow("")
    val imageUrl: StateFlow<String> = _imageUrl.asStateFlow()

    private val _websiteUrl = MutableStateFlow("")
    val websiteUrl: StateFlow<String> = _websiteUrl.asStateFlow()

    private val _wantsDeadline = MutableStateFlow(false)
    val wantsDeadline: StateFlow<Boolean> = _wantsDeadline.asStateFlow()

    private val _deadlineTimestamp = MutableStateFlow(TimeUtils.now() + TimeUtils.ONE_WEEK)
    val deadlineTimestamp: StateFlow<Long> = _deadlineTimestamp.asStateFlow()

    fun updateDescription(value: String) {
        _description.value = value
    }

    fun updateAmount(value: String) {
        _amount.value = value
    }

    fun updateSummary(value: String) {
        _summary.value = value
    }

    fun updateImageUrl(value: String) {
        _imageUrl.value = value
    }

    fun updateWebsiteUrl(value: String) {
        _websiteUrl.value = value
    }

    fun updateWantsDeadline(value: Boolean) {
        _wantsDeadline.value = value
    }

    fun updateDeadlineTimestamp(value: Long) {
        _deadlineTimestamp.value = value
    }

    fun canPost(): Boolean {
        val desc = _description.value
        val amt = _amount.value
        return desc.isNotBlank() &&
            amt.isNotBlank() &&
            amt.toLongOrNull() != null &&
            (amt.toLongOrNull() ?: 0) > 0
    }

    /**
     * Build the GoalEvent template from current state.
     *
     * @param relays the outbox relay list to include in the goal
     * @return the event template, or null if validation fails
     */
    fun createTemplate(relays: List<NormalizedRelayUrl>): EventTemplate<out Event>? {
        val amountSats = _amount.value.toLongOrNull() ?: return null
        val amountMillisats = amountSats * 1000L

        val closedAt = if (_wantsDeadline.value) _deadlineTimestamp.value else null
        val img = _imageUrl.value.ifBlank { null }
        val sum = _summary.value.ifBlank { null }
        val web = _websiteUrl.value.ifBlank { null }

        return GoalEvent.build(
            description = _description.value,
            amount = amountMillisats,
            relays = relays,
            closedAt = closedAt,
            image = img,
            summary = sum,
            websiteUrl = web,
        )
    }

    fun cancel() {
        _description.value = ""
        _amount.value = ""
        _summary.value = ""
        _imageUrl.value = ""
        _websiteUrl.value = ""
        _wantsDeadline.value = false
        _deadlineTimestamp.value = TimeUtils.now() + TimeUtils.ONE_WEEK
    }
}
