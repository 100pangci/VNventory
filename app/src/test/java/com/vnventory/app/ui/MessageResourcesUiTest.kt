package com.vnventory.app.ui

import android.content.Context
import android.content.res.Resources
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.vnventory.app.R
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.text.Message
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.ui.text.localized
import com.vnventory.app.ui.text.resourceId
import com.vnventory.app.ui.text.resolve
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageResourcesUiTest {
    @get:Rule val compose = createComposeRule()
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test fun `全部业务消息具有资源且格式参数可正确解析`() {
        val placeholders = Regex("%([1-9][0-9]*)\\$([sd])")
        MessageKey.entries.forEach { key ->
            val template = resources.getString(key.resourceId)
            val types = placeholders.findAll(template).associate { it.groupValues[1].toInt() to it.groupValues[2] }
            val args: List<Any> = (1..(types.keys.maxOrNull() ?: 0)).map { if (types[it] == "d") 2 else "test" }
            assertTrue(key.name, resources.resolve(message(key, *args.toTypedArray())).isNotBlank())
        }
    }

    @Test fun `池化和校验提示支持嵌套消息且用户内容不被翻译或当格式串`() {
        assertEquals("平均分摊（2 笔）", resources.resolve(message(MessageKey.ALLOCATION_POOL, AllocationMode.EQUAL.label, 2)))
        assertEquals("100% / %1\$s", resources.resolve(Message.Literal("100% / %1\$s")))
        assertEquals("Shop %s：费用不能为负数（暂未分摊）", resources.resolve(message(MessageKey.ALLOCATION_ISSUE, "Shop %s", message(MessageKey.EXPENSE_NEGATIVE))))
    }

    @Test fun `已保存在状态中的错误随显示资源变化而重新解析`() {
        val currentResources = mutableStateOf(resources)
        val heldMessage = message(MessageKey.AMOUNT_TOTAL_OVERFLOW)
        compose.setContent { CompositionLocalProvider(LocalResources provides currentResources.value) { Text(heldMessage.localized()) } }
        compose.onNodeWithText(resources.getString(R.string.message_amount_total_overflow)).assertExists()
        val base = resources
        @Suppress("DEPRECATION")
        val translated = object : Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getString(id: Int, vararg formatArgs: Any): String =
                if (id == R.string.message_amount_total_overflow) "Amount exceeds the supported range" else super.getString(id, *formatArgs)
        }
        compose.runOnIdle { currentResources.value = translated }
        compose.onNodeWithText("Amount exceeds the supported range").assertExists()
    }
}
