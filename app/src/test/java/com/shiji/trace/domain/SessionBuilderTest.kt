// 时迹 —— 会话构建器单元测试（纯 JVM）

package com.shiji.trace.domain

import com.shiji.trace.data.db.entity.EVENT_DEVICE_SHUTDOWN
import com.shiji.trace.data.db.entity.EVENT_KEYGUARD_SHOWN
import com.shiji.trace.data.db.entity.EVENT_MOVE_TO_BACKGROUND
import com.shiji.trace.data.db.entity.EVENT_MOVE_TO_FOREGROUND
import com.shiji.trace.data.db.entity.EVENT_PAUSED
import com.shiji.trace.data.db.entity.EVENT_RESUMED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionBuilderTest {

    /** 便捷构造：前台事件 */
    private fun fg(time: Long, pkg: String = "com.app.a") =
        SessionEvent(time, pkg, EVENT_RESUMED)

    /** 便捷构造：后台事件 */
    private fun bg(time: Long, pkg: String = "com.app.a") =
        SessionEvent(time, pkg, EVENT_PAUSED)

    // —— 场景 1：正常开/关会话 ——
    @Test
    fun `正常的前台后台事件生成一个会话`() {
        val events = listOf(fg(1000), bg(5000))
        val sessions = SessionBuilder.build(events)

        assertEquals(1, sessions.size)
        assertEquals(1000, sessions[0].startMs)
        assertEquals(5000, sessions[0].endMs)
        assertEquals(4000, sessions[0].durationMs)
        assertTrue(!sessions[0].openEnded, "正常关闭的会话不应标记 openEnded")
    }

    // —— 场景 2：连续前台事件去重 ——
    @Test
    fun `同应用连续前台事件只生成一个会话`() {
        val events = listOf(
            fg(1000),
            fg(1500),  // 同应用再次 RESUMED（抖动），应忽略
            bg(5000)
        )
        val sessions = SessionBuilder.build(events)

        assertEquals(1, sessions.size)
        assertEquals(1000, sessions[0].startMs)
    }

    // —— 场景 3：锁屏关闭全部 ——
    @Test
    fun `锁屏事件关闭全部活跃会话`() {
        val events = listOf(
            fg(1000, "com.app.a"),
            fg(2000, "com.app.b"),
            SessionEvent(3000, "com.app.a", EVENT_KEYGUARD_SHOWN),
            bg(4000, "com.app.a"), // 锁屏后的事件应被忽略（会话已关闭）
        )
        val sessions = SessionBuilder.build(events)

        assertEquals(2, sessions.size)
        // 两个会话都应在锁屏时刻（3000）结束
        assertTrue(sessions.all { it.endMs == 3000L })
        // 锁屏后的 bg 事件不应再产生会话
        assertTrue(sessions.none { it.startMs > 3000L })
    }

    // —— 场景 4：小于 1 秒的会话丢弃 ——
    @Test
    fun `小于1秒的会话被丢弃`() {
        val events = listOf(
            fg(1000),
            bg(1500), // 500ms，过短
            fg(2000),
            bg(5000), // 3 秒，有效
        )
        val sessions = SessionBuilder.build(events)

        assertEquals(1, sessions.size)
        assertEquals(2000, sessions[0].startMs)
    }

    // —— 场景 5：事件乱序时仍正确 ——
    @Test
    fun `事件乱序时仍能正确构建`() {
        val events = listOf(
            bg(5000),
            fg(1000),
        )
        val sessions = SessionBuilder.build(events)

        assertEquals(1, sessions.size)
        assertEquals(1000, sessions[0].startMs)
        assertEquals(5000, sessions[0].endMs)
    }

    // —— 场景 6：崩溃（无退后台事件）→ 未闭合会话 ——
    @Test
    fun `崩溃无退后台事件时标记为未闭合`() {
        val events = listOf(
            fg(1000, "com.app.a"),
            fg(3000, "com.app.b"), // b 进前台，a 崩溃再无事件
        )
        val sessions = SessionBuilder.build(events)

        // 只有 a 一个会话：b 的区间 0ms（3000-3000），被最短 1 秒规则过滤
        assertEquals(1, sessions.size)
        val a = sessions.first { it.packageName == "com.app.a" }
        // a 未闭合：结束时间 = 最后事件时间（3000），标记 openEnded
        assertTrue(a.openEnded)
        assertEquals(3000, a.endMs)
    }

    // —— 场景 7：MOVE_TO_FOREGROUND/BACKGROUND 事件同样有效 ——
    @Test
    fun `MOVE系列事件同样生成会话`() {
        val events = listOf(
            SessionEvent(1000, "com.app.a", EVENT_MOVE_TO_FOREGROUND),
            SessionEvent(5000, "com.app.a", EVENT_MOVE_TO_BACKGROUND),
        )
        val sessions = SessionBuilder.build(events)

        assertEquals(1, sessions.size)
        assertEquals(4000, sessions[0].durationMs)
    }

    // —— 场景 8：关机事件关闭全部 ——
    @Test
    fun `关机事件关闭全部会话`() {
        val events = listOf(
            fg(1000),
            SessionEvent(5000, "com.app.a", EVENT_DEVICE_SHUTDOWN),
        )
        val sessions = SessionBuilder.build(events)

        assertEquals(1, sessions.size)
        assertEquals(5000, sessions[0].endMs)
        assertTrue(sessions[0].openEnded)
    }

    // —— 场景 9：空输入 ——
    @Test
    fun `空事件流返回空列表`() {
        assertTrue(SessionBuilder.build(emptyList()).isEmpty())
    }
}
