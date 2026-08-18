// 时迹 —— 同步引擎的 Room 存储实现（桥接 DAO 与引擎接口）

package com.shiji.trace.data.sync

import com.shiji.trace.data.db.AppDatabase
import com.shiji.trace.data.db.dao.AppInfoDao
import com.shiji.trace.data.db.dao.AppSessionDao
import com.shiji.trace.data.db.dao.DailySnapshotDao
import com.shiji.trace.data.db.dao.UsageEventDao
import com.shiji.trace.data.db.entity.AppInfoEntity
import com.shiji.trace.data.db.entity.AppSessionEntity
import com.shiji.trace.data.db.entity.DailySnapshotEntity
import com.shiji.trace.data.db.entity.UsageEventEntity
import com.shiji.trace.domain.SessionEvent

/**
 * 同步引擎的存储实现：把引擎接口映射到 Room DAO
 */
class RoomSyncStorage(private val db: AppDatabase) : SyncStorage {

    private val eventDao: UsageEventDao = db.usageEventDao()
    private val sessionDao: AppSessionDao = db.appSessionDao()
    private val snapshotDao: DailySnapshotDao = db.dailySnapshotDao()
    private val appInfoDao: AppInfoDao = db.appInfoDao()

    override suspend fun insertEvents(events: List<UsageEventEntity>): Int =
        // IGNORE 冲突策略 + 唯一索引 = 幂等写入；返回实际插入行数
        eventDao.insertAll(events).count { it >= 0 }

    override suspend fun maxEventTime(startMs: Long, endMs: Long): Long? =
        eventDao.maxEventTimeBetween(startMs, endMs)

    override suspend fun queryEventsForRebuild(startMs: Long, endMs: Long): List<SessionEvent> =
        eventDao.queryBetween(startMs, endMs).map {
            SessionEvent(it.eventTimeMs, it.packageName, it.eventType)
        }

    override suspend fun replaceSessionsForDate(date: String, sessions: List<AppSessionEntity>) {
        sessionDao.deleteByDate(date)
        sessionDao.insertAll(sessions)
    }

    override suspend fun upsertSnapshots(snapshots: List<DailySnapshotEntity>) {
        snapshotDao.upsertAll(snapshots)
    }

    override suspend fun getSnapshot(date: String, packageName: String): DailySnapshotEntity? =
        snapshotDao.queryByDate(date).firstOrNull { it.packageName == packageName }

    override suspend fun upsertAppInfos(infos: List<AppInfoEntity>) {
        appInfoDao.upsertAll(infos)
    }
}

/**
 * 同步引擎的游标存储实现（SharedPreferences，单长整型）
 */
class PrefsCursorStore(
    private val prefs: android.content.SharedPreferences,
) : SyncCursorStore {

    override fun readCursor(): Long? =
        if (prefs.contains(KEY)) prefs.getLong(KEY, 0L) else null

    override fun writeCursor(timeMs: Long) {
        prefs.edit().putLong(KEY, timeMs).apply()
    }

    companion object {
        private const val KEY = "last_synced_event_time"
    }
}
