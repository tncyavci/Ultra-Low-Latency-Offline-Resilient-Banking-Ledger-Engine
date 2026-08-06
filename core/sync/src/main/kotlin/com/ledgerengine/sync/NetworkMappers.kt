package com.ledgerengine.sync

import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.CurrencyAmount
import com.ledgerengine.domain.model.IdempotencyKey
import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.Money
import com.ledgerengine.domain.model.TransactionDirection
import com.ledgerengine.domain.model.TransactionId
import com.ledgerengine.domain.model.TransactionStatus
import com.ledgerengine.network.dto.TransactionEventDto
import com.ledgerengine.network.dto.TransactionSyncRequestDto
import java.util.Base64

internal fun LedgerEntry.toSyncRequest(): TransactionSyncRequestDto {
    val signatureBytes = requireNotNull(signature) { "Cannot sync an unsigned entry: ${id.value}" }
    val keyId = requireNotNull(signingKeyId) { "Cannot sync an entry with no signing key id: ${id.value}" }
    return TransactionSyncRequestDto(
        id = id.value,
        idempotencyKey = idempotencyKey.value,
        accountId = accountId.value,
        counterpartyAccountId = counterpartyAccountId?.value,
        direction = direction.name,
        amountMinorUnits = amount.money.minorUnits,
        currencyCode = amount.currencyCode,
        memo = memo,
        hlcTimestamp = hlcTimestamp,
        createdAtEpochMillis = createdAtEpochMillis,
        signature = Base64.getEncoder().encodeToString(signatureBytes),
        signingKeyId = keyId,
    )
}

internal fun TransactionEventDto.toDomain(): LedgerEntry = LedgerEntry(
    id = TransactionId(id),
    idempotencyKey = IdempotencyKey(idempotencyKey),
    accountId = AccountId(accountId),
    counterpartyAccountId = counterpartyAccountId?.let(::AccountId),
    direction = TransactionDirection.valueOf(direction),
    amount = CurrencyAmount(Money.ofMinorUnits(amountMinorUnits), currencyCode),
    memo = memo,
    status = TransactionStatus.CONFIRMED,
    hlcTimestamp = hlcTimestamp,
    createdAtEpochMillis = createdAtEpochMillis,
    signature = Base64.getDecoder().decode(signature),
    signingKeyId = signingKeyId,
    serverSequence = serverSequence,
)
