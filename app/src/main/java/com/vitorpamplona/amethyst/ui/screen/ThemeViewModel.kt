package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

@Immutable
class ThemeViewModel : ViewModel() {
    private val _theme = MutableLiveData(0)
    val theme: LiveData<Int> = _theme

    fun onChange(newValue: Int) {
        _theme.value = newValue
    }
}
