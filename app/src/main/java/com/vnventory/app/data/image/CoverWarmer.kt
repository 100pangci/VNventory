package com.vnventory.app.data.image

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest

/**
 * 保存收藏或绑定版本后预热封面缓存。
 *
 * 保存时网络刚被用于搜索/浏览，是下载封面最可靠的时机；完成后即使之后断网、
 * 国际链路拥塞或被阻断，收藏书架也能直接从本机持久缓存显示封面。
 * 只按用户操作预热单张图片，不批量抓取搜索列表。
 */
fun interface CoverWarmer {

    fun warm(coverUrl: String?)

    companion object {
        /** 测试或未接线时的空实现。 */
        val None: CoverWarmer = CoverWarmer { _ -> }
    }
}

class CoilCoverWarmer(context: Context) : CoverWarmer {

    private val appContext = context.applicationContext

    override fun warm(coverUrl: String?) {
        if (coverUrl.isNullOrBlank()) return
        SingletonImageLoader.get(appContext)
            .enqueue(ImageRequest.Builder(appContext).data(coverUrl).build())
    }
}
