# kotlinx.serialization 保留规则（release 若开启混淆时需要）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / OkHttp / SLF4J 常见告警
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.debug.**
-dontwarn okhttp3.**
-dontwarn okio.**
