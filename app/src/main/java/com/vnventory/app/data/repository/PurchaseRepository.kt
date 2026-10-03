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
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.Expense
import com.vnventory.app.domain.model.OrderDetail
import com.vnventory.app.domain.model.OrderSummary
import com.vnventory.app.domain.model.PurchaseOrder
import com.vnventory.app.domain.model.costInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    fun observeOrderDetail(orderId: Long): Flow<OrderDetail?> = orderDao.observeGraph(orderId).map { graph ->
        graph?.let { snapshot ->
            val copies = snapshot.copies
                .sortedWith(compareBy({ it.createdAt }, { it.id }))
                .map { it.toDomain() }
            val expenses = snapshot.expenses
                .sortedWith(compareBy({ it.expense.createdAt }, { it.expense.id }))
                .map { it.expense.toDomain(it.allocations.associate { allocation -> allocation.ownedCopyId to allocation.amountMinor }) }
            OrderDetail(
                order = snapshot.order.toDomain(),
                copies = copies,
                expenses = expenses,
                breakdown = CostEngine.computeOrderCosts(
                    copies.map { it.costInput() },
                    expenses.map { it.costInput() },
                ),
            )
        }
    }.flowOn(io)

    fun observeExpenseCategoryTotals(): Flow<List<CategoryTotal>> =
        expenseDao.observeCategoryTotals().flowOn(io)

    // ---- 订单 ----

    suspend fun createOrder(order: PurchaseOrder): Long = withContext(io) {
        require(order.title.isNotBlank()) { "订单名称不能为空" }
        LocalRules.currency(order.currency)
        orderDao.insert(order.copy(id = 0).toEntity())
    }

    suspend fun updateOrder(order: PurchaseOrder) = withContext(io) {
        require(order.title.isNotBlank()) { "订单名称不能为空" }
        LocalRules.currency(order.currency)
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
            validateExpense(expense)
            val id = expenseDao.insert(expense.copy(id = 0).toEntity())
            if (expense.mode == AllocationMode.MANUAL) {
                expenseDao.upsertAllocations(expense.allocations.toEntities(id))
            }
            LocalRules.totals(database)
            id
        }
    }

    suspend fun updateExpense(expense: Expense) = withContext(io) {
        database.withTransaction {
            val old = requireNotNull(expenseDao.getById(expense.id)) { "费用已不存在" }
            require(old.orderId == expense.orderId) { "不能将费用直接转移到其他订单" }
            validateExpense(expense)
            expenseDao.update(expense.toEntity())
            expenseDao.clearAllocations(expense.id)
            if (expense.mode == AllocationMode.MANUAL) {
                expenseDao.upsertAllocations(expense.allocations.toEntities(expense.id))
            }
            LocalRules.totals(database)
        }
    }

    suspend fun deleteExpense(expenseId: Long) = withContext(io) {
        expenseDao.deleteById(expenseId)
    }

    suspend fun getExpense(expenseId: Long): Expense? = withContext(io) {
        database.withTransaction {
            val entity = expenseDao.getById(expenseId) ?: return@withTransaction null
            val allocations = expenseDao.getAllocations(expenseId).associate { it.ownedCopyId to it.amountMinor }
            entity.toDomain(allocations)
        }
    }

    /** 手动分摊：整体替换某费用的明细 */
    suspend fun setManualAllocations(expenseId: Long, allocations: Map<Long, Long>) = withContext(io) {
        database.withTransaction {
            val entity = requireNotNull(expenseDao.getById(expenseId)) { "费用已不存在" }
            require(entity.mode == AllocationMode.MANUAL) { "该费用不是手动分摊模式" }
            validateExpense(entity.toDomain(allocations))
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
            LocalRules.order(database, orderId)
        }
    }

    private suspend fun validateExpense(expense: Expense) {
        require(orderDao.getById(expense.orderId) != null) { "订单已不存在" }
        require(expense.name.isNotBlank()) { "费用名称不能为空" }
        LocalRules.currency(expense.currency)
        val copies = ownedCopyDao.getByOrder(expense.orderId).map { CostCopyInput(it.id, it.priceMinor, it.currency) }
        val problem = CostEngine.expenseProblem(expense.costInput(), copies)
        require(problem == null) { problem!! }
    }

    private fun Map<Long, Long>.toEntities(expenseId: Long): List<ExpenseAllocationEntity> =
        map { (copyId, amount) -> ExpenseAllocationEntity(expenseId, copyId, amount) }
}
