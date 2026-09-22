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

    fun onBackground() {
        backgroundedAt = System.currentTimeMillis()
    }

    fun onForeground(context: Context) {
        if (!AppLock.isPinSet(context)) return
        if (backgroundedAt == 0L) return
        val elapsed = System.currentTimeMillis() - backgroundedAt
        if (elapsed >= AppLock.timeoutMs(context)) _locked.value = true
    }

    fun lockNow() {
        _locked.value = true
    }
}
