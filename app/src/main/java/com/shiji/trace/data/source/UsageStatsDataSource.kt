// 时迹 —— 系统使用统计数据源（封装安卓系统 API）
// 负责：授权检查 + 事件查询 + 聚合查询

package com.shiji.trace.data.source

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Process
import com.shiji.trace.data.db.entity.EVENT_DEVICE_SHUTDOWN
import com.shiji.trace.data.db.entity.EVENT_KEYGUARD_SHOWN
import com.shiji.trace.data.db.entity.EVENT_MOVE_TO_BACKGROUND
import com.shiji.trace.data.db.entity.EVENT_MOVE_TO_FOREGROUND
import com.shiji.trace.data.db.entity.EVENT_SCREEN_NON_INTERACTIVE
import com.shiji.trace.data.sync.SyncEventSource
import com.shiji.trace.domain.SessionEvent

/**
 * 系统使用统计数据源
 * 所有与 UsageStatsManager 的交互集中在此，其他层不直接接触系统 API
 * 实现 SyncEventSource 接口 → 可直接作为同步引擎的事件源
 */
class UsageStatsDataSource(private val context: Context) : SyncEventSource {

    /** 系统使用统计管理器（懒加载） */
    private val usageStatsManager: UsageStatsManager?
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    // —— 需要记录的事件类型集合（过滤噪音：只存有用的）——
    // 前台/后台 = 会话开闭；锁屏/熄屏/关机 = 关闭全部活跃会话（防止深夜"幽灵会话"）
    // 注意：不跟踪 11（STANDBY_BUCKET_CHANGED，小米批量冻结应用会狂发，纯噪音）和
    // 15（SCREEN_INTERACTIVE，亮屏）——它们没有会话语义，入库只会污染数据
    private val trackedTypes = setOf(
        EVENT_MOVE_TO_FOREGROUND, EVENT_MOVE_TO_BACKGROUND,
        EVENT_KEYGUARD_SHOWN, EVENT_SCREEN_NON_INTERACTIVE, EVENT_DEVICE_SHUTDOWN,
    )

    /**
     * 检查是否已获得使用情况访问权限
     * 主判据：应用操作管理器查询（系统真实授权状态）
     * 兜底判据：部分小米系统返回异常值时，用空查询探测（能查到数据即视为已授权）
     */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = try {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } catch (e: Exception) {
            // 部分 ROM 查询会抛异常，走兜底探测
            AppOpsManager.MODE_DEFAULT
        }
        if (mode == AppOpsManager.MODE_ALLOWED) return true
        // 兜底：尝试查询最近 1 分钟数据，能查到说明实际可用
        return try {
            val end = System.currentTimeMillis()
            val stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, end - 60_000, end
            )
            !stats.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /** 打开系统"使用情况访问权限"设置页（供用户手动授权） */
    fun openUsageAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // 某些 ROM 没有该设置页，回退到应用详情设置
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    /**
     * 查询事件流并转换为简化事件
     * @param startMs 起始时间（epoch 毫秒）
     * @param endMs 结束时间（epoch 毫秒）
     * @return 简化事件列表（时间升序），未授权或失败返回空列表
     */
    override fun queryEvents(startMs: Long, endMs: Long): List<SessionEvent> {
        val manager = usageStatsManager ?: return emptyList()
        return try {
            val events = manager.queryEvents(startMs, endMs)
            val result = ArrayList<SessionEvent>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                // 只记录需要的事件类型（过滤噪音，减小存储）
                if (event.eventType !in trackedTypes) continue
                result.add(
                    SessionEvent(
                        timeMs = event.timeStamp,
                        packageName = event.packageName,
                        type = event.eventType
                    )
                )
            }
            result.sortedBy { it.timeMs }
        } catch (e: Exception) {
            // 查询失败（权限被撤销等）：返回空，由上层优雅降级
            emptyList()
        }
    }

    /**
     * 查询某应用某天的总使用时长（系统聚合口径，用于快照交叉校验）
     * @return 该时间段内应用使用总时长（毫秒），未知返回 null
     */
    override fun queryAppTotalTime(packageName: String, startMs: Long, endMs: Long): Long? {
        val manager = usageStatsManager ?: return null
        return try {
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startMs, endMs
            ) ?: return null
            stats.firstOrNull { it.packageName == packageName }?.totalTimeInForeground
        } catch (e: Exception) {
            null
        }
    }

    /** 探测系统实际可查的事件最早时间（用于首次回填范围） */
    fun probeEarliestEventTime(now: Long, lookBackMs: Long): Long? {
        val manager = usageStatsManager ?: return null
        return try {
            val events = manager.queryEvents(now - lookBackMs, now)
            var earliest: Long? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val t = event.timeStamp
                if (earliest == null || t < earliest) earliest = t
            }
            earliest
        } catch (e: Exception) {
            null
        }
    }
}
