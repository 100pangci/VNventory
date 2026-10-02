package com.vnventory.app

import android.app.Application
import com.vnventory.app.di.AppContainer

class VNventoryApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}
