package com.streamvault.domain.model.stremio

import kotlinx.coroutines.flow.StateFlow

/**
 * [SIGN_IN]: linking could not finish. [SESSION_EXPIRED]: a linked account's session was rejected while syncing.
 * [LIBRARY_TOO_LARGE]: the cloud library exceeds [StremioAccountLimits.MAX_LIBRARY_ITEMS].
 */
enum class StremioAccountError { NETWORK, SIGN_IN, SESSION_EXPIRED, EXPIRED, STORAGE, SYNC, INCOGNITO, LIBRARY_TOO_LARGE }

object StremioAccountLimits {
    const val MAX_LIBRARY_ITEMS = 5000
}

data class StremioAccountState(
    val ready: Boolean = false,
    val connected: Boolean = false,
    val displayName: String? = null,
    val linkUrl: String? = null,
    val linkCode: String? = null,
    val busy: Boolean = false,
    val lastSyncAt: Long = 0,
    /** Cloud library items the last sync could not import because no installed add-on resolves their IDs. */
    val skippedItems: Int = 0,
    val error: StremioAccountError? = null
)

/** Presentation never receives the account credential. */
interface StremioAccount {
    val state: StateFlow<StremioAccountState>
    fun start()
    suspend fun load()
    suspend fun beginLink()
    suspend fun pollLink()
    suspend fun cancelLink()
    suspend fun disconnect()
    suspend fun sync(): Boolean
}
