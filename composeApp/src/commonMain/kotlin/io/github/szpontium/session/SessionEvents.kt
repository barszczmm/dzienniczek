package io.github.szpontium.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Process-wide signals about stored session data. */
object SessionEvents {
    private val _credentialsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emitted when a student's credentials changed (e.g. an automatically refreshed token) and should be saved. */
    val credentialsChanged: SharedFlow<Unit> = _credentialsChanged.asSharedFlow()

    fun notifyCredentialsChanged() {
        _credentialsChanged.tryEmit(Unit)
    }
}
