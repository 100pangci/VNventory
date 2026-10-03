package com.vnventory.app.data.backup

import com.vnventory.app.data.local.entity.ExpenseAllocationEntity
import com.vnventory.app.data.local.entity.ExpenseEntity
import com.vnventory.app.data.local.entity.OwnedCopyEntity
import com.vnventory.app.data.local.entity.PurchaseOrderEntity
import com.vnventory.app.data.mapper.toDomain
import com.vnventory.app.data.repository.LocalRules
import com.vnventory.app.domain.cost.CostCopyInput
import com.vnventory.app.domain.cost.CostEngine
import com.vnventory.app.domain.cost.CostExpenseInput
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.ExpenseCategory
import com.vnventory.app.domain.model.Money
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate

/** 独立于 Room schema 的可移植格式；只备份购买事实和配置，不包含 VNDB 缓存。 */
@Serializable
data class BackupDocument(
    val format: String,
    val schemaVersion: Int,
    val exportedAt: Long,
    val settings: BackupSettings,
    val orders: List<BackupOrder>,
    val copies: List<BackupCopy>,
    val expenses: List<BackupExpense>,
    val allocations: List<BackupAllocation>,
)

@Serializable
data class BackupSettings(val defaultCurrency: String)

@Serializable
data class BackupOrder(
    val id: Long,
    val title: String,
    val merchant: String?,
    val orderDate: String?,
    val currency: String,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupCopy(
    val id: Long,
    val vnId: String,
    val releaseId: String?,
    val vnTitle: String,
    val releaseTitle: String?,
    val coverUrl: String?,
    val priceMinor: Long,
    val currency: String,
    val condition: String,
    val conditionNote: String?,
    val purchaseDate: String?,
    val shop: String?,
    val orderId: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupExpense(
    val id: Long,
    val orderId: Long,
    val name: String,
    val category: String,
    val amountMinor: Long,
    val currency: String,
    val mode: String,
    val notes: String?,
    val createdAt: Long,
)

@Serializable
data class BackupAllocation(val expenseId: Long, val ownedCopyId: Long, val amountMinor: Long)

data class BackupData(
    val exportedAt: Long,
    val defaultCurrency: String,
    val orders: List<PurchaseOrderEntity>,
    val copies: List<OwnedCopyEntity>,
    val expenses: List<ExpenseEntity>,
    val allocations: List<ExpenseAllocationEntity>,
) {
    /** 解码时及正式写事务内都校验，禁止部分导入、猜测修复或静默丢弃坏记录。 */
    fun validate() {
        LocalRules.currency(defaultCurrency)
        fun ids(values: List<Long>) {
            require(values.all { it > 0 } && values.distinct().size == values.size) { "备份中的记录 ID 无效或重复" }
        }
        ids(orders.map { it.id })
        ids(copies.map { it.id })
        ids(expenses.map { it.id })
        val orderIds = orders.map { it.id }.toSet()
        val expensesById = expenses.associateBy { it.id }
        val copiesById = copies.associateBy { it.id }
        orders.forEach {
            require(it.title.isNotBlank()) { "备份中的订单名称为空" }
            LocalRules.currency(it.currency)
        }
        copies.forEach {
            LocalRules.copy(it.toDomain())
            require(it.releaseId == null || it.releaseId.matches(Regex("r[1-9][0-9]*"))) { "备份中的版本 ID 无效" }
            require(it.orderId == null || it.orderId in orderIds) { "备份中的收藏关联了不存在的订单" }
        }
        require(allocations.map { it.expenseId to it.ownedCopyId }.distinct().size == allocations.size) { "备份中的手动分摊重复" }
        allocations.forEach {
            val expense = expensesById[it.expenseId]
            require(expense != null && expense.mode == AllocationMode.MANUAL) { "备份中的分摊未关联手动费用" }
            val copy = copiesById[it.ownedCopyId]
            require(copy != null && copy.orderId == expense.orderId) { "备份中的分摊包含不属于该订单的收藏" }
            require(it.amountMinor >= 0) { "备份中的分摊金额不能为负数" }
        }
        val copiesByOrder = copies.groupBy { it.orderId }
        val allocationsByExpense = allocations.groupBy { it.expenseId }
        expenses.forEach {
            require(it.orderId in orderIds) { "备份中的费用关联了不存在的订单" }
            require(it.name.isNotBlank()) { "备份中的费用名称为空" }
            LocalRules.currency(it.currency)
            val problem = CostEngine.expenseProblem(
                CostExpenseInput(
                    expenseId = it.id,
                    name = it.name,
                    category = it.category,
                    amountMinor = it.amountMinor,
                    currency = it.currency,
                    mode = it.mode,
                    manualAllocations = allocationsByExpense[it.id].orEmpty().associate { row -> row.ownedCopyId to row.amountMinor },
                ),
                copiesByOrder[it.orderId].orEmpty().map { copy -> CostCopyInput(copy.id, copy.priceMinor, copy.currency) },
            )
            require(problem == null) { problem!! }
        }
        Money.totals(copies.map { it.currency to it.priceMinor } + expenses.map { it.currency to it.amountMinor })
    }
}

object BackupCodec {
    const val FORMAT = "VNventoryBackup"
    const val VERSION = 1
    const val MAX_BYTES = 32 * 1024 * 1024
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(data: BackupData): ByteArray {
        val document = BackupDocument(
            format = FORMAT,
            schemaVersion = VERSION,
            exportedAt = data.exportedAt,
            settings = BackupSettings(data.defaultCurrency),
            orders = data.orders.map { it.toBackup() },
            copies = data.copies.map { it.toBackup() },
            expenses = data.expenses.map { it.toBackup() },
            allocations = data.allocations.map { BackupAllocation(it.expenseId, it.ownedCopyId, it.amountMinor) },
        )
        return json.encodeToString(document).encodeToByteArray().also {
            require(it.size <= MAX_BYTES) { "备份超过 32 MiB，无法导出" }
        }
    }

    fun decode(input: InputStream): BackupData {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(bytes.size().toLong() + count <= MAX_BYTES) { "备份文件超过 32 MiB，无法读取" }
            bytes.write(buffer, 0, count)
        }
        val document = try {
            json.decodeFromString<BackupDocument>(bytes.toByteArray().decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF"))
        } catch (e: SerializationException) {
            throw IllegalArgumentException("不是有效的 VNventory 备份文件，文件可能已损坏", e)
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("备份文件不是有效的 UTF-8 文本", e)
        }
        require(document.format == FORMAT) { "请选择 VNventory 导出的备份文件" }
        require(document.schemaVersion == VERSION) { "不支持此备份版本，请使用兼容的 VNventory 版本恢复" }
        val data = try {
            BackupData(
                exportedAt = document.exportedAt,
                defaultCurrency = document.settings.defaultCurrency,
                orders = document.orders.map { it.toEntity() },
                copies = document.copies.map { it.toEntity() },
                expenses = document.expenses.map { it.toEntity() },
                allocations = document.allocations.map { ExpenseAllocationEntity(it.expenseId, it.ownedCopyId, it.amountMinor) },
            )
        } catch (e: java.time.DateTimeException) {
            throw IllegalArgumentException("备份中的日期无效", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("备份中的品相、费用分类或分摊方式无效", e)
        }
        return data.also { it.validate() }
    }
}

private fun PurchaseOrderEntity.toBackup() = BackupOrder(
    id = id,
    title = title,
    merchant = merchant,
    orderDate = orderDate?.toString(),
    currency = currency,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BackupOrder.toEntity() = PurchaseOrderEntity(
    id = id,
    title = title,
    merchant = merchant,
    orderDate = orderDate?.let(LocalDate::parse),
    currency = currency,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun OwnedCopyEntity.toBackup() = BackupCopy(
    id = id,
    vnId = vnId,
    releaseId = releaseId,
    vnTitle = vnTitle,
    releaseTitle = releaseTitle,
    coverUrl = coverUrl,
    priceMinor = priceMinor,
    currency = currency,
    condition = condition.name,
    conditionNote = conditionNote,
    purchaseDate = purchaseDate?.toString(),
    shop = shop,
    orderId = orderId,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BackupCopy.toEntity() = OwnedCopyEntity(
    id = id,
    vnId = vnId,
    releaseId = releaseId,
    vnTitle = vnTitle,
    releaseTitle = releaseTitle,
    coverUrl = coverUrl,
    priceMinor = priceMinor,
    currency = currency,
    condition = CopyCondition.valueOf(condition),
    conditionNote = conditionNote,
    purchaseDate = purchaseDate?.let(LocalDate::parse),
    shop = shop,
    orderId = orderId,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ExpenseEntity.toBackup() = BackupExpense(
    id = id,
    orderId = orderId,
    name = name,
    category = category.name,
    amountMinor = amountMinor,
    currency = currency,
    mode = mode.name,
    notes = notes,
    createdAt = createdAt,
)

private fun BackupExpense.toEntity() = ExpenseEntity(
    id = id,
    orderId = orderId,
    name = name,
    category = ExpenseCategory.valueOf(category),
    amountMinor = amountMinor,
    currency = currency,
    mode = AllocationMode.valueOf(mode),
    notes = notes,
    createdAt = createdAt,
)
