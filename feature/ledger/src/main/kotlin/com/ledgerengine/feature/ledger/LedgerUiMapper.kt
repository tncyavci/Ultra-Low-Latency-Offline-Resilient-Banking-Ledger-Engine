package com.ledgerengine.feature.ledger

import com.ledgerengine.domain.model.LedgerEntry
import com.ledgerengine.domain.model.Money
import com.ledgerengine.domain.model.TransactionDirection
import com.ledgerengine.domain.model.TransactionStatus
import com.ledgerengine.ui.chart.SpendingPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

fun formatMoney(money: Money, currencyCode: String): String {
    val currency = Currency.getInstance(currencyCode)
    val majorUnits = money.minorUnits / Math.pow(10.0, currency.defaultFractionDigits.toDouble())
    val formatter = java.text.NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        this.currency = currency
    }
    return formatter.format(majorUnits)
}

fun LedgerEntry.toUiModel(): TransactionUiModel {
    val isCredit = direction == TransactionDirection.CREDIT
    val instant = Instant.ofEpochMilli(createdAtEpochMillis)
    return TransactionUiModel(
        id = id.value,
        title = memo?.takeIf { it.isNotBlank() } ?: if (isCredit) "Incoming transfer" else "Payment",
        subtitle = dayFormatter.format(instant.atZone(ZoneId.systemDefault())),
        amountFormatted = (if (isCredit) "+" else "-") + formatMoney(amount.money, amount.currencyCode),
        isCredit = isCredit,
        statusLabel = status.toDisplayLabel(),
    )
}

private fun TransactionStatus.toDisplayLabel(): String = when (this) {
    TransactionStatus.PENDING_LOCAL, TransactionStatus.SIGNED, TransactionStatus.QUEUED -> "Pending · offline"
    TransactionStatus.SYNCING -> "Syncing…"
    TransactionStatus.CONFIRMED -> "Completed"
    TransactionStatus.REJECTED -> "Failed"
    TransactionStatus.CONFLICTED -> "Needs review"
}

/** Most recent entries, oldest-first, as chart points — sign-agnostic magnitude so both debits and credits plot as positive bars. */
fun List<LedgerEntry>.toSpendingPoints(maxPoints: Int = 14): ImmutableList<SpendingPoint> =
    asReversed()
        .takeLast(maxPoints)
        .map { entry ->
            SpendingPoint(
                label = dayFormatter.format(Instant.ofEpochMilli(entry.createdAtEpochMillis).atZone(ZoneId.systemDefault())),
                amountMinorUnits = kotlin.math.abs(entry.amount.money.minorUnits),
            )
        }
        .toPersistentList()

fun List<LedgerEntry>.toUiModels(): ImmutableList<TransactionUiModel> = map { it.toUiModel() }.toPersistentList()
