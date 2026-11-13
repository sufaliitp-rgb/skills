package com.sandhyagems.app.util

import com.sandhyagems.app.StoreConfig
import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    /** Reads a price such as "1500.00" or "1,500". Returns null for empty or invalid text. */
    fun parse(raw: String?): BigDecimal? =
        raw?.trim()?.replace(",", "")?.takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

    /** Formats in the Indian style: ₹1,25,000 (paise are shown only when not zero). */
    fun format(amount: BigDecimal): String {
        val scaled = amount.setScale(2, RoundingMode.HALF_UP).abs()
        val whole = scaled.toBigInteger()
        val paise = scaled.subtract(BigDecimal(whole)).movePointRight(2).toInt()
        val sign = if (amount.signum() < 0) "-" else ""
        val fraction = if (paise == 0) "" else "." + paise.toString().padStart(2, '0')
        return sign + StoreConfig.CURRENCY_SYMBOL + groupIndian(whole.toString()) + fraction
    }

    /** 1234567 -> 12,34,567: the last three digits, then groups of two. */
    fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val groups = ArrayList<String>()
        var end = head.length
        while (end > 0) {
            val start = maxOf(0, end - 2)
            groups.add(0, head.substring(start, end))
            end = start
        }
        return groups.joinToString(",") + "," + digits.takeLast(3)
    }

    /** Whole-number percentage saved, or null when there is no real discount. */
    fun discountPercent(price: BigDecimal, compareAt: BigDecimal?): Int? {
        if (compareAt == null || compareAt <= price || compareAt.signum() <= 0) return null
        val percent = compareAt.subtract(price).multiply(BigDecimal(100))
            .divide(compareAt, 0, RoundingMode.HALF_UP).toInt()
        return percent.takeIf { it > 0 }
    }
}
