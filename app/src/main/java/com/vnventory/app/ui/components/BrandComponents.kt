package com.vnventory.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vnventory.app.R

/** 应用内展示完整 SVG；与自适应启动图标共用同一彩色前景，不添加主题染色。 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, contentDescription: String? = "VNventory 应用图标") {
    Box(modifier.size(128.dp)) {
        Image(painterResource(R.drawable.vnventory_logo_background), contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = contentDescription, modifier = Modifier.fillMaxSize())
    }
}

/** 装饰性品牌标记使用同源单色图；放大到去除启动图标安全区留白后的大小。 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_monochrome),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.size(32.dp).graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
    )
}
