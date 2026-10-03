package com.vnventory.app.ui.preview

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vnventory.app.ui.collection.CollectionContent
import com.vnventory.app.ui.collection.CollectionUiState
import com.vnventory.app.ui.detail.CopyDetailContent
import com.vnventory.app.ui.detail.CopyDetailUiState
import com.vnventory.app.ui.home.HomeContent
import com.vnventory.app.domain.model.VnInfo
import com.vnventory.app.ui.add.AddFlowUiState
import com.vnventory.app.ui.add.PurchaseFormContent
import com.vnventory.app.ui.add.PurchaseFormState
import com.vnventory.app.ui.theme.VNventoryTheme

@Preview(name = "首页 · 浅色", widthDp = 393, heightDp = 852, showBackground = true)
@Preview(name = "首页 · 深色", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun HomePreview() {
    VNventoryTheme { Surface { HomeContent(ShelfPreviewData.stats, ShelfPreviewData.copies, {}, {}, {}) } }
}

@Preview(name = "封面书架", widthDp = 393, heightDp = 852, showBackground = true)
@Preview(name = "书架 · 大字体", widthDp = 393, heightDp = 852, fontScale = 1.5f, showBackground = true)
@Composable
private fun CollectionPreview() {
    VNventoryTheme { Surface { CollectionContent(CollectionUiState(copies = ShelfPreviewData.copies, loading = false), {}, {}, {}, {}) } }
}

@Preview(name = "收藏档案", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun DetailPreview() {
    VNventoryTheme { Surface { CopyDetailContent(CopyDetailUiState(loading = false, copy = ShelfPreviewData.copies.first()), {}) } }
}

@Preview(name = "记录购入", widthDp = 393, heightDp = 852, showBackground = true)
@Preview(name = "购入 · 大字体", widthDp = 393, heightDp = 852, fontScale = 1.5f, showBackground = true)
@Composable
private fun PurchasePreview() {
    val copy = ShelfPreviewData.copies.first()
    val state = AddFlowUiState(
        selectedVn = VnInfo(copy.vnId, "Hoshi Furu Yoru no Kioku", copy.vnTitle, null, copy.coverUrl, null),
        form = PurchaseFormState(manualVersion = true, releaseTitle = "初回限定版", currency = "JPY", priceText = "6800"),
    )
    VNventoryTheme { Surface { PurchaseFormContent(state, {}) } }
}
