package com.vnventory.app.data.repository

import androidx.room.withTransaction
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.local.dao.CategoryTotal
import com.vnventory.app.data.local.dao.ExpenseDao
import com.vnventory.app.data.local.dao.OwnedCopyDao
import com.vnventory.app.data.local.dao.PurchaseOrderDao
import com.vnventory.app.data.local.entity.ExpenseAllocationEntity
import com.vnventory.app.data.mapper.toDomain
import com.vnventory.app.data.mapper.toEntity
import com.vnventory.app.domain.cost.CostCopyInput
import com.vnventory.app.domain.cost.CostEngine
import com.vnventory.app.domain.cost.CostExpenseInput
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.Expense
import com.vnventory.app.domain.model.OrderDetail
import com.vnventory.app.domain.model.OrderSummary
import com.vnventory.app.domain.model.PurchaseOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 购买批次（订单）与费用仓库。
 *
 * - 订单详情会实时计算每盒成本（CostEngine），不落库；
 * - MANUAL 分摊的明细落库，其它模式清空明细并实时计算。
 */
class PurchaseRepository(
    private val database: VNventoryDatabase,
    private val orderDao: PurchaseOrderDao,
    private val expenseDao: ExpenseDao,
    private val ownedCopyDao: OwnedCopyDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun observeOrders(): Flow<List<OrderSummary>> = combine(
        orderDao.observeAll(),
        orderDao.observeGoodsTotals(),
        expenseDao.observeOrderFeeTotals(),
    ) { orders, goodsTotals, feeTotals ->
        orders.map { order ->
            val goods = goodsTotals.filter { it.orderId == order.id }
            val fees = feeTotals.filter { it.orderId == order.id }
            OrderSummary(
                order = order.toDomain(),
                copyCount = goods.sumOf { it.copyCount },
                goodsTotals = goods.associate { it.currency to it.total },
                feeTotals = fees.associate { it.currency to it.total },
            )
        }
    }.flowOn(io)

    fun observeOrderDetail(orderId: Long): Flow<OrderDetail?> = combine(
        orderDao.observeById(orderId),
        ownedCopyDao.observeByOrder(orderId),
        expenseDao.observeByOrder(orderId),
        expenseDao.observeAllocationsForOrder(orderId),
    ) { order, copies, expenses, allocations ->
        if (order == null) {
            null
        } else {
            val allocationsByExpense = allocations.groupBy { it.expenseId }
            val domainExpenses = expenses.map { entity ->
                val map = allocationsByExpense[entity.id]
                    .orEmpty()
                    .associate { it.ownedCopyId to it.amountMinor }
                entity.toDomain(map)
            }
            val domainCopies = copies.map { it.toDomain() }
            OrderDetail(
                order = order.toDomain(),
                copies = domainCopies,
                expenses = domainExpenses,
                breakdown = CostEngine.computeOrderCosts(
                    copies = domainCopies.map { CostCopyInput(it.id, it.priceMinor, it.currency) },
                    expenses = domainExpenses.map {
                        CostExpenseInput(
                            expenseId = it.id,
                            name = it.name,
                            category = it.category,
                            amountMinor = it.amountMinor,
                            currency = it.currency,
                            mode = it.mode,
                            manualAllocations = it.allocations,
                        )
                    },
                ),
            )
        }
    }.flowOn(io)

    fun observeExpenseCategoryTotals(): Flow<List<CategoryTotal>> =
        expenseDao.observeCategoryTotals().flowOn(io)

    // ---- 订单 ----

    suspend fun createOrder(order: PurchaseOrder): Long = withContext(io) {
        orderDao.insert(order.copy(id = 0).toEntity())
    }

    suspend fun updateOrder(order: PurchaseOrder) = withContext(io) {
        orderDao.update(order.toEntity())
    }

    /** 删除订单：费用级联删除，收藏盒保留（orderId 置空） */
    suspend fun deleteOrder(orderId: Long) = withContext(io) {
        orderDao.deleteById(orderId)
    }

    suspend fun countCopiesInOrder(orderId: Long): Int =
        withContext(io) { orderDao.countCopies(orderId) }

    suspend fun getOrder(orderId: Long): PurchaseOrder? =
        withContext(io) { orderDao.getById(orderId)?.toDomain() }

    // ---- 费用 ----

    suspend fun addExpense(expense: Expense): Long = withContext(io) {
        database.withTransaction {
            val id = expenseDao.insert(expense.copy(id = 0).toEntity())
            if (expense.mode == AllocationMode.MANUAL) {
                expenseDao.upsertAllocations(expense.allocations.toEntities(id))
            }
            id
        }
    }

    suspend fun updateExpense(expense: Expense) = withContext(io) {
        database.withTransaction {
            expenseDao.update(expense.toEntity())
            expenseDao.clearAllocations(expense.id)
            if (expense.mode == AllocationMode.MANUAL) {
                expenseDao.upsertAllocations(expense.allocations.toEntities(expense.id))
            }
        }
    }

    suspend fun deleteExpense(expenseId: Long) = withContext(io) {
        expenseDao.deleteById(expenseId)
    }

    suspend fun getExpense(expenseId: Long): Expense? = withContext(io) {
        val entity = expenseDao.getById(expenseId) ?: return@withContext null
        val allocations = expenseDao.observeAllocations(expenseId).first()
            .associate { it.ownedCopyId to it.amountMinor }
        entity.toDomain(allocations)
    }

    /** 手动分摊：整体替换某费用的明细 */
    suspend fun setManualAllocations(expenseId: Long, allocations: Map<Long, Long>) = withContext(io) {
        database.withTransaction {
            expenseDao.clearAllocations(expenseId)
            expenseDao.upsertAllocations(allocations.toEntities(expenseId))
        }
    }

    /** 把若干盒加入/移出某订单（orderId = null 表示移出），并清理失效的手动分摊 */
    suspend fun assignCopiesToOrder(copyIds: List<Long>, orderId: Long?) = withContext(io) {
        database.withTransaction {
            copyIds.forEach { copyId ->
                val copy = ownedCopyDao.getById(copyId) ?: return@forEach
                ownedCopyDao.update(copy.copy(orderId = orderId, updatedAt = System.currentTimeMillis()))
                expenseDao.pruneAllocationsForCopy(copyId)
            }
        }
    }

    private fun Map<Long, Long>.toEntities(expenseId: Long): List<ExpenseAllocationEntity> =
        map { (copyId, amount) -> ExpenseAllocationEntity(expenseId, copyId, amount) }
}
