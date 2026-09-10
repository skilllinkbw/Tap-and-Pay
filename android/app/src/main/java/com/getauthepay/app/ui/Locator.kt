package com.getauthepay.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.getauthepay.app.di.ServiceLocator

/**
 * Composition local giving every screen access to the app's service
 * container.
 *
 * It lives in the `ui` package because that is where screens resolve it
 * from (`com.getauthepay.app.ui.LocalLocator`). It is provided once by
 * [com.getauthepay.app.MainActivity] above the navigation host, so any
 * screen reached through navigation can read it.
 *
 * ViewModels must NOT read this — a ViewModel outlives the composition
 * that created it. Dependencies are passed into ViewModel constructors
 * instead; see `ui/ViewModelFactory.kt`.
 */
val LocalLocator = staticCompositionLocalOf<ServiceLocator> {
    error("ServiceLocator not provided")
}
