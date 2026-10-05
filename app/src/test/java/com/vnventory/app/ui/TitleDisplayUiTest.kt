package com.vnventory.app.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.vnventory.app.data.mapper.toDomain
import com.vnventory.app.data.remote.vndb.VndbVnDto
import com.vnventory.app.data.remote.vndb.VndbReleaseDto
import com.vnventory.app.domain.model.*
import com.vnventory.app.ui.add.VnSearchRow
import com.vnventory.app.ui.add.ReleaseRow
import com.vnventory.app.ui.add.PurchaseFormContent
import com.vnventory.app.ui.add.AddFlowUiState
import com.vnventory.app.ui.add.PurchaseFormState
import com.vnventory.app.ui.add.ReleasesUiState
import com.vnventory.app.ui.collection.CollectionContent
import com.vnventory.app.ui.collection.CollectionUiState
import com.vnventory.app.ui.detail.CopyDetailContent
import com.vnventory.app.ui.detail.CopyDetailUiState
import com.vnventory.app.ui.edit.CopyEditContent
import com.vnventory.app.ui.edit.CopyEditUiState
import com.vnventory.app.ui.home.HomeContent
import com.vnventory.app.ui.home.HomeStats
import com.vnventory.app.ui.preview.ShelfPreviewData
import com.vnventory.app.ui.text.LocalTitleDisplayMode
import com.vnventory.app.ui.theme.VNventoryTheme
import com.vnventory.app.ui.orders.OrderDetailContent
import com.vnventory.app.ui.orders.ExpenseEditorContent
import com.vnventory.app.ui.orders.OrderDetailUiState
import com.vnventory.app.ui.orders.ExpenseEditorState
import com.vnventory.app.domain.cost.CostEngine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-xxhdpi")
class TitleDisplayUiTest {
    @get:Rule val compose = createComposeRule()
    private val mode = mutableStateOf(TitleDisplayMode.ORIGINAL)
    private val releaseNames = mutableStateOf(false)
    private val copy = ShelfPreviewData.copies.first().copy(
        vnOriginalTitle = "サクラノ詩", vnRomanizedTitle = "Sakura no Uta",
        releaseOriginalTitle = "初回版", releaseRomanizedTitle = "First Edition",
    )
    private val vn = VndbVnDto(copy.vnId, "Sakura no Uta", "サクラノ詩").toDomain().copy(imageUrl = copy.coverUrl)
    private val release = VndbReleaseDto("r1", "First Edition", "初回版").toDomain(copy.vnId)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalTitleDisplayMode provides mode.value) {
                VNventoryTheme { Surface { content() } }
            }
        }
    }

    private fun switch() { compose.runOnIdle { mode.value = TitleDisplayMode.ROMANIZED } }

    private fun assertShelfSwitch() {
        compose.onNodeWithText("サクラノ詩").assertIsDisplayed()
        compose.onNodeWithText("初回版").assertDoesNotExist()
        switch()
        compose.onNodeWithText("Sakura no Uta").assertIsDisplayed()
        compose.onNodeWithText("サクラノ詩").assertDoesNotExist()
        compose.onNodeWithText("First Edition").assertDoesNotExist()
        compose.runOnIdle { releaseNames.value = true }
        compose.onNodeWithText("First Edition").assertIsDisplayed()
        compose.runOnIdle { mode.value = TitleDisplayMode.ORIGINAL }
        compose.onNodeWithText("初回版").assertIsDisplayed()
        compose.onNodeWithText("First Edition").assertDoesNotExist()
    }

    @Test fun `grid switches immediately with release visibility independent`() {
        show { CollectionContent(CollectionUiState(copies = listOf(copy), loading = false, showReleaseNames = releaseNames.value), {}, {}, {}, {}) }
        assertShelfSwitch()
    }

    @Test fun `list switches immediately without changing its metadata layout`() {
        show { CollectionContent(CollectionUiState(copies = listOf(copy), loading = false, showReleaseNames = releaseNames.value), {}, {}, {}, {}) }
        compose.onNodeWithContentDescription("详细列表").performClick()
        assertShelfSwitch()
        compose.onNodeWithText(copy.purchaseDate!!.toString()).assertIsDisplayed()
    }

    @Test fun `home switches immediately with independent release visibility`() {
        show { HomeContent(HomeStats(showReleaseNames = releaseNames.value), listOf(copy), {}, {}, {}) }
        assertShelfSwitch()
    }

    @Test fun `search main title and cover accessibility follow mode without replacing results`() {
        show { VnSearchRow(vn, onClick = {}) }
        compose.onNodeWithContentDescription("サクラノ詩", useUnmergedTree = true).assertExists()
        switch()
        compose.onNodeWithContentDescription("Sakura no Uta", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("サクラノ詩", useUnmergedTree = true).assertDoesNotExist()
        // Both titles remain available; only their primary/secondary role changes.
        compose.onNodeWithText("サクラノ詩", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Sakura no Uta", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `release selection follows mode`() {
        show { ReleaseRow(release, copy.coverUrl, onClick = {}) }
        compose.onNodeWithText("初回版").assertIsDisplayed()
        switch()
        compose.onNodeWithText("First Edition").assertIsDisplayed()
        compose.onNodeWithText("初回版").assertDoesNotExist()
    }

    @Test fun `purchase form follows mode rather than its fixed legacy form title`() {
        show { PurchaseFormContent(AddFlowUiState(selectedVn = vn, releases = ReleasesUiState(releases = listOf(release)),
            form = PurchaseFormState(releaseId = release.id, releaseTitle = "Legacy")), {}) }
        compose.onNodeWithText("サクラノ詩").assertIsDisplayed()
        compose.onNodeWithText("初回版").assertIsDisplayed()
        switch()
        compose.onNodeWithText("Sakura no Uta").assertIsDisplayed()
        compose.onNodeWithText("First Edition").assertIsDisplayed()
    }

    @Test fun `detail works from snapshots alone and switches immediately`() {
        show { CopyDetailContent(CopyDetailUiState(loading = false, copy = copy), {}) }
        compose.onNodeWithText("サクラノ詩").assertIsDisplayed()
        compose.onNodeWithText("初回版").assertIsDisplayed()
        switch()
        compose.onNodeWithText("Sakura no Uta").assertIsDisplayed()
        compose.onNodeWithText("First Edition").assertIsDisplayed()
    }

    @Test fun `edit manual release keeps user input on switching`() {
        val manual = copy.copy(releaseId = null, releaseTitle = "My custom edition", releaseOriginalTitle = null, releaseRomanizedTitle = null)
        show { CopyEditContent(CopyEditUiState(loading = false, copy = manual), {}, {}) }
        compose.onNodeWithText("My custom edition").assertIsDisplayed()
        switch()
        compose.onNodeWithText("Sakura no Uta").assertIsDisplayed()
        compose.onNodeWithText("My custom edition").assertIsDisplayed()
    }

    private fun orderDetail(): OrderDetail = OrderDetail(
        PurchaseOrder(1, "Batch", null, null, "JPY", null, 0, 0), listOf(copy), emptyList(),
        CostEngine.computeOrderCosts(listOf(copy.costInput()), emptyList()),
    )

    @Test fun `order copies follow the same title mode`() {
        show { OrderDetailContent(orderDetail(), {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("サクラノ詩").performScrollTo().assertIsDisplayed()
        switch()
        compose.onNodeWithText("Sakura no Uta").assertIsDisplayed()
        compose.onNodeWithText("First Edition").assertIsDisplayed()
    }

    @Test fun `manual allocations and cost preview follow mode without recalculating data`() {
        val detail = orderDetail()
        show { ExpenseEditorContent(OrderDetailUiState(detail = detail,
            editor = ExpenseEditorState(mode = AllocationMode.MANUAL, amountText = "100"), preview = detail.breakdown), {}, {}, {}, {}, { _, _ -> }) }
        compose.onAllNodesWithText("サクラノ詩", useUnmergedTree = true).assertCountEquals(2)
        switch()
        compose.onAllNodesWithText("Sakura no Uta", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("First Edition", useUnmergedTree = true).assertExists()
    }
}
