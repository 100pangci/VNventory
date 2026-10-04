import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// 私钥及密码只从仓库外的私有配置加载；其他机器可用环境变量指定配置文件。
val releaseSigningFile = providers.environmentVariable("VNVENTORY_SIGNING_PROPERTIES").orNull
    ?.let { rootProject.file(it) }
    ?: File(System.getProperty("user.home"), ".sign/vnventory-release.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
val configuredVersionName = providers.gradleProperty("vnventoryVersionName").getOrElse("1.0.1")
val configuredVersionCode = versionCodeFor(configuredVersionName)
providers.gradleProperty("vnventoryVersionCode").orNull?.let { expected ->
    require(expected.toIntOrNull() == configuredVersionCode) {
        "VNventory versionCode does not match versionName $configuredVersionName"
    }
}

private fun versionCodeFor(versionName: String): Int {
    val match = Regex("""(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)""")
        .matchEntire(versionName)
        ?: error("VNventory versionName must use MAJOR.MINOR.PATCH: $versionName")
    val major = match.groupValues[1].toLong()
    val minor = match.groupValues[2].toLong()
    val patch = match.groupValues[3].toLong()
    require(minor < 1000 && patch < 1000) {
        "VNventory versionName requires MINOR and PATCH below 1000: $versionName"
    }
    require(major <= Int.MAX_VALUE.toLong() / 1_000_000) {
        "VNventory versionName produces an Android versionCode that is too large: $versionName"
    }
    val code = major * 1_000_000 + minor * 1_000 + patch
    require(code in 1..Int.MAX_VALUE.toLong()) {
        "VNventory versionName produces an invalid Android versionCode: $versionName"
    }
    return code.toInt()
}

android {
    namespace = "com.vnventory.app"
    // Android 16+ 采用小版本号（android-37.2），当前 SDK 目录: toolchain/android-sdk
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.vnventory.app"
        minSdk = 26
        targetSdk = 37
        versionCode = configuredVersionCode
        versionName = configuredVersionName
    }

    signingConfigs {
        create("release") {
            // minSdk 26 已支持 APK v2；同时提供 v3，旧版 JAR 签名和增量安装 v4 不需要。
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
            if (releaseSigningFile.isFile) {
                val required = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                require(required.all { !releaseSigningProperties.getProperty(it).isNullOrBlank() }) {
                    "Release 签名配置缺少必要字段，请检查 VNVENTORY_SIGNING_PROPERTIES 指向的私有配置"
                }
                val configuredStore = File(releaseSigningProperties.getProperty("storeFile"))
                storeFile = if (configuredStore.isAbsolute) configuredStore
                    else File(releaseSigningFile.parentFile, configuredStore.path)
                storeType = releaseSigningProperties.getProperty("storeType", "PKCS12")
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 没有私有签名配置时仍可构建未签名 APK；CI tag 发布可通过私有配置签名。
            signingConfig = if (releaseSigningFile.isFile) signingConfigs.getByName("release") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Robolectric 依赖（android-all jar）缓存到项目内，保持"依赖都在项目里"
                test.systemProperty("maven.repo.local", "$rootDir/toolchain/maven-local")
                test.systemProperty("vnventory.screenshot.dir", "$rootDir/toolchain/review/ui/screenshots")
                test.maxHeapSize = "2g"
            }
        }
    }

    sourceSets.getByName("test").resources.srcDir("$projectDir/schemas")
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
