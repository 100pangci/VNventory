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
    @Test fun `真实v1经v2到v3保留全部事实`() = verifyMigration(1)
    @Test fun `真实v2到v3保留零价分摊与自增序列`() = verifyMigration(2)
    @Test fun `已清空收藏的v2仍保留自增序列`() = verifyMigration(2, empty = true)

    private fun verifyMigration(version: Int, empty: Boolean = false) = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-review-$version-$empty.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = javaClass.classLoader!!.getResourceAsStream("com.vnventory.app.data.local.VNventoryDatabase/$version.json")!!
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
            if (version == 1) old.execSQL("INSERT INTO release_cache VALUES ('r1','v1','A+B',NULL,'win','ja','Pub',NULL,NULL,NULL,NULL,0)")
            else {
                old.execSQL("INSERT INTO release_cache VALUES ('r1','A+B',NULL,'win','ja','Pub',NULL,NULL,NULL,NULL,0)")
                old.execSQL("INSERT INTO release_vn VALUES ('v1','r1')")
            }
            old.execSQL("INSERT INTO purchase_order VALUES (1,'Batch',NULL,NULL,'JPY',NULL,0,0)")
            old.execSQL("""INSERT INTO owned_copy VALUES (1,'v1','r1','A','A+B',NULL,5000,'JPY','USED',NULL,'2026-10-01','Shop',1,'keep',0,0)""")
            old.execSQL("INSERT INTO expense VALUES (1,1,'Ship','SHIPPING',100,'JPY','MANUAL',NULL,0)")
            old.execSQL("INSERT INTO expense_allocation VALUES (1,1,100)")
            old.execSQL("""INSERT INTO owned_copy SELECT 2,vnId,releaseId,vnTitle,releaseTitle,coverUrl,0,currency,condition,conditionNote,purchaseDate,shop,orderId,notes,createdAt,updatedAt FROM owned_copy WHERE id=1""")
            old.execSQL("UPDATE sqlite_sequence SET seq=100 WHERE name='owned_copy'")
            if (empty) {
                old.execSQL("DELETE FROM expense_allocation")
                old.execSQL("DELETE FROM owned_copy")
            }
            old.version = version
        }
        val db = Room.databaseBuilder(context, VNventoryDatabase::class.java, name)
            .addMigrations(VNventoryDatabase.MIGRATION_1_2, VNventoryDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            if (empty) {
                db.openHelper.writableDatabase.execSQL("""INSERT INTO owned_copy(vnId,vnTitle,releaseTitle,priceMinor,currency,condition,createdAt,updatedAt) VALUES ('v1','A','Manual',NULL,'JPY','USED',0,0)""")
                val inserted = db.ownedCopyDao().getAll().single()
                assertTrue(inserted.id > 100)
                assertNull(inserted.priceMinor)
                return@runTest
            }
            // Room validates the entire migrated schema as it opens this database.
            val copy = db.ownedCopyDao().getById(1)!!
            assertEquals(5000L, copy.priceMinor)
            assertEquals("keep", copy.notes)
            assertEquals(1L, copy.orderId)
            assertEquals(100L, db.expenseDao().getAllocations(1).single().amountMinor)
            assertEquals(0L, db.ownedCopyDao().getById(2)!!.priceMinor)
            val nullId = db.ownedCopyDao().insert(copy.copy(id = 0, priceMinor = null))
            assertTrue(nullId > 100)
            assertNull(db.ownedCopyDao().getById(nullId)!!.priceMinor)
            assertEquals(100L, db.expenseDao().getAllocations(1).single().amountMinor)
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
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
