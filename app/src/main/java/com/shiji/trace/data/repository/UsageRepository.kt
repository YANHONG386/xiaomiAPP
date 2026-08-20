// 时迹 —— 数据仓库（界面层的唯一数据入口）
// 聚合 DAO 查询：今日概览、时间线、统计、并行组

package com.shiji.trace.data.repository

import com.shiji.trace.data.db.AppDatabase
import com.shiji.trace.data.db.entity.AppSessionEntity
import com.shiji.trace.data.db.entity.DailySnapshotEntity
import com.shiji.trace.data.db.entity.ParallelGroupEntity
import com.shiji.trace.domain.ParallelDetector
import com.shiji.trace.domain.SessionData
import com.shiji.trace.data.source.UsageStatsDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据仓库
 * 界面层不直接访问 DAO，统一走这里（今日/时间线/统计查询 + 并行检测）
 */
class UsageRepository(
    private val db: AppDatabase,
    private val usageStatsDataSource: UsageStatsDataSource,
    private val systemPackages: Set<String>,
) {
    /** 日期格式化（与同步引擎一致：本地时区 yyyy-MM-dd） */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // —— 今日概览 ——

    /** 今日各应用快照（响应式：数据更新自动刷新） */
    fun observeToday(): Flow<List<DailySnapshotEntity>> =
        db.dailySnapshotDao().observeByDate(today())

    /** 今日总时长（毫秒，响应式） */
    fun observeTodayTotal(): Flow<Long> =
        db.dailySnapshotDao().observeByDate(today()).map { snapshots ->
            snapshots.sumOf { it.totalTimeMs }
        }

    // —— 时间线 ——

    /** 某日会话列表（响应式） */
    fun observeSessions(date: String): Flow<List<AppSessionEntity>> =
        db.appSessionDao().observeByDate(date)

    /**
     * 某日并行组（响应式）
     * - 当日：从实时会话流推导（不落库，每次同步后自动更新）
     * - 历史日期：首次查看时按需计算并缓存到 parallel_group 表
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeParallelGroups(date: String): Flow<List<ParallelGroupEntity>> {
        if (date == today()) {
            return db.appSessionDao().observeByDate(date).mapLatest { sessions ->
                detectParallel(date, sessions) // 当日直接推导，不写库
            }
        }
        // 历史日期：查看时按需计算并缓存
        return db.parallelGroupDao().observeByDate(date).mapLatest { cached ->
            if (cached.isEmpty()) {
                recomputeParallelGroups(date)
            }
            db.parallelGroupDao().queryByDate(date) // 重算后再次查询
        }
    }

    /** 从会话重算某日并行组并缓存（先清空旧的，避免残留） */
    private suspend fun recomputeParallelGroups(date: String): List<ParallelGroupEntity> {
        val sessions = db.appSessionDao().queryByDate(date)
        // 先清空旧缓存，避免残留（同一天重算时）
        db.parallelGroupDao().deleteByDate(date)
        val entities = detectParallel(date, sessions)
        db.parallelGroupDao().insertAll(entities)
        return entities
    }

    /** 会话列表 → 并行组实体（过滤系统应用；当日实时推导与历史落库共用） */
    private fun detectParallel(
        date: String,
        sessions: List<AppSessionEntity>,
    ): List<ParallelGroupEntity> =
        ParallelDetector.detect(
            sessions.filter { !it.isSystem }.map {
                SessionData(
                    packageName = it.packageName,
                    startMs = it.startTimeMs,
                    endMs = it.endTimeMs,
                    openEnded = false, // 落库会话已闭合
                )
            },
            excludePackages = systemPackages,
        ).map { g ->
            ParallelGroupEntity(
                date = date,
                startMs = g.startMs,
                endMs = g.endMs,
                durationMs = g.durationMs,
                packagesJson = org.json.JSONArray(g.packages).toString(),
                confidence = g.confidence,
            )
        }

    // —— 统计 ——

    /** 某日期段每日总时长（柱状图数据） */
    suspend fun dailyTotals(startDate: String, endDate: String) =
        db.dailySnapshotDao().queryTotalsBetween(startDate, endDate)

    /** 某日期段应用聚合（排行数据） */
    suspend fun appTotals(startDate: String, endDate: String) =
        db.dailySnapshotDao().queryAppTotalsBetween(startDate, endDate)

    // —— 辅助 ——

    /** 今日日期字符串 */
    fun today(): String = dateFormat.format(Date())

    /** 日期字符串转毫秒 */
    fun parseDate(date: String): Long = dateFormat.parse(date)?.time ?: 0L

    /** 毫秒转日期字符串（时间线日期切换用） */
    fun todayDateFromMillis(ms: Long): String = dateFormat.format(Date(ms))

    /** 检查授权状态（界面横幅用） */
    fun hasUsageAccess(): Boolean = usageStatsDataSource.hasUsageAccess()
}
