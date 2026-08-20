// 时迹 —— 同步引擎单元测试（纯 JVM，用 fake 替代系统 API 和数据库）

package com.shiji.trace.data.sync

import com.shiji.trace.data.db.entity.AppSessionEntity
import com.shiji.trace.data.db.entity.DailySnapshotEntity
import com.shiji.trace.data.db.entity.UsageEventEntity
import com.shiji.trace.domain.SessionEvent
import kotlinx.coroutines.test.runTest
import com.shiji.trace.data.db.entity.EVENT_RESUMED
import com.shiji.trace.data.db.entity.EVENT_PAUSED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// —— fake 实现 ——

/** 事件源 fake：预置事件，按时间段过滤返回 */
class FakeEventSource(var events: List<SessionEvent>) : SyncEventSource {
    override fun queryEvents(startMs: Long, endMs: Long): List<SessionEvent> =
        events.filter { it.timeMs in startMs..endMs }

    override fun queryAppTotalTime(packageName: String, startMs: Long, endMs: Long): Long? = null
}

/** 存储 fake：内存存储 */
class FakeStorage : SyncStorage {
    val eventStore = mutableListOf<UsageEventEntity>()
    val sessionStore = mutableMapOf<String, MutableList<AppSessionEntity>>()
    val snapshotStore = mutableMapOf<Pair<String, String>, DailySnapshotEntity>()
    val infoStore = mutableMapOf<String, com.shiji.trace.data.db.entity.AppInfoEntity>()

    override suspend fun insertEvents(events: List<UsageEventEntity>): Int {
        // 模拟唯一索引去重：(eventTimeMs, packageName, eventType, className)
        val seen = eventStore.map { it.eventTimeMs to it.packageName to it.eventType to it.className }.toSet()
        val fresh = events.filter { (it.eventTimeMs to it.packageName to it.eventType to it.className) !in seen }
        eventStore.addAll(fresh)
        return fresh.size
    }

    override suspend fun maxEventTime(startMs: Long, endMs: Long): Long? =
        eventStore.filter { it.eventTimeMs in startMs..endMs }.maxOfOrNull { it.eventTimeMs }

    override suspend fun queryEventsForRebuild(startMs: Long, endMs: Long): List<SessionEvent> =
        eventStore.filter { it.eventTimeMs in startMs..endMs }
            .map { SessionEvent(it.eventTimeMs, it.packageName, it.eventType) }
            .sortedBy { it.timeMs }

    override suspend fun replaceSessionsForDate(date: String, sessions: List<AppSessionEntity>) {
        sessionStore[date] = sessions.toMutableList()
    }

    override suspend fun upsertSnapshots(snapshots: List<DailySnapshotEntity>) {
        snapshots.forEach { snapshotStore[it.date to it.packageName] = it }
    }

    override suspend fun getSnapshot(date: String, packageName: String): DailySnapshotEntity? =
        snapshotStore[date to packageName]

    override suspend fun upsertAppInfos(infos: List<com.shiji.trace.data.db.entity.AppInfoEntity>) {
        infos.forEach { infoStore[it.packageName] = it }
    }
}

/** 游标 fake：内存游标 */
class FakeCursorStore : SyncCursorStore {
    var cursor: Long? = null
    override fun readCursor(): Long? = cursor
    override fun writeCursor(timeMs: Long) { cursor = timeMs }
}

/** 便捷构造引擎 */
private fun engine(
    events: List<SessionEvent>,
    storage: FakeStorage = FakeStorage(),
    cursor: FakeCursorStore = FakeCursorStore(),
    now: Long = 10_000_000_000L,
    systemPackages: Set<String> = emptySet(),
): Triple<UsageSyncEngine, FakeStorage, FakeCursorStore> {
    val e = UsageSyncEngine(FakeEventSource(events), storage, cursor, systemPackages)
    e.nowProvider = { now }
    return Triple(e, storage, cursor)
}

class UsageSyncEngineTest {

    private val HOUR = 3_600_000L

    // —— 场景 1：无游标 → 触发首次回填 ——
    @Test
    fun `无游标时执行回填并写入事件`() = runTest {
        val now = 10_000_000_000L
        val events = listOf(
            SessionEvent(now - 2 * HOUR, "com.app.a", EVENT_RESUMED),
            SessionEvent(now - 2 * HOUR + 60_000, "com.app.a", EVENT_PAUSED),
        )
        val (engine, storage, cursor) = engine(events, now = now)

        val inserted = engine.syncIncremental()

        assertTrue(inserted > 0, "回填应写入事件")
        assertEquals(2, storage.eventStore.size)
        assertNotNull(cursor.cursor, "回填后应写入游标")
    }

    // —— 场景 2：增量同步写入新事件 ——
    @Test
    fun `增量同步写入游标之后的新事件`() = runTest {
        val now = 10_000_000_000L
        val (engine, storage, cursor) = engine(emptyList(), now = now)
        // 预置游标：3 小时前
        cursor.writeCursor(now - 3 * HOUR)

        // 事件源补充：游标之后的新事件
        val source = FakeEventSource(
            listOf(
                SessionEvent(now - 2 * HOUR, "com.app.b", EVENT_RESUMED),
                SessionEvent(now - 2 * HOUR + 60_000, "com.app.b", EVENT_PAUSED),
            )
        )
        val engine2 = UsageSyncEngine(source, storage, cursor, emptySet())
        engine2.nowProvider = { now }

        val inserted = engine2.syncIncremental()

        assertEquals(2, inserted)
        assertEquals(2, storage.eventStore.size)
    }

    // —— 场景 3：幂等去重（重复同步不产生重复数据）——
    @Test
    fun `重复同步不产生重复事件`() = runTest {
        val now = 10_000_000_000L
        val events = listOf(
            SessionEvent(now - 2 * HOUR, "com.app.a", EVENT_RESUMED),
            SessionEvent(now - 2 * HOUR + 60_000, "com.app.a", EVENT_PAUSED),
        )
        val (engine, storage, _) = engine(events, now = now)

        // 同步两次（模拟再次打开应用）
        engine.syncIncremental()
        val second = engine.syncIncremental()

        assertEquals(2, storage.eventStore.size, "重复同步不应重复写入")
        assertEquals(0, second, "第二次同步应无新数据")
    }

    // —— 场景 4：时钟回拨检测（当前时间早于游标 → 重置游标重新同步）——
    @Test
    fun `时钟回拨时重置游标重新同步`() = runTest {
        val now = 10_000_000_000L
        val (engine, storage, cursor) = engine(emptyList(), now = now)
        // 预置游标：超前 5 小时（时钟曾调快后同步过，现在调回正常时间）
        cursor.writeCursor(now + 5 * HOUR)

        // 事件源：5 小时前的事件（调回期间错过的区间，尚未同步）
        val source = FakeEventSource(
            listOf(
                SessionEvent(now - 5 * HOUR, "com.app.a", EVENT_RESUMED),
                SessionEvent(now - 5 * HOUR + 60_000, "com.app.a", EVENT_PAUSED),
            )
        )
        val engine2 = UsageSyncEngine(source, storage, cursor, emptySet())
        engine2.nowProvider = { now }

        engine2.syncIncremental()

        // 事件应被重新写入，游标重置后回退到最早事件附近
        assertTrue(storage.eventStore.isNotEmpty(), "时钟回拨后应重新拉取事件")
        assertTrue((cursor.cursor ?: 0) < now - 4 * HOUR, "游标应回退到最早事件附近")
    }

    // —— 场景 5：会话重建 ——
    @Test
    fun `同步后当日会话被重建`() = runTest {
        val now = 10_000_000_000L
        val events = listOf(
            SessionEvent(now - 2 * HOUR, "com.app.a", EVENT_RESUMED),
            SessionEvent(now - 2 * HOUR + 60_000, "com.app.a", EVENT_PAUSED),
        )
        val (engine, storage, _) = engine(events, now = now)

        engine.syncIncremental()

        // 会话应已生成（60 秒会话）
        val todaySessions = storage.sessionStore.values.flatten()
        assertEquals(1, todaySessions.size)
        assertEquals(60_000, todaySessions[0].durationMs)
    }

    // —— 场景 6：快照生成（非系统应用）——
    @Test
    fun `同步后生成每日快照`() = runTest {
        val now = 10_000_000_000L
        val events = listOf(
            SessionEvent(now - 2 * HOUR, "com.app.a", EVENT_RESUMED),
            SessionEvent(now - 2 * HOUR + 60_000, "com.app.a", EVENT_PAUSED),
            SessionEvent(now - 1 * HOUR, "com.app.a", EVENT_RESUMED),
            SessionEvent(now - 1 * HOUR + 30_000, "com.app.a", EVENT_PAUSED),
        )
        val (engine, storage, _) = engine(events, now = now)

        engine.syncIncremental()

        // a 应用当日快照 = 60s + 30s = 90s
        val snapshot = storage.snapshotStore.values.first()
        assertEquals(90_000, snapshot.totalTimeMs)
        assertEquals(2, snapshot.sessionCount)
    }

    // —— 场景 7：系统包不入快照 ——
    @Test
    fun `系统应用不生成快照`() = runTest {
        val now = 10_000_000_000L
        val events = listOf(
            SessionEvent(now - 2 * HOUR, "com.android.launcher", EVENT_RESUMED),
            SessionEvent(now - 2 * HOUR + 60_000, "com.android.launcher", EVENT_PAUSED),
        )
        val (engine, storage, _) = engine(events, now = now, systemPackages = setOf("com.android.launcher"))

        engine.syncIncremental()

        assertTrue(storage.snapshotStore.isEmpty(), "系统应用不应入快照")
    }

    // —— 场景 8：空事件同步 ——
    @Test
    fun `无事件时同步不报错`() = runTest {
        val now = 10_000_000_000L
        val (engine, storage, _) = engine(emptyList(), now = now)
        val inserted = engine.syncIncremental()
        assertEquals(0, inserted)
        assertTrue(storage.sessionStore.isEmpty())
    }
}
