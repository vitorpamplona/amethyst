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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.create

import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.endpoint
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.image
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.summary
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Backing ViewModel for [CreateAudioRoomSheet]. Holds form state, runs
 * [publishAndBuildLaunchInfo] which signs and broadcasts a NIP-53 kind
 * 30312 [MeetingSpaceEvent] tagging the user as `host`, then returns
 * the launch parameters for [com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.room.AudioRoomActivity].
 *
 * The defaults point at `nostrnests.com`'s public moq-rs deployment so a
 * blank form produces a working room. The user can edit them to point at
 * their own moq-rs / moq-auth pair.
 */
class CreateAudioRoomViewModel : ViewModel() {
    /** Lazily bound on first composition; the sheet calls [bindAccountIfMissing]. */
    @Volatile private var account: AccountViewModel? = null

    private val _state = MutableStateFlow(FormState.defaults())
    val state: StateFlow<FormState> = _state.asStateFlow()

    fun bindAccountIfMissing(accountViewModel: AccountViewModel) {
        if (account != null) return
        account = accountViewModel
        // Seed the URL fields from the user's saved kind-10062 list so
        // first-time use flows naturally from the Settings screen. If
        // the list is empty (or the user hasn't published one yet), keep
        // the nostrnests.com defaults already in [FormState.defaults].
        val savedFirst =
            accountViewModel.account.nestsServers.flow.value
                .firstOrNull()
                ?.takeIf { it.startsWith("http") }
        if (savedFirst != null) {
            _state.update { it.copy(serviceUrl = savedFirst, endpointUrl = savedFirst) }
        }
    }

    fun onRoomNameChange(value: String) = _state.update { it.copy(roomName = value, error = null) }

    fun onSummaryChange(value: String) = _state.update { it.copy(summary = value, error = null) }

    fun onServiceUrlChange(value: String) = _state.update { it.copy(serviceUrl = value.trim(), error = null) }

    fun onEndpointUrlChange(value: String) = _state.update { it.copy(endpointUrl = value.trim(), error = null) }

    fun onImageUrlChange(value: String) = _state.update { it.copy(imageUrl = value.trim(), error = null) }

    /**
     * Build the kind-30312 event, sign + broadcast it, and return the
     * launch info the sheet needs to start [AudioRoomActivity]. Returns
     * null on validation or network failure (with [FormState.error]
     * set so the UI can render it).
     */
    suspend fun publishAndBuildLaunchInfo(): RoomLaunchInfo? {
        val current = _state.value
        val account =
            account ?: run {
                _state.update { it.copy(error = "Account not bound; please retry from the spaces screen.") }
                return null
            }
        if (current.roomName.isBlank()) {
            _state.update { it.copy(error = "Room name is required.") }
            return null
        }
        val service = current.serviceUrl.trim().trimEnd('/')
        val endpoint = current.endpointUrl.trim().trimEnd('/')
        if (service.isBlank() || !service.startsWith("http")) {
            _state.update { it.copy(error = "MoQ service URL must start with http(s)://") }
            return null
        }
        if (endpoint.isBlank() || !endpoint.startsWith("http")) {
            _state.update { it.copy(error = "MoQ endpoint URL must start with http(s)://") }
            return null
        }

        _state.update { it.copy(isPublishing = true, error = null) }
        val accountModel = account.account
        val hostPubkey = accountModel.userProfile().pubkeyHex
        val template =
            MeetingSpaceEvent.build(
                room = current.roomName.trim(),
                status = StatusTag.STATUS.OPEN,
                service = service,
                host = ParticipantTag(hostPubkey, null, ROLE.HOST.code, null),
            ) {
                endpoint(endpoint)
                current.summary
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { summary(it) }
                current.imageUrl
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { image(it) }
            }

        val signed =
            try {
                accountModel.signAndComputeBroadcast(template)
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isPublishing = false,
                        error = "Failed to publish: ${t.message ?: t::class.simpleName}",
                    )
                }
                return null
            }

        // Don't reset isPublishing here — the sheet dismisses on success
        // and the VM is scoped per-composition so the next open starts
        // fresh anyway.
        return RoomLaunchInfo(
            addressValue = signed.address().toValue(),
            authBaseUrl = service,
            endpoint = endpoint,
            hostPubkey = hostPubkey,
            roomId = signed.dTag(),
            kind = MeetingSpaceEvent.KIND,
        )
    }

    data class FormState(
        val roomName: String,
        val summary: String,
        val serviceUrl: String,
        val endpointUrl: String,
        val imageUrl: String,
        val isPublishing: Boolean,
        val error: String?,
    ) {
        val canSubmit: Boolean
            get() = roomName.isNotBlank() && serviceUrl.isNotBlank() && endpointUrl.isNotBlank()

        companion object {
            fun defaults() =
                FormState(
                    roomName = "",
                    summary = "",
                    serviceUrl = DEFAULT_SERVICE_URL,
                    endpointUrl = DEFAULT_ENDPOINT_URL,
                    imageUrl = "",
                    isPublishing = false,
                    error = null,
                )
        }
    }

    /**
     * Captured fields needed to launch [AudioRoomActivity]. Mirrors the
     * `EXTRA_*` set the activity expects; pulled out here so the sheet
     * is a thin renderer.
     */
    data class RoomLaunchInfo(
        val addressValue: String,
        val authBaseUrl: String,
        val endpoint: String,
        val hostPubkey: String,
        val roomId: String,
        val kind: Int,
    )

    companion object {
        /**
         * Public nostrnests deployment — a blank form produces a working
         * room here. Users can edit either field to point at their own
         * moq-auth / moq-relay pair.
         */
        const val DEFAULT_SERVICE_URL: String = "https://moq.nostrnests.com"
        const val DEFAULT_ENDPOINT_URL: String = "https://moq.nostrnests.com"
    }
}
