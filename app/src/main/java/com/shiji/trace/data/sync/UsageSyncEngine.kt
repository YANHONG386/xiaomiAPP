// 时迹 —— 同步引擎（核心：增量同步 + 首次回填）
// 设计要点：依赖抽象接口（事件源/存储/游标），核心逻辑纯 Kotlin 可单测

package com.shiji.trace.data.sync

import com.shiji.trace.data.db.entity.AppInfoEntity
import com.shiji.trace.data.db.entity.AppSessionEntity
import com.shiji.trace.data.db.entity.DailySnapshotEntity
import com.shiji.trace.data.db.entity.UsageEventEntity
import com.shiji.trace.domain.SessionBuilder
import com.shiji.trace.domain.SessionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// —— 同步参数常量 ——

/** 单次查询分片时长（毫秒）：12 小时。部分 ROM 对单次 queryEvents 跨度有隐性限制 */
const val CHUNK_MS = 12L * 60 * 60 * 1000

/** 游标回退余量（毫秒）：5 分钟。容忍系统事件延迟上报，防止漏事件 */
const val CURSOR_BACKTRACK_MS = 5L * 60 * 1000

/** 时钟回拨检测阈值（毫秒）：2 小时。新事件时间早于游标太多 → 判定时钟被修改 */
const val CLOCK_RESET_THRESHOLD_MS = 2L * 60 * 60 * 1000

/** 首次回填最大天数：7 天（系统通常只保留数天事件） */
const val BACKFILL_MAX_DAYS = 7

/**
 * 事件源抽象（真实实现：UsageStatsDataSource，测试用：fake）
 */
interface SyncEventSource {
    /** 查询时间段内的事件（时间升序） */
    fun queryEvents(startMs: Long, endMs: Long): List<SessionEvent>

    /** 查询某应用时间段内的总时长（系统聚合口径），未知返回 null */
    fun queryAppTotalTime(packageName: String, startMs: Long, endMs: Long): Long?
}

/**
 * 存储抽象（真实实现：Room DAO 组合，测试用：fake）
 */
interface SyncStorage {
    /** 幂等写入事件，返回实际写入条数 */
    suspend fun insertEvents(events: List<UsageEventEntity>): Int

    /** 时间段内最大事件时间，无返回 null */
    suspend fun maxEventTime(startMs: Long, endMs: Long): Long?

    /** 按时间段查询事件（会话重建用） */
    suspend fun queryEventsForRebuild(startMs: Long, endMs: Long): List<SessionEvent>

    /** 重建某日会话（先删后插） */
    suspend fun replaceSessionsForDate(date: String, sessions: List<AppSessionEntity>)

    /** 写入快照（按 (date, packageName) 覆盖） */
    suspend fun upsertSnapshots(snapshots: List<DailySnapshotEntity>)

    /** 读取快照 */
    suspend fun getSnapshot(date: String, packageName: String): DailySnapshotEntity?

    /** 写入应用信息 */
    suspend fun upsertAppInfos(infos: List<AppInfoEntity>)
}

/**
 * 游标存储抽象（真实实现：SharedPreferences，测试用：fake）
 */
interface SyncCursorStore {
    /** 读取游标（上次同步到的事件时间，null 表示从未同步） */
    fun readCursor(): Long?
    /** 写入游标 */
    fun writeCursor(timeMs: Long)
}

/** 回填进度（供 UI 展示进度动画） */
data class BackfillProgress(
    /** 已完成天数 */
    val doneDays: Int,
    /** 总天数 */
    val totalDays: Int,
)

/**
 * 同步引擎
 *
 * 增量同步算法：
 * 1. 从游标回溯 5 分钟查起，分片查询（每片 ≤12h）
 * 2. 过滤 + 幂等写入（唯一索引兜底去重）
 * 3. 游标推进到最大写入事件时间 - 5 分钟
 * 4. 后处理：重建当日会话 + 累计当日快照
 * 5. 时钟回拨检测：新事件早于游标 2h → 重置游标重拉
 */
class UsageSyncEngine(
    private val eventSource: SyncEventSource,
    private val storage: SyncStorage,
    private val cursorStore: SyncCursorStore,
    /** 系统包集合（桌面、systemui 等，不参与排行与并行） */
    private val systemPackages: Set<String>,
) {
    /** 回填进度流（UI 订阅显示进度动画） */
    private val _backfillProgress = MutableStateFlow<BackfillProgress?>(null)
    val backfillProgress: Flow<BackfillProgress?> = _backfillProgress

    /** 日期格式化（本地时区，写入时固化，避免时区变化重算） */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    /** 当前时间提供器（可注入，测试用） */
    var nowProvider: () -> Long = { System.currentTimeMillis() }

    // ============ 公开接口 ============

    /**
     * 增量同步（每次打开应用 / 定时任务调用）
     * @return 本次写入的事件条数（0 表示无新数据或未授权）
     */
    suspend fun syncIncremental(): Int {
        val now = nowProvider()
        // 游标起点：上次同步位置回退 5 分钟（容忍事件延迟上报）
        val cursor = cursorStore.readCursor() ?: return backfill(now)
        var fromMs = cursor - CURSOR_BACKTRACK_MS
        if (fromMs < 0) fromMs = 0

        var totalInserted = 0
        // 分片查询，每片 ≤12 小时
        var chunkStart = fromMs
        while (chunkStart < now) {
            val chunkEnd = minOf(chunkStart + CHUNK_MS, now)
            val events = eventSource.queryEvents(chunkStart, chunkEnd)
            if (events.isNotEmpty()) {
                // —— 时钟回拨检测：事件时间远早于游标 → 时钟被修改，重置游标重拉 ——
                if (events.first().timeMs < cursor - CLOCK_RESET_THRESHOLD_MS) {
                    // 重置游标到最早新事件处，重新同步
                    cursorStore.writeCursor(events.first().timeMs)
                    chunkStart = events.first().timeMs
                    continue
                }
                val inserted = writeEvents(events)
                totalInserted += inserted
                // 游标推进到最大写入事件时间（回退 5 分钟余量）
                val maxTime = storage.maxEventTime(chunkStart, chunkEnd) ?: events.last().timeMs
                cursorStore.writeCursor(maxTime - CURSOR_BACKTRACK_MS)
            }
            chunkStart = chunkEnd
        }

        // 后处理：重建当日会话 + 快照（有新数据才处理，避免无谓开销）
        if (totalInserted > 0) {
            val today = formatDate(now)
            rebuildDay(today, now - (24L * 60 * 60 * 1000), now)
        }
        return totalInserted
    }

    /**
     * 首次授权回填：从系统可查范围拉取历史事件
     * @param now 当前时间
     * @param lookBackDays 回填天数（默认 7 天）
     */
    suspend fun backfill(now: Long = nowProvider(), lookBackDays: Int = BACKFILL_MAX_DAYS): Int {
        // 探测系统实际可查的最早事件时间（重启会清空事件，探测真实范围）
        val probeSource = eventSource
        // 用"探测最早时间"缩小回填范围：系统只保留开机以来的事件
        // （此处通过查询 7 天前的空窗口推断；简单起见按 lookBackDays 分天回填）
        val startMs = now - lookBackDays * 24L * 60 * 60 * 1000
        _backfillProgress.value = BackfillProgress(0, lookBackDays)

        var totalInserted = 0
        var dayIndex = 0
        var dayStart = startMs
        val dayMs = 24L * 60 * 60 * 1000
        while (dayStart < now) {
            val dayEnd = minOf(dayStart + dayMs, now)
            // 每天内再分片查询（每片 ≤12h）
            var chunkStart = dayStart
            while (chunkStart < dayEnd) {
                val chunkEnd = minOf(chunkStart + CHUNK_MS, dayEnd)
                val events = eventSource.queryEvents(chunkStart, chunkEnd)
                if (events.isNotEmpty()) {
                    totalInserted += writeEvents(events)
                    val maxTime = storage.maxEventTime(chunkStart, chunkEnd) ?: events.last().timeMs
                    cursorStore.writeCursor(maxOf(cursorStore.readCursor() ?: 0L, maxTime - CURSOR_BACKTRACK_MS))
                }
                chunkStart = chunkEnd
            }
            dayIndex++
            _backfillProgress.value = BackfillProgress(dayIndex, lookBackDays)
            dayStart = dayEnd
        }

        // 回填完成后重建最近一天会话
        if (totalInserted > 0) {
            rebuildDay(formatDate(now), now - dayMs, now)
        }
        _backfillProgress.value = null
        return totalInserted
    }

    /** 重置游标（权限恢复后按最近快照日重放） */
    fun resetCursorTo(date: String) {
        // 把游标设置到该日期 0 点
        val millis = parseDateMillis(date)
        cursorStore.writeCursor(millis - 1)
    }

    // ============ 内部实现 ============

    /** 写入事件并登记应用信息 */
    private suspend fun writeEvents(events: List<SessionEvent>): Int {
        // 事件 → 实体（固化日期）
        val entities = events.map { e ->
            UsageEventEntity(
                eventTimeMs = e.timeMs,
                packageName = e.packageName,
                eventType = e.type,
                className = "",
                date = formatDate(e.timeMs)
            )
        }
        val inserted = storage.insertEvents(entities)

        // 登记应用信息（首见时间、系统标记）
        val now = nowProvider()
        val infos = events
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                AppInfoEntity(
                    packageName = pkg,
                    label = pkg, // 显示名由 UI 层用包管理器解析后回填
                    isSystem = pkg in systemPackages,
                    firstSeenMs = list.minOf { it.timeMs },
                    lastSeenMs = list.maxOf { it.timeMs },
                )
            }
        storage.upsertAppInfos(infos)
        return inserted
    }

    /**
     * 重建某日会话与快照（删除当日 → 从事件重算 → 快照累计）
     * @param date 目标日期 yyyy-MM-dd
     * @param dayStartMs 该日 0 点（epoch 毫秒）
     * @param dayEndMs 该日结束（epoch 毫秒）
     */
    private suspend fun rebuildDay(date: String, dayStartMs: Long, dayEndMs: Long) {
        // 从事件流重建会话（系统包仍记录会话，但标记 isSystem）
        val events = storage.queryEventsForRebuild(dayStartMs, dayEndMs)
        val sessions = SessionBuilder.build(events).map { s ->
            AppSessionEntity(
                packageName = s.packageName,
                startTimeMs = s.startMs,
                endTimeMs = s.endMs,
                durationMs = s.durationMs,
                date = date,
                isSystem = s.packageName in systemPackages,
            )
        }
        storage.replaceSessionsForDate(date, sessions)

        // 快照：按应用聚合会话
        val snapshots = sessions
            .filter { !it.isSystem } // 系统应用不入快照（不参与统计）
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                DailySnapshotEntity(
                    date = date,
                    packageName = pkg,
                    totalTimeMs = list.sumOf { it.durationMs },
                    sessionCount = list.size,
                    lastUsedMs = list.maxOf { it.endTimeMs },
                    recordedAtMs = nowProvider(),
                )
            }
        storage.upsertSnapshots(snapshots)
    }

    /** 格式化为日期字符串 */
    private fun formatDate(timeMs: Long): String = dateFormat.format(Date(timeMs))

    /** 解析日期字符串为当天 0 点毫秒 */
    private fun parseDateMillis(date: String): Long {
        return try {
            dateFormat.parse(date)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
