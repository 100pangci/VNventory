package com.vnventory.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.local.entity.ReleaseVnEntity
import com.vnventory.app.data.local.entity.VnCacheEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test fun `v1升级保留购买记录分摊与缓存并支持合辑多VN`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-review.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = javaClass.classLoader!!.getResourceAsStream("com.vnventory.app.data.local.VNventoryDatabase/1.json")!!
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject.getValue("database").jsonObject }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            schema.getValue("entities").jsonArray.forEach { value ->
                val entity = value.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                old.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    old.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { old.execSQL(it.jsonPrimitive.content) }
            old.execSQL("INSERT INTO vn_cache VALUES ('v1','A',NULL,NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO release_cache VALUES ('r1','v1','A+B',NULL,'win','ja','Pub',NULL,NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO purchase_order VALUES (1,'Batch',NULL,NULL,'JPY',NULL,0,0)")
            old.execSQL("""INSERT INTO owned_copy VALUES (1,'v1','r1','A','A+B',NULL,5000,'JPY','USED',NULL,'2026-10-01','Shop',1,'keep',0,0)""")
            old.execSQL("INSERT INTO expense VALUES (1,1,'Ship','SHIPPING',100,'JPY','MANUAL',NULL,0)")
            old.execSQL("INSERT INTO expense_allocation VALUES (1,1,100)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, VNventoryDatabase::class.java, name)
            .addMigrations(VNventoryDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            // Room validates the entire migrated schema as it opens this database.
            val copy = db.ownedCopyDao().getById(1)!!
            assertEquals(5000L, copy.priceMinor)
            assertEquals("keep", copy.notes)
            assertEquals(1L, copy.orderId)
            assertEquals(100L, db.expenseDao().getAllocations(1).single().amountMinor)
            assertTrue(db.vnCacheDao().isLinked("v1", "r1"))
            db.vnCacheDao().upsertVn(VnCacheEntity("v2", "B", null, null, null, null, 0))
            db.vnCacheDao().upsertLinks(listOf(ReleaseVnEntity("v2", "r1")))
            assertEquals("r1", db.vnCacheDao().observeReleases("v1").first().single().vndbId)
            assertEquals("r1", db.vnCacheDao().observeReleases("v2").first().single().vndbId)
            db.vnCacheDao().clearVnCache()
            db.vnCacheDao().clearReleaseCache()
            assertEquals(copy, db.ownedCopyDao().getById(1))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
