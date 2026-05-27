plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.memind.mobile.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 协程基础能力。
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // JSON 序列化能力，用于模型和 Room 辅助字段转换。
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // HTTP 客户端，用于 OpenAI 兼容接口访问。
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room 持久化层，用于移动端本地 SQLite 存储。
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    kapt("androidx.room:room-compiler:2.7.0")

    // 单元测试依赖。
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.mockito:mockito-core:5.16.0")
}
