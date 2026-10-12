package com.vnventory.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.vnventory.app.data.image.CoverImageLoader
import com.vnventory.app.di.AppContainer

class VNventoryApp : Application(), SingletonImageLoader.Factory {

    val container: AppContainer by lazy { AppContainer(this) }

    /** Coil 单例：封面走持久磁盘缓存与带重试的图片客户端（见 [CoverImageLoader]）。 */
    override fun newImageLoader(context: Context): ImageLoader = CoverImageLoader.create(context)
}
