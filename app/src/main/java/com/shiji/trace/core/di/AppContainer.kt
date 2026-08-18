// 时迹 —— 依赖容器（手动依赖注入）
// 采用手动注入而非 Hilt 框架：本应用依赖少，手动注入更轻、无注解处理开销

package com.shiji.trace.core.di

import android.content.Context

/**
 * 全局依赖容器
 * 持有数据库、仓库、同步引擎等单例对象，供各 ViewModel 使用
 *
 * 当前为骨架，M2 数据层阶段将填充：
 * - AppDatabase（本地数据库）
 * - UsageRepository（数据仓库）
 * - UsageSyncEngine（同步引擎）
 */
class AppContainer(private val appContext: Context) {
    // TODO(M2): 在此初始化数据库与仓库
}
