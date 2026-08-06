package com.ledgerengine.database.ledger

import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.CurrencyAmount
import com.ledgerengine.domain.model.IdempotencyKey
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.Money
import com.ledgerengine.domain.model.TransactionDirection
import com.ledgerengine.domain.model.TransactionId
import com.ledgerengine.domain.model.TransactionStatus

/** Maps between the SQLDelight-generated row type and the domain [LedgerEntry]. */
internal fun Ledger_entry.toDomain(): LedgerEntry = LedgerEntry(
    id = TransactionId(id),
    idempotencyKey = IdempotencyKey(idempotency_key),
    accountId = AccountId(account_id),
    counterpartyAccountId = counterparty_account_id?.let(::AccountId),
    direction = TransactionDirection.valueOf(direction),
    amount = CurrencyAmount(Money.ofMinorUnits(amount_minor_units), currency_code),
    memo = memo,
    status = TransactionStatus.valueOf(status),
    hlcTimestamp = hlc_timestamp,
    createdAtEpochMillis = created_at_epoch_millis,
    signature = signature,
    signingKeyId = signing_key_id,
    serverSequence = server_sequence,
)
