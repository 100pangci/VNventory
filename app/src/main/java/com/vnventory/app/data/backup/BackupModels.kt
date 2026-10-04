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
import com.vnventory.app.domain.model.ShopChannels
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import com.vnventory.app.domain.text.MessageException
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.domain.text.requireMessage

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
data class BackupSettings(
    val defaultCurrency: String,
    val shopChannels: List<String>? = null,
    val showShelfPrices: Boolean = false,
    val showPriceStats: Boolean = false,
)

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
    val priceMinor: Long?,
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
    /** Null means an older backup has no such preference; restoring it must not erase local candidates. */
    val shopChannels: List<String>? = null,
    val showShelfPrices: Boolean = false,
    val showPriceStats: Boolean = false,
) {
    /** 解码时及正式写事务内都校验，禁止部分导入、猜测修复或静默丢弃坏记录。 */
    fun validate() {
        LocalRules.currency(defaultCurrency)
        shopChannels?.let(ShopChannels::validate)
        fun ids(values: List<Long>) {
            requireMessage(values.all { it > 0 } && values.distinct().size == values.size) { message(MessageKey.BACKUP_IDS_INVALID) }
        }
        ids(orders.map { it.id })
        ids(copies.map { it.id })
        ids(expenses.map { it.id })
        val orderIds = orders.map { it.id }.toSet()
        val expensesById = expenses.associateBy { it.id }
        val copiesById = copies.associateBy { it.id }
        orders.forEach {
            requireMessage(it.title.isNotBlank()) { message(MessageKey.BACKUP_ORDER_TITLE_EMPTY) }
            LocalRules.currency(it.currency)
        }
        copies.forEach {
            LocalRules.copy(it.toDomain())
            requireMessage(it.releaseId == null || it.releaseId.matches(Regex("r[1-9][0-9]*"))) { message(MessageKey.BACKUP_RELEASE_ID_INVALID) }
            requireMessage(it.orderId == null || it.orderId in orderIds) { message(MessageKey.BACKUP_COPY_ORDER_MISSING) }
        }
        requireMessage(allocations.map { it.expenseId to it.ownedCopyId }.distinct().size == allocations.size) { message(MessageKey.BACKUP_ALLOCATION_DUPLICATE) }
        allocations.forEach {
            val expense = expensesById[it.expenseId]
            requireMessage(expense != null && expense.mode == AllocationMode.MANUAL) { message(MessageKey.BACKUP_ALLOCATION_NOT_MANUAL) }
            val copy = copiesById[it.ownedCopyId]
            requireMessage(copy != null && copy.orderId == expense.orderId) { message(MessageKey.BACKUP_ALLOCATION_COPY_MISMATCH) }
            requireMessage(it.amountMinor >= 0) { message(MessageKey.BACKUP_ALLOCATION_NEGATIVE) }
        }
        val copiesByOrder = copies.groupBy { it.orderId }
        val allocationsByExpense = allocations.groupBy { it.expenseId }
        expenses.forEach {
            requireMessage(it.orderId in orderIds) { message(MessageKey.BACKUP_EXPENSE_ORDER_MISSING) }
            requireMessage(it.name.isNotBlank()) { message(MessageKey.BACKUP_EXPENSE_NAME_EMPTY) }
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
            // 缺失价格可让既有比例分摊暂时失效；恢复购买事实，不猜价格。
            requireMessage(problem == null || problem == message(MessageKey.ALLOCATION_PRICE_MISSING)) { problem!! }
        }
        Money.totals(copies.mapNotNull { copy -> copy.priceMinor?.let { copy.currency to it } } + expenses.map { it.currency to it.amountMinor })
    }
}

object BackupCodec {
    const val FORMAT = "VNventoryBackup"
    const val VERSION = 2
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
            settings = BackupSettings(data.defaultCurrency, data.shopChannels, data.showShelfPrices, data.showPriceStats),
            orders = data.orders.map { it.toBackup() },
            copies = data.copies.map { it.toBackup() },
            expenses = data.expenses.map { it.toBackup() },
            allocations = data.allocations.map { BackupAllocation(it.expenseId, it.ownedCopyId, it.amountMinor) },
        )
        return json.encodeToString(document).encodeToByteArray().also {
            requireMessage(it.size <= MAX_BYTES) { message(MessageKey.BACKUP_EXPORT_TOO_LARGE) }
        }
    }

    fun decode(input: InputStream): BackupData {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            requireMessage(bytes.size().toLong() + count <= MAX_BYTES) { message(MessageKey.BACKUP_IMPORT_TOO_LARGE) }
            bytes.write(buffer, 0, count)
        }
        val document = try {
            json.decodeFromString<BackupDocument>(bytes.toByteArray().decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF"))
        } catch (e: SerializationException) {
            throw MessageException(message(MessageKey.BACKUP_INVALID_FILE), e)
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw MessageException(message(MessageKey.BACKUP_INVALID_UTF8), e)
        }
        requireMessage(document.format == FORMAT) { message(MessageKey.BACKUP_WRONG_FORMAT) }
        requireMessage(document.schemaVersion in 1..VERSION) { message(MessageKey.BACKUP_UNSUPPORTED_VERSION) }
        val data = try {
            BackupData(
                exportedAt = document.exportedAt,
                defaultCurrency = document.settings.defaultCurrency,
                orders = document.orders.map { it.toEntity() },
                copies = document.copies.map { it.toEntity() },
                expenses = document.expenses.map { it.toEntity() },
                allocations = document.allocations.map { ExpenseAllocationEntity(it.expenseId, it.ownedCopyId, it.amountMinor) },
                shopChannels = document.settings.shopChannels,
                showShelfPrices = document.settings.showShelfPrices,
                showPriceStats = document.settings.showPriceStats,
            )
        } catch (e: java.time.DateTimeException) {
            throw MessageException(message(MessageKey.BACKUP_INVALID_DATE), e)
        } catch (e: IllegalArgumentException) {
            throw MessageException(message(MessageKey.BACKUP_INVALID_ENUM), e)
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
