// Top-level build file
// 纯 Java 工程，仅声明 Android 应用插件。Kotlin 插件未使用，不引入。
// AGP 8.13.2：支持 compileSdk 37（Android 16 minor SDK），libxposed 102 要求 compileSdk>=37。
plugins {
    id("com.android.application") version "8.13.2" apply false
}
