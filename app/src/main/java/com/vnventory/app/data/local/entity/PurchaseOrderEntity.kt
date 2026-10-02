package com.vnventory.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 一次购买 / 一个转运批次。
 *
 * 例：“2026-09 骏河屋一批”，其中包含 A、B、C 三盒游戏与若干费用。
 */
@Entity(tableName = "purchase_order")
data class PurchaseOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val merchant: String?,
    /** 下单日期；允许为空 */
    val orderDate: LocalDate?,
    /** 订单默认币种（商品价格与新增费用的默认值） */
    val currency: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
