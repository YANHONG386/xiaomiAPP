// 时迹 —— app 模块构建脚本
// 注意：本项目是纯单机应用，红线 = 不申请 INTERNET 权限、不引任何网络库

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)   // Compose 编译器（Kotlin 2.x 起必须）
    alias(libs.plugins.ksp)              // 注解处理（Room 用）
}

android {
    namespace = "com.shiji.trace"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shiji.trace"
        minSdk = 26                       // 最低支持安卓 8.0（覆盖小米存量机）
        targetSdk = 35                    // 高于小米商店下限(30)，避免安卓16新行为变更
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 上架版：开启代码混淆（R8），减小包体
            isMinifyEnabled = true
            isShrinkResources = true
            // 签名配置在本地 keystore.properties 中读取（不入库，见 .gitignore）
            val keystoreProps = rootProject.file("keystore.properties")
            if (keystoreProps.exists()) {
                val props = java.util.Properties().apply { load(keystoreProps.inputStream()) }
                signingConfig = signingConfigs.create("release") {
                    storeFile = rootProject.file(props["storeFile"] as String)
                    storePassword = props["storePassword"] as String
                    keyAlias = props["keyAlias"] as String
                    keyPassword = props["keyPassword"] as String
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 编译选项：Java 17（与 JDK 17 匹配）
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true                    // 开启 Compose
    }
}

dependencies {
    // —— Compose 界面框架（通过 BOM 统一版本）——
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // —— 基础组件 ——
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)   // 前台生命周期监听（打开应用时触发同步）
    implementation(libs.androidx.navigation.compose)

    // —— 数据层（纯本地，无网络依赖）——
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    // —— 异步协程 ——
    implementation(libs.kotlinx.coroutines.android)

    // —— 测试（纯 JVM）——
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
}
