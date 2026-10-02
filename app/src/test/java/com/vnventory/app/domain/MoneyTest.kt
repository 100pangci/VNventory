package com.vnventory.app.domain

import com.vnventory.app.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    // ---- decimals ----

    @Test
    fun `JPY 没有小数位`() {
        assertEquals(0, Money.decimals("JPY"))
        assertEquals(0, Money.decimals("jpy"))
        assertEquals(0, Money.decimals(" KRW "))
    }

    @Test
    fun `CNY 有两位小数`() {
        assertEquals(2, Money.decimals("CNY"))
        assertEquals(2, Money.decimals("USD"))
    }

    @Test
    fun `未知货币回退两位`() {
        assertEquals(2, Money.decimals("XYZ"))
    }

    // ---- format ----

    @Test
    fun `日元整数格式化带千分位`() {
        assertEquals("¥1,234", Money.format(1234, "JPY"))
        assertEquals("¥0", Money.format(0, "JPY"))
    }

    @Test
    fun `人民币按分格式化`() {
        assertEquals("¥123.45", Money.format(12345, "CNY"))
        assertEquals("¥123", Money.format(12300, "CNY"))
        assertEquals("¥0.01", Money.format(1, "CNY"))
    }

    @Test
    fun `带代码格式化`() {
        assertEquals("¥12,000 JPY", Money.formatWithCode(12000, "JPY"))
        assertEquals("$9.99 USD", Money.formatWithCode(999, "USD"))
    }

    // ---- parse ----

    @Test
    fun `解析日元`() {
        assertEquals(1234L, Money.parse("1234", "JPY"))
        assertEquals(1234L, Money.parse(" 1,234 ", "JPY"))
        assertEquals(1234L, Money.parse("¥1234", "JPY"))
        assertEquals(1234L, Money.parse("¥1,234", "JPY"))
    }

    @Test
    fun `解析人民币`() {
        assertEquals(12345L, Money.parse("123.45", "CNY"))
        assertEquals(123450L, Money.parse("1,234.5", "CNY"))
        assertEquals(100L, Money.parse("1.00", "CNY"))
        assertEquals(100L, Money.parse("1。00", "CNY"))
    }

    @Test
    fun `小数位数超限不静默取整`() {
        assertNull(Money.parse("12.345", "CNY"))
        assertNull(Money.parse("0.5", "JPY"))
    }

    @Test
    fun `非法输入返回 null`() {
        assertNull(Money.parse("", "JPY"))
        assertNull(Money.parse("abc", "JPY"))
        assertNull(Money.parse("-5", "JPY"))
        assertNull(Money.parse("1.2.3", "CNY"))
    }

    // ---- toEditableString ----

    @Test
    fun `编辑回填字符串`() {
        assertEquals("123.45", Money.toEditableString(12345, "CNY"))
        assertEquals("123.4", Money.toEditableString(12340, "CNY"))
        assertEquals("123", Money.toEditableString(12300, "CNY"))
        assertEquals("1234", Money.toEditableString(1234, "JPY"))
        assertEquals("0", Money.toEditableString(0, "CNY"))
    }

    @Test
    fun `格式化与解析互为逆运算`() {
        val samples = listOf(0L, 1L, 99L, 100L, 12345L, 99999999L)
        for (s in samples) {
            for (code in listOf("CNY", "JPY", "USD")) {
                val text = Money.toEditableString(s, code)
                assertEquals("round trip $s $code", s, Money.parse(text, code))
            }
        }
    }
}
