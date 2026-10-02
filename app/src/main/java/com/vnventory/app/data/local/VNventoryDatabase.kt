package com.vnventory.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vnventory.app.data.local.dao.ExpenseDao
import com.vnventory.app.data.local.dao.OwnedCopyDao
import com.vnventory.app.data.local.dao.PurchaseOrderDao
import com.vnventory.app.data.local.dao.VnCacheDao
import com.vnventory.app.data.local.entity.ExpenseAllocationEntity
import com.vnventory.app.data.local.entity.ExpenseEntity
import com.vnventory.app.data.local.entity.OwnedCopyEntity
import com.vnventory.app.data.local.entity.PurchaseOrderEntity
import com.vnventory.app.data.local.entity.ReleaseCacheEntity
import com.vnventory.app.data.local.entity.VnCacheEntity

/**
 * 本地数据库（唯一事实来源）。
 *
 * 关系速览：
 * ```
 * purchase_order 1 ── n owned_copy        （删订单：owned_copy 保留，orderId 置空）
 * purchase_order 1 ── n expense           （删订单：expense 级联删除）
 * expense        1 ── n expense_allocation（仅 MANUAL 模式有行）
 * owned_copy     1 ── n expense_allocation（删盒：分摊行级联删除）
 * vn_cache       1 ── n release_cache     （纯缓存，可整体清空）
 * ```
 */
@Database(
    entities = [
        VnCacheEntity::class,
        ReleaseCacheEntity::class,
        OwnedCopyEntity::class,
        PurchaseOrderEntity::class,
        ExpenseEntity::class,
        ExpenseAllocationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VNventoryDatabase : RoomDatabase() {

    abstract fun vnCacheDao(): VnCacheDao
    abstract fun ownedCopyDao(): OwnedCopyDao
    abstract fun purchaseOrderDao(): PurchaseOrderDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        private const val DB_NAME = "vnventory.db"

        fun build(context: Context): VNventoryDatabase =
            Room.databaseBuilder(context, VNventoryDatabase::class.java, DB_NAME)
                .build()
    }
}
