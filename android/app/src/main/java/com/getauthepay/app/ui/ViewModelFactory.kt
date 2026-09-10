package com.getauthepay.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * Tiny `ViewModelProvider.Factory` builder so screens can construct
 * ViewModels with injected dependencies (the ServiceLocator) without
 * pulling in a DI framework.
 *
 * ViewModels must never reach for a CompositionLocal themselves — a
 * `CompositionLocal` can only be read during composition, and a ViewModel
 * outlives the composition that created it. Dependencies are therefore
 * passed through the constructor and the composable supplies the factory.
 */
@Suppress("UNCHECKED_CAST")
fun <VM : ViewModel> authePayViewModelFactory(initializer: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            initializer() as T

        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated("Use the CreationExtras overload", level = DeprecationLevel.HIDDEN)
        override fun <T : ViewModel> create(modelClass: Class<T>): T = initializer() as T
    }
