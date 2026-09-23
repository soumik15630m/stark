package com.soumik.stark.core.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Tracks whether the app is currently unlocked, and applies the auto-lock timeout. */
object SessionLock {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    private var backgroundedAt = 0L
    var decoyMode = false
        private set

    fun initFor(context: Context) {
        _locked.value = AppLock.isPinSet(context)
    }

    fun unlock(decoy: Boolean = false) {
        decoyMode = decoy
        _locked.value = false
    }

    /**
     * Re-lock as soon as the app leaves the foreground (screen off, app switch, home). The user
     * expects a fresh unlock every time; the PIN-free ride dashboard covers glanceable needs.
     */
    fun onBackground(context: Context) {
        backgroundedAt = System.currentTimeMillis()
        if (AppLock.isPinSet(context)) _locked.value = true
    }

    fun onForeground(context: Context) {
        // No-op: locking happens on background. Kept for lifecycle symmetry.
    }

    fun lockNow() {
        _locked.value = true
    }
}
