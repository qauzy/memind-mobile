# Memind Mobile 对外 API 需要在宿主 App 混淆时保持稳定，方便 PokeClaw 侧直接调用。
-keep class com.memind.mobile.core.** { *; }

# kotlinx.serialization 生成的序列化器需要保留，避免 release 混淆后 JSON 编解码失败。
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
