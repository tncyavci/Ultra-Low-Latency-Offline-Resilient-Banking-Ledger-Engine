package com.ledgerengine.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `ofMajorUnits converts to exact minor units without float drift`() {
        val money = Money.ofMajorUnits(19.99)
        assertEquals(1999L, money.minorUnits)
    }

    @Test
    fun `repeated additions of 0,10 never drift due to floating point error`() {
        var total = Money.ZERO
        repeat(1000) { total += Money.ofMajorUnits(0.10) }
        assertEquals(Money.ofMajorUnits(100.00), total)
    }

    @Test
    fun `arithmetic operators behave as expected`() {
        val ten = Money.ofMinorUnits(1000)
        val three = Money.ofMinorUnits(300)
        assertEquals(Money.ofMinorUnits(1300), ten + three)
        assertEquals(Money.ofMinorUnits(700), ten - three)
        assertTrue((-ten).isNegative)
        assertFalse(ten.isNegative)
        assertTrue(Money.ZERO.isZero)
    }

    @Test(expected = ArithmeticException::class)
    fun `addition overflow throws rather than silently wrapping`() {
        Money.ofMinorUnits(Long.MAX_VALUE) + Money.ofMinorUnits(1)
    }

    @Test
    fun `currency amounts of the same currency add correctly`() {
        val a = CurrencyAmount(Money.ofMajorUnits(10.0), "EUR")
        val b = CurrencyAmount(Money.ofMajorUnits(5.0), "EUR")
        assertEquals(CurrencyAmount(Money.ofMajorUnits(15.0), "EUR"), a + b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched currency addition is rejected`() {
        val eur = CurrencyAmount(Money.ofMajorUnits(10.0), "EUR")
        val usd = CurrencyAmount(Money.ofMajorUnits(5.0), "USD")
        eur + usd
    }
}
