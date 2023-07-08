package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Immutable

@Immutable
sealed class GenericLoadable<T> {

    @Immutable
    class Loading<T> : GenericLoadable<T>()

    @Immutable
    class Loaded<T>(val loaded: T) : GenericLoadable<T>()

    @Immutable
    class Empty<T> : GenericLoadable<T>()

    @Immutable
    class Error<T>(val errorMessage: String) : GenericLoadable<T>()
}
