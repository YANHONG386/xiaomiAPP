// 时迹 —— 后台定时采集任务
// WorkManager 周期任务（15 分钟），作为前台同步的兜底

package com.shiji.trace.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shiji.trace.TimelineApp
import com.shiji.trace.data.source.UsageStatsDataSource
import com.shiji.trace.data.sync.UsageSyncEngine
import java.util.concurrent.TimeUnit

/**
 * 后台采集任务
 * 每 15 分钟尝试一次增量同步（未授权时静默跳过，不重试轰炸）
 */
class UsageSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as TimelineApp
        // 未授权 → 直接成功返回（避免 WorkManager 按失败重试轰炸）
        if (!app.container.usageStatsDataSource.hasUsageAccess()) {
            return Result.success()
        }
        return try {
            app.container.syncEngine.syncIncremental()
            Result.success()
        } catch (e: Exception) {
            // 同步异常：按退避策略稍后重试
            Result.retry()
        }
    }

    companion object {
        /** 周期任务名称（唯一标识） */
        const val WORK_NAME = "usage_sync"

        /** 周期间隔：15 分钟（WorkManager 系统下限） */
        private const val INTERVAL_MINUTES = 15L

        /**
         * 注册周期同步任务（幂等：已注册不重复）
         * 注意：本应用无网络约束（纯本地任务，不申请 INTERNET 权限）
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
