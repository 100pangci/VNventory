package com.vnventory.app.domain

import com.vnventory.app.domain.model.Fx
import com.vnventory.app.domain.text.MessageException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FxTest {

    @Test fun `用户输入汇率解析`() {
        assertEquals(50_000_000L, Fx.parse("0.05"))
        assertEquals(42_299_852L, Fx.parse("0.042299852"))
        assertEquals(1_000_000_000L, Fx.parse("1"))
        assertEquals(1_000_000_000L, Fx.parse("1.0"))
        assertEquals(1_500L, Fx.parse("0.0000015"))
        assertNull(Fx.parse("0"))
        assertNull(Fx.parse("-0.1"))
        assertNull(Fx.parse("abc"))
        assertNull(Fx.parse(""))
        assertNull(Fx.parse("0.0000000001"))
        assertNull(Fx.parse("1e5"))
    }

    @Test fun `数据源小数按九位四舍五入`() {
        assertEquals(42_299_852L, Fx.fromDecimal("0.042299852"))
        assertEquals(42_299_853L, Fx.fromDecimal("0.0422998525"))
        assertEquals(1_000_000_000L, Fx.fromDecimal("1.0000000004"))
        assertNull(Fx.fromDecimal("0"))
        assertNull(Fx.fromDecimal("not-a-number"))
        assertNull(Fx.fromDecimal(""))
    }

    @Test fun `定点换算与 half-up 舍入`() {
        // 12,800 日元 × 0.042299852 ≈ ¥541.44（54,144 分）
        assertEquals(54_144L, Fx.convert(12_800L, "JPY", 42_299_852L, "CNY"))
        // 100 元 → 日元：10,000 分 × 19.5 = 1,950 円（0 位小数）
        assertEquals(1_950L, Fx.convert(10_000L, "CNY", 19_500_000_000L, "JPY"))
        // 1 日元 × 0.005 = 0.005 元 = 0.5 分 → half-up 进 1 分
        assertEquals(1L, Fx.convert(1L, "JPY", Fx.parse("0.005")!!, "CNY"))
        assertEquals(0L, Fx.convert(0L, "JPY", Fx.parse("0.05")!!, "CNY"))
        // 0.004999 元 = 0.4999 分 → 舍去
        assertEquals(0L, Fx.convert(1L, "JPY", Fx.parse("0.004999")!!, "CNY"))
    }

    @Test fun `换算溢出拒绝而不是静默截断`() {
        try {
            Fx.convert(Long.MAX_VALUE, "JPY", Fx.parse("100000")!!, "CNY")
            fail("expected overflow")
        } catch (e: MessageException) {
            assertTrue(e.userMessage.toString().contains("OVERFLOW"))
        }
    }

    @Test fun `展示格式`() {
        assertEquals("1 JPY = 0.0423 CNY", Fx.format(42_299_852L, "jpy", "cny"))
        assertEquals("1 CNY = 12.5 JPY", Fx.format(12_500_000_000L, "CNY", "JPY"))
        assertEquals("1 CNY = 1 JPY", Fx.format(1_000_000_000L, "CNY", "JPY"))
    }}
