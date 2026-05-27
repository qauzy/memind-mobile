pluginManagement {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // 国内网络下优先使用镜像解析 Kotlin/JVM 依赖。
        maven(url = "https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}

rootProject.name = "memind-mobile"
include(":memind-mobile-core")
include(":memind-store-json")

if (providers.gradleProperty("memind.includeSqlite").orNull == "true") {
    include(":memind-store-sqlite")
}
