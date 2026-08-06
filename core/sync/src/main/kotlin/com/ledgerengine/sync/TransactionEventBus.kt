package com.ledgerengine.sync

import com.ledgerengine.domain.model.LedgerEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide broadcast of every ledger mutation — locally created, synced,
 * confirmed, or reconciled — independent of which account/screen is currently
 * observing. UI surfaces that only care about balances subscribe to a
 * per-account [kotlinx.coroutines.flow.StateFlow] instead (see
 * `OfflineQueueRepository.observeAccount`); this bus exists for cross-cutting
 * concerns like toast/snackbar notifications, badge counts, and analytics that
 * want every event exactly once, in order.
 */
@Singleton
class TransactionEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<LedgerEntry>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<LedgerEntry> = _events

    suspend fun publish(entry: LedgerEntry) {
        _events.emit(entry)
    }
}
