package com.vitorpamplona.amethyst.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Contact
import com.vitorpamplona.amethyst.model.FollowSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _sortOption = MutableStateFlow(FollowSortOption.CHRONOLOGICAL)
    val sortOption: StateFlow<FollowSortOption> = _sortOption.asStateFlow()

    fun setContacts(contacts: List<Contact>) {
        _contacts.value = contacts
        applySorting()
    }

    fun setSortOption(option: FollowSortOption) {
        _sortOption.value = option
        applySorting()
    }

    private fun applySorting() {
        val currentContacts = _contacts.value
        val currentOption = _sortOption.value

        val sortedContacts = when (currentOption) {
            FollowSortOption.CHRONOLOGICAL -> currentContacts.sortedBy { it.createdAt }
            FollowSortOption.SATS_SENT -> currentContacts.sortedByDescending { it.satsSent }
            FollowSortOption.SATS_RECEIVED -> currentContacts.sortedByDescending { it.satsReceived }
            FollowSortOption.FOLLOWER_COUNT -> currentContacts.sortedByDescending { it.followerCount }
        }

        _contacts.value = sortedContacts
    }
}
