package com.vnventory.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vnventory.app.domain.model.CopyCondition
import java.time.LocalDate

/**
 * 用户实际拥有的一盒（事实来源）。
 *
 * - [releaseId] 为空 = 手动版本（VNDB 找不到对应版本时创建），
 *   后续可随时绑定到某个 VNDB Release（只改这一行）。
 * - [vnTitle] / [releaseTitle] / [coverUrl] 为添加时的快照，
 *   即使 VNDB 缓存清空或元数据变化，收藏记录与展示不会丢失。
 * - 删除订单时本行保留（orderId 置空），绝不因批次删除丢失收藏。
 */
@Entity(
    tableName = "owned_copy",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("vnId"),
        Index("releaseId"),
        Index("orderId"),
        Index("purchaseDate"),
    ],
)
data class OwnedCopyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** VNDB VN ID（形如 `v17`）；手动版本也必须有关联 VN */
    val vnId: String,
    /** VNDB Release ID（形如 `r12345`）；为空 = 手动版本 */
    val releaseId: String?,
    /** 快照：VN 标题 */
    val vnTitle: String,
    /** 快照：Release 标题 或 手动版本名 */
    val releaseTitle: String?,
    /** 快照：封面图 URL */
    val coverUrl: String?,
    /** 购入价格（最小货币单位，例：日元=円，人民币=分） */
    val priceMinor: Long,
    /** ISO 4217 货币代码，如 `JPY` / `CNY` */
    val currency: String,
    /** 品相 */
    val condition: CopyCondition,
    /** 品相为自定义时的说明文本 */
    val conditionNote: String?,
    /** 购买日期；允许为空 */
    val purchaseDate: LocalDate?,
    val shop: String?,
    val orderId: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
