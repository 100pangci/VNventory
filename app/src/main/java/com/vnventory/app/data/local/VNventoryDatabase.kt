package com.vnventory.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vnventory.app.data.local.dao.BackupDao
import com.vnventory.app.data.local.dao.ExpenseDao
import com.vnventory.app.data.local.dao.OwnedCopyDao
import com.vnventory.app.data.local.dao.PurchaseOrderDao
import com.vnventory.app.data.local.dao.VnCacheDao
import com.vnventory.app.data.local.entity.ExpenseAllocationEntity
import com.vnventory.app.data.local.entity.ExpenseEntity
import com.vnventory.app.data.local.entity.OwnedCopyEntity
import com.vnventory.app.data.local.entity.PurchaseOrderEntity
import com.vnventory.app.data.local.entity.ReleaseCacheEntity
import com.vnventory.app.data.local.entity.ReleaseVnEntity
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
 * vn_cache       n ── n release_cache     （通过 release_vn；纯缓存）
 * ```
 */
@Database(
    entities = [
        VnCacheEntity::class,
        ReleaseCacheEntity::class,
        ReleaseVnEntity::class,
        OwnedCopyEntity::class,
        PurchaseOrderEntity::class,
        ExpenseEntity::class,
        ExpenseAllocationEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VNventoryDatabase : RoomDatabase() {

    abstract fun vnCacheDao(): VnCacheDao
    abstract fun ownedCopyDao(): OwnedCopyDao
    abstract fun purchaseOrderDao(): PurchaseOrderDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun backupDao(): BackupDao

    companion object {
        private const val DB_NAME = "vnventory.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 保留元数据和已知关联；用户购买事实表不做删改。
                db.execSQL("CREATE TABLE release_links_temp (vnId TEXT NOT NULL, releaseId TEXT NOT NULL)")
                db.execSQL("INSERT INTO release_links_temp SELECT vnId, vndbId FROM release_cache WHERE vnId IS NOT NULL")
                db.execSQL("""CREATE TABLE release_cache_new (
                    vndbId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, released TEXT,
                    platforms TEXT NOT NULL, languages TEXT NOT NULL, publishers TEXT NOT NULL,
                    jan TEXT, minAge INTEGER, official INTEGER, packagingImageUrl TEXT, fetchedAt INTEGER NOT NULL
                )""")
                db.execSQL("""INSERT INTO release_cache_new SELECT vndbId,title,released,platforms,languages,
                    publishers,jan,minAge,official,packagingImageUrl,fetchedAt FROM release_cache""")
                db.execSQL("DROP TABLE release_cache")
                db.execSQL("ALTER TABLE release_cache_new RENAME TO release_cache")
                db.execSQL("""CREATE TABLE release_vn (
                    vnId TEXT NOT NULL, releaseId TEXT NOT NULL, PRIMARY KEY(vnId,releaseId),
                    FOREIGN KEY(vnId) REFERENCES vn_cache(vndbId) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(releaseId) REFERENCES release_cache(vndbId) ON UPDATE NO ACTION ON DELETE CASCADE
                )""")
                db.execSQL("CREATE INDEX index_release_vn_releaseId ON release_vn(releaseId)")
                db.execSQL("INSERT INTO release_vn SELECT vnId,releaseId FROM release_links_temp")
                db.execSQL("DROP TABLE release_links_temp")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // DROP 的外键级联会删除手动分摊；先完整保存，重建后恢复。
                db.execSQL("CREATE TEMP TABLE allocations_backup AS SELECT * FROM expense_allocation")
                db.execSQL("CREATE TEMP TABLE copy_sequence AS SELECT seq FROM sqlite_sequence WHERE name = 'owned_copy'")
                db.execSQL("""CREATE TABLE owned_copy_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, vnId TEXT NOT NULL, releaseId TEXT,
                    vnTitle TEXT NOT NULL, releaseTitle TEXT, coverUrl TEXT, priceMinor INTEGER,
                    currency TEXT NOT NULL, condition TEXT NOT NULL, conditionNote TEXT, purchaseDate TEXT,
                    shop TEXT, orderId INTEGER, notes TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(orderId) REFERENCES purchase_order(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )""")
                db.execSQL("INSERT INTO owned_copy_new SELECT * FROM owned_copy")
                db.execSQL("DROP TABLE owned_copy")
                db.execSQL("ALTER TABLE owned_copy_new RENAME TO owned_copy")
                listOf("vnId", "releaseId", "orderId", "purchaseDate").forEach {
                    db.execSQL("CREATE INDEX index_owned_copy_$it ON owned_copy($it)")
                }
                db.execSQL("UPDATE sqlite_sequence SET seq = MAX(seq, COALESCE((SELECT seq FROM copy_sequence), 0)) WHERE name = 'owned_copy'")
                db.execSQL("INSERT INTO sqlite_sequence(name, seq) SELECT 'owned_copy', seq FROM copy_sequence WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'owned_copy')")
                db.execSQL("INSERT OR REPLACE INTO expense_allocation SELECT * FROM allocations_backup")
                db.execSQL("DROP TABLE allocations_backup")
                db.execSQL("DROP TABLE copy_sequence")
            }
        }

        fun build(context: Context): VNventoryDatabase =
            Room.databaseBuilder(context, VNventoryDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
