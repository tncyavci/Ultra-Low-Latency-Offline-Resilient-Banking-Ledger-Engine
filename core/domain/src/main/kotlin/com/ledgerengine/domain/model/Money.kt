package com.ledgerengine.domain.model

/**
 * Exact monetary value stored as integer minor units (e.g. cents) to avoid
 * floating point drift in balance arithmetic. All ledger math must go
 * through this type — never use [Double]/[Float] for money.
 */
@JvmInline
value class Money private constructor(val minorUnits: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(minorUnits, other.minorUnits))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(minorUnits, other.minorUnits))

    operator fun unaryMinus(): Money = Money(Math.negateExact(minorUnits))

    override fun compareTo(other: Money): Int = minorUnits.compareTo(other.minorUnits)

    val isNegative: Boolean get() = minorUnits < 0
    val isPositive: Boolean get() = minorUnits > 0
    val isZero: Boolean get() = minorUnits == 0L

    companion object {
        val ZERO = Money(0L)

        fun ofMinorUnits(minorUnits: Long): Money = Money(minorUnits)

        /** e.g. ofMajorUnits(19.99, scale = 2) -> 1999 minor units */
        fun ofMajorUnits(amount: Double, scale: Int = 2): Money {
            require(scale in 0..6) { "Unsupported currency scale: $scale" }
            val factor = Math.pow(10.0, scale.toDouble())
            return Money(Math.round(amount * factor))
        }
    }
}

/** ISO 4217 currency code paired with a [Money] amount. */
data class CurrencyAmount(
    val money: Money,
    val currencyCode: String,
) {
    init {
        require(currencyCode.length == 3) { "currencyCode must be ISO 4217 (3 letters): $currencyCode" }
    }

    operator fun plus(other: CurrencyAmount): CurrencyAmount {
        require(currencyCode == other.currencyCode) {
            "Cannot add mismatched currencies: $currencyCode vs ${other.currencyCode}"
        }
        return copy(money = money + other.money)
    }
}
