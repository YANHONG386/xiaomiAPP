// 时迹 —— 应用入口
// 负责初始化全局依赖（数据库等），M2 阶段会在此注册数据同步

package com.shiji.trace

import android.app.Application
import com.shiji.trace.core.di.AppContainer

/**
 * 应用入口类
 * 在应用启动时初始化全局依赖容器（数据库、同步引擎等）
 */
class TimelineApp : Application() {

    /** 全局依赖容器（手动依赖注入，M2 阶段填充数据库等） */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 初始化依赖容器（AppContainer 在 M2 阶段接入数据库）
        container = AppContainer(this)
    }
}
