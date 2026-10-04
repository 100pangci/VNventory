package com.vnventory.app.domain.model

import java.time.LocalDate
import com.vnventory.app.domain.text.Message
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message

/**
 * 用户实际拥有的一盒（事实来源，UI 主模型）。
 *
 * 说明：
 * - [releaseId] 为空表示“手动版本”（VNDB 找不到对应版本时自建）。
 * - [vnTitle] / [releaseTitle] / [coverUrl] 为添加时的快照，
 *   即使 VNDB 缓存被清空或元数据变化，收藏记录与展示也不丢失。
 */
data class OwnedCopy(
    val id: Long,
    val vnId: String,
    val releaseId: String?,
    val vnTitle: String,
    val releaseTitle: String?,
    val coverUrl: String?,
    val priceMinor: Long?,
    val currency: String,
    val condition: CopyCondition,
    val conditionNote: String?,
    val purchaseDate: LocalDate?,
    val shop: String?,
    val orderId: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isManualRelease: Boolean get() = releaseId == null

    /** 列表展示用版本名：Release 名 > 手动版本名 > “手动版本” */
    val displayReleaseName: Message
        get() = releaseTitle?.takeIf { it.isNotBlank() }?.let(Message::Literal)
            ?: message(if (isManualRelease) MessageKey.MANUAL_RELEASE else MessageKey.UNKNOWN_RELEASE)
}

/** 同一 VN 下同一发行版本；手动版本按用户填写的版本名区分。 */
fun OwnedCopy.versionKey(): Triple<String, String?, String?> = Triple(vnId, releaseId, releaseTitle.takeIf { releaseId == null })

fun List<OwnedCopy>.copyOrdinal(copy: OwnedCopy): Int? {
    val siblings = filter { it.versionKey() == copy.versionKey() }.sortedWith(compareBy({ it.createdAt }, { it.id }))
    return if (siblings.size > 1) siblings.indexOfFirst { it.id == copy.id }.takeIf { it >= 0 }?.plus(1) else null
}

fun OwnedCopy.knownPriceTotals(): Map<String, Long> = priceMinor?.let { mapOf(currency to it) }.orEmpty()

/**
 * 购买订单/转运批次。
 */
data class PurchaseOrder(
    val id: Long,
    val title: String,
    val merchant: String?,
    val orderDate: LocalDate?,
    val currency: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
