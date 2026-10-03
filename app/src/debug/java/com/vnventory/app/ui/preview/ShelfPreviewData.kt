package com.vnventory.app.ui.preview

import com.vnventory.app.R
import com.vnventory.app.BuildConfig
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.OwnedCopy
import com.vnventory.app.ui.home.HomeStats
import java.time.LocalDate

/** 纯展示样本，从不写入 Room，不从网络下载图片。海报为项目原创矢量资源。 */
object ShelfPreviewData {
    val copies = listOf(
        sample(101, "星降る夜の記憶", "初回限定版 · PC", R.drawable.preview_cover_night, 6800, CopyCondition.UNOPENED, "r1"),
        sample(102, "星降る夜の記憶", "初回限定版 · PC", R.drawable.preview_cover_sunset, 5200, CopyCondition.USED, "r1"),
        sample(103, "春を待つ手紙", "特典同梱版 · PC", R.drawable.preview_cover_spring, 7400, CopyCondition.NEW, null),
    )
    val stats = HomeStats(vnCount = 2, copyCount = 3, priceTotals = mapOf("JPY" to 19400), shippingTotals = mapOf("CNY" to 12800))

    private fun sample(id: Long, title: String, release: String, art: Int, price: Long, condition: CopyCondition, releaseId: String?) = OwnedCopy(
        id, if (id == 103L) "v2" else "v1", releaseId, title, release,
        "android.resource://${BuildConfig.APPLICATION_ID}/$art", price, "JPY", condition, null,
        LocalDate.of(2026, 9, id.toInt() - 100), "本地展示样本", null, "海报与记录仅用于 UI 预览。", 0, 0,
    )
}
