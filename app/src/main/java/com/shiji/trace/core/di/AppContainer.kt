// 时迹 —— 依赖容器（手动依赖注入）
// 采用手动注入而非 Hilt 框架：本应用依赖少，手动注入更轻、无注解处理开销

package com.shiji.trace.core.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.shiji.trace.data.db.AppDatabase
import com.shiji.trace.data.repository.UsageRepository
import com.shiji.trace.data.source.UsageStatsDataSource
import com.shiji.trace.data.sync.PrefsCursorStore
import com.shiji.trace.data.sync.RoomSyncStorage
import com.shiji.trace.data.sync.UsageSyncEngine
import com.shiji.trace.work.UsageSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 全局依赖容器
 * 持有数据库、数据源、同步引擎、仓库等单例对象，供各 ViewModel 使用
 */
class AppContainer(private val appContext: Context) {

    // —— 数据库 ——
    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    // —— 系统使用统计数据源 ——
    val usageStatsDataSource: UsageStatsDataSource by lazy {
        UsageStatsDataSource(appContext)
    }

    // —— 系统包集合（桌面、系统界面等，不参与排行与并行检测）——
    private val systemPackages: Set<String> by lazy {
        try {
            val pm = appContext.packageManager
            pm.getInstalledApplications(0)
                .filter {
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                }
                .map { it.packageName }
                .toSet()
        } catch (e: Exception) {
            // 查询失败时至少排除桌面和系统界面（常见包名兜底）
            setOf(
                "com.android.launcher",
                "com.android.systemui",
                "android",
            )
        }
    }

    // —— 同步引擎 ——
    val syncEngine: UsageSyncEngine by lazy {
        UsageSyncEngine(
            eventSource = usageStatsDataSource,
            storage = RoomSyncStorage(database),
            cursorStore = PrefsCursorStore(
                appContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            ),
            systemPackages = systemPackages,
        )
    }

    // —— 数据仓库（查询聚合）——
    val repository: UsageRepository by lazy {
        UsageRepository(database, usageStatsDataSource, systemPackages)
    }

    /** 应用级协程作用域（前台同步等后台任务用） */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 触发一次同步（授权后 / 前台回到应用时调用）
     * 后台线程执行，不阻塞 UI
     */
    fun triggerSync() {
        appScope.launch {
            try {
                syncEngine.syncIncremental()
            } catch (e: Exception) {
                // 同步失败不影响使用（下次再试）
            }
        }
    }

    /**
     * 授权成功后调用：注册后台周期任务 + 立即回填
     */
    fun onUsageAccessGranted() {
        // 注册 15 分钟周期采集（幂等：已注册不重复）
        UsageSyncWorker.schedule(appContext)
        // 立即执行首次回填（拉取历史事件）
        appScope.launch {
            try {
                syncEngine.backfill()
            } catch (e: Exception) {
                // 回填失败不影响授权状态（下次同步补齐）
            }
        }
    }
}
