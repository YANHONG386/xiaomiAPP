// 时迹项目 —— 根构建脚本
// 只声明插件版本（版本统一在 gradle/libs.versions.toml 管理），具体插件在 app 模块应用
// 仓库顺序：阿里云镜像优先（国内网络稳定），官方仓库兜底
pluginManagement {
    repositories {
        // 阿里云镜像（Google Maven / 插件门户 / Central 代理）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 谷歌仓库（安卓官方组件，镜像失效时兜底）
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Maven 中央仓库
        mavenCentral()
        // Gradle 插件门户
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 统一仓库管理：所有模块的依赖声明必须通过本配置解析
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

// 根项目名：时迹
rootProject.name = "ShiJi"
// 只包含 app 一个模块（个人项目避免过度拆分）
include(":app")
