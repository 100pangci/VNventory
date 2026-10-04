package com.vnventory.app.domain

import com.vnventory.app.ui.add.PurchaseFormState
import org.junit.Assert.*
import org.junit.Test

class PurchaseFormTest {
    @Test fun `商品小计是单盒价格乘数量且不改变每盒单价`() {
        val form = PurchaseFormState(priceText = "6800", currency = "JPY", quantity = 2)
        assertEquals(6800L, form.parsedPrice)
        assertEquals(13600L, form.subtotalMinor)
        assertTrue(form.canSave)
    }

    @Test fun `商品小计按最小货币单位精确计算`() {
        assertEquals(303L, PurchaseFormState(priceText = "1.01", currency = "CNY", quantity = 3).subtotalMinor)
        assertNull(PurchaseFormState(priceText = "", quantity = 2).subtotalMinor)
        assertTrue(PurchaseFormState(priceText = "", quantity = 2).canSave)
        assertEquals(0L, PurchaseFormState(priceText = "0", quantity = 2).subtotalMinor)
    }

    @Test fun `数量非法或小计溢出时不展示伪造金额也不能保存`() {
        val overflow = PurchaseFormState(priceText = Long.MAX_VALUE.toString(), currency = "JPY", quantity = 2)
        assertNull(overflow.subtotalMinor)
        assertFalse(overflow.canSave)
        assertNull(overflow.copy(quantity = 0).subtotalMinor)
        assertFalse(overflow.copy(quantity = 0).canSave)
    }
}
