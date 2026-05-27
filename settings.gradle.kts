pluginManagement {
    repositories {
        // 国内网络下优先使用镜像解析 Android Gradle Plugin，失败时再回退官方仓库。
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // 国内网络下优先使用镜像解析 AndroidX/Room 等依赖，避免 dl.google.com TLS 抖动影响打包。
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "memind-mobile"
include(":memind-mobile-core")
