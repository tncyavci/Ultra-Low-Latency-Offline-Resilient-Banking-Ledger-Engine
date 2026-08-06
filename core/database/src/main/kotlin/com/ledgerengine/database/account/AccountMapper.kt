package com.ledgerengine.database.account

import com.ledgerengine.domain.model.Account
import com.ledgerengine.domain.model.AccountId
import com.ledgerengine.domain.model.Money

internal fun AccountEntity.toDomain(): Account = Account(
    id = AccountId(accountId),
    displayName = displayName,
    currencyCode = currencyCode,
    balance = Money.ofMinorUnits(balanceMinorUnits),
    availableBalance = Money.ofMinorUnits(availableBalanceMinorUnits),
    lastReconciledHlc = lastReconciledHlc,
)

internal fun Account.toEntity(): AccountEntity = AccountEntity(
    accountId = id.value,
    displayName = displayName,
    currencyCode = currencyCode,
    balanceMinorUnits = balance.minorUnits,
    availableBalanceMinorUnits = availableBalance.minorUnits,
    lastReconciledHlc = lastReconciledHlc,
)
