package com.vitorpamplona.amethyst.ui.screen

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ThemeViewModel : ViewModel() {
    private val _theme = MutableLiveData("System")
    val theme: LiveData<String> = _theme

    fun onChange(newValue: String) {
        _theme.value = newValue
    }
}
