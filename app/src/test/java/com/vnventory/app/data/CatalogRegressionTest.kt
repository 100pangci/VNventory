package com.vnventory.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.core.AppResult
import com.vnventory.app.data.local.VNventoryDatabase
import com.vnventory.app.data.mapper.toDomain
import com.vnventory.app.data.repository.VnRepository
import com.vnventory.app.data.repository.CollectionRepository
import com.vnventory.app.data.remote.vndb.*
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.OwnedCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

open class FakeVndb : VndbService {
    override suspend fun getVn(vnId: String) = VndbVnDto(vnId, title = vnId)
    override suspend fun searchVn(query: String, page: Int, results: Int) = VndbVnResponse(listOf(VndbVnDto("v$page", title = query)), more = page < 2)
    override suspend fun getReleases(vnId: String, page: Int, results: Int) = VndbReleaseResponse(listOf(VndbReleaseDto("r1", title = "Bundle")))
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogRegressionTest {
    private lateinit var db: VNventoryDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VNventoryDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    @Test fun `读取所有页去重且一次性替换当前VN缓存`() = runTest {
        val pages = mutableListOf<Int>()
        val api = object : FakeVndb() {
            override suspend fun getReleases(vnId: String, page: Int, results: Int): VndbReleaseResponse {
                pages += page
                return if (page == 1) VndbReleaseResponse((1..100).map { VndbReleaseDto("r$it", title = "R$it") }, more = true)
                else VndbReleaseResponse(listOf(VndbReleaseDto("r100"), VndbReleaseDto("r101")))
            }
        }
        val repo = VnRepository(api, db.vnCacheDao(), Dispatchers.Unconfined)
        val result = repo.fetchReleases("v1") as AppResult.Success
        assertEquals(101, result.data.size)
        assertEquals(listOf(1,2), pages)
        assertEquals(101, repo.observeCachedReleases("v1").first().size)
    }

    @Test fun `后续页失败不破坏旧缓存且取消不变成离线成功`() = runTest {
        val good = VnRepository(FakeVndb(), db.vnCacheDao(), Dispatchers.Unconfined)
        good.fetchReleases("v1")
        val bad = VnRepository(object : FakeVndb() {
            override suspend fun getReleases(vnId: String, page: Int, results: Int): VndbReleaseResponse {
                if (page == 2) throw java.io.IOException("network")
                return VndbReleaseResponse(listOf(VndbReleaseDto("r2")), more = true)
            }
            override suspend fun searchVn(query: String, page: Int, results: Int): VndbVnResponse = throw CancellationException()
        }, db.vnCacheDao(), Dispatchers.Unconfined)
        assertTrue(bad.fetchReleases("v1") is AppResult.Failure)
        assertEquals("r1", bad.observeCachedReleases("v1").first().single().id)
        try { bad.searchVn("v1"); fail("取消不得被吞掉") } catch (_: CancellationException) { }
    }

    @Test fun `同合辑先后浏览不同VN不会覆盖关联`() = runTest {
        val repo = VnRepository(FakeVndb(), db.vnCacheDao(), Dispatchers.Unconfined)
        repo.fetchReleases("v1")
        repo.fetchReleases("v2")
        assertEquals("r1", repo.observeCachedReleases("v1").first().single().id)
        assertEquals("r1", repo.observeCachedReleases("v2").first().single().id)
        assertEquals("v1", repo.getCachedRelease("r1", "v1")!!.vnId)
    }

    @Test fun `联网和缓存选择列表隐藏非官方但保留旧收藏与元数据`() = runTest {
        val api = object : FakeVndb() {
            override suspend fun getReleases(vnId: String, page: Int, results: Int) = VndbReleaseResponse(listOf(
                VndbReleaseDto("r1", title = "Official", official = true),
                VndbReleaseDto("r2", title = "Unofficial", official = false),
                VndbReleaseDto("r3", title = "Legacy", official = null),
            ))
        }
        val repo = VnRepository(api, db.vnCacheDao(), Dispatchers.Unconfined)
        val result = repo.fetchReleases("v1") as AppResult.Success
        assertEquals(listOf("r1", "r3"), result.data.map { it.id })
        assertEquals(setOf("r1", "r3"), repo.observeCachedReleases("v1").first().map { it.id }.toSet())
        val collection = CollectionRepository(db, db.ownedCopyDao(), db.vnCacheDao(), db.expenseDao(), Dispatchers.Unconfined)
        val old = OwnedCopy(0, "v1", "r2", "旧标题快照", "旧版本快照", null, 100, "JPY", CopyCondition.USED, null, null, null, null, null, 0, 0)
        val id = collection.addCopies(listOf(old)).single()
        repo.fetchReleases("v1")
        assertEquals(old.copy(id = id), collection.getById(id))
        assertEquals(false, repo.getCachedRelease("r2", "v1")!!.official)
    }

    @Test fun `第一页全是非官方也能继续加载下一页官方版本`() = runTest {
        val repo = VnRepository(object : FakeVndb() {
            override suspend fun getReleases(vnId: String, page: Int, results: Int) =
                if (page == 1) VndbReleaseResponse(listOf(VndbReleaseDto("r1", official = false)), more = true)
                else VndbReleaseResponse(listOf(VndbReleaseDto("r2", official = true)))
        }, db.vnCacheDao(), Dispatchers.Unconfined)
        val result = repo.fetchReleases("v1") as AppResult.Success
        assertEquals("r2", result.data.single().id)
    }

    @Test fun `旧缓存离线搜索仍默认显示日语且保留罗马字检索`() = runTest {
        val vn = VndbVnDto("v1", title = "Romanized", alttitle = "日本語の題名")
        val repo = VnRepository(object : FakeVndb() {
            override suspend fun getVn(vnId: String) = vn
            override suspend fun searchVn(query: String, page: Int, results: Int): VndbVnResponse = throw java.io.IOException("offline")
        }, db.vnCacheDao(), Dispatchers.Unconfined)
        repo.fetchVn("v1")
        for (query in listOf("Romanized", "日本語")) {
            val result = repo.searchVn(query) as AppResult.Success
            assertTrue(result.data.offline)
            assertEquals("日本語の題名", result.data.items.single().displayTitle)
            assertEquals("Romanized", result.data.items.single().secondaryTitle)
        }
    }
}
