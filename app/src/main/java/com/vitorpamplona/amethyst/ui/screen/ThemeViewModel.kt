package com.vitorpamplona.amethyst.ui.screen

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.errorprone.annotations.Immutable

@Immutable
class ThemeViewModel : ViewModel() {
    private val _theme = MutableLiveData(0)
    val theme: LiveData<Int> = _theme

    fun onChange(newValue: Int) {
        _theme.value = newValue
    }
}
