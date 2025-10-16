// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // 基础 Android 应用插件
    alias(libs.plugins.androidApplication) apply false
    // Kotlin 插件
    alias(libs.plugins.kotlinAndroid) apply false
}

// 定义版本别名，供所有模块使用
// 如果您想让这个文件不依赖于 libs.versions.toml，可以简化为：
/*
plugins {
    id("com.android.application") version "8.4.1" apply false // 示例版本
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false // 示例版本
}
*/
// 注意：在实际项目中，使用 Version Catalogs (libs) 更推荐。