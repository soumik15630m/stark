package com.soumik.stark.ui.common

import kotlinx.coroutines.flow.MutableStateFlow

/** Cross-screen banners: an available update and a tamper/root warning. */
object AppSignals {
    val updateTag = MutableStateFlow<String?>(null)
    val tampered = MutableStateFlow(false)
    /** Set from the widget to open the PIN-free ride dashboard directly. */
    val openDashboard = MutableStateFlow(false)
}
