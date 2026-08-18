// 时迹项目 —— 根构建脚本
// 只声明插件版本（版本统一在 gradle/libs.versions.toml 管理），具体插件在 app 模块应用

// 声明要使用的插件及其版本（不应用到本根项目，只是声明供子模块复用）
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
