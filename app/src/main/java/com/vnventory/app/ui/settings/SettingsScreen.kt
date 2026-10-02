package com.vnventory.app.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vnventory.app.BuildConfig
import com.vnventory.app.di.AppViewModelProvider
import com.vnventory.app.domain.model.Money
import com.vnventory.app.ui.components.SectionCard

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val defaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "默认货币") {
                Text(
                    text = "添加收藏、新建订单和费用时的默认币种（每笔仍可单独修改）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Money.commonCurrencies.forEach { code ->
                        FilterChip(
                            selected = defaultCurrency == code,
                            onClick = { viewModel.setDefaultCurrency(code) },
                            label = { Text("$code（${Money.symbol(code).ifEmpty { code }}）") },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "数据与来源") {
                InfoLine("收藏数据只保存在本机（Room 数据库），不登录、不上传。")
                InfoLine("作品 / 版本元数据来自 VNDB 公共 API（api.vndb.org），仅作缓存使用。")
                InfoLine("VNDB 数据变化或缓存被清理都不会影响你的购买记录。")
                InfoLine("多币种金额不做汇率换算，按币种分开统计。")
            }
        }

        item {
            SectionCard(title = "关于") {
                InfoLine("VNventory ${BuildConfig.VERSION_NAME}（个人向 Galgame 实体收藏管理）")
                InfoLine("本应用与 VNDB 官方无隶属关系，数据版权归 VNDB 及各版权方所有。")
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text = "· $text",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}
