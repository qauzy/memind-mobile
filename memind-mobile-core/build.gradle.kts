import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
    id("maven-publish")
}

group = "com.memind.mobile"
version = "0.1.0"

android {
    namespace = "com.memind.mobile.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = project.group.toString()
                artifactId = "memind-mobile-core"
                version = project.version.toString()
                pom {
                    name.set("Memind Mobile Core")
                    description.set("移动端可嵌入的轻量记忆系统核心库，面向 PokeClaw 等 Android App 集成。")
                }
            }
        }
        repositories {
            maven {
                name = "localBuild"
                url = layout.buildDirectory.dir("repo").get().asFile.toURI()
            }
        }
    }
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
