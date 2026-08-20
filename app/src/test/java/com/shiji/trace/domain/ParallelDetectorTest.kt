// 时迹 —— 并行检测器单元测试（纯 JVM）
// 覆盖：切换/延迟/真分屏/分屏退出/崩溃/画中画/三并行/系统包排除

package com.shiji.trace.domain

import com.shiji.trace.data.db.entity.CONFIDENCE_HIGH
import com.shiji.trace.data.db.entity.CONFIDENCE_LOW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParallelDetectorTest {

    /** 便捷构造：正常闭合会话 */
    private fun session(pkg: String, start: Long, end: Long) =
        SessionData(pkg, start, end, openEnded = false)

    // —— 场景 1：纯切换（事件严格串行）→ 无并行 ——
    @Test
    fun `纯切换不产生并行组`() {
        // A: 1000-5000, B: 5000-9000 —— 无重叠
        val sessions = listOf(
            session("com.app.a", 1000, 5000),
            session("com.app.b", 5000, 9000),
        )
        val groups = ParallelDetector.detect(sessions)

        assertTrue(groups.isEmpty(), "严格串行不应有并行组")
    }

    // —— 场景 2：切换但 PAUSED 延迟 2 秒（超过 1500ms 阈值？不，1.2s 内）——
    @Test
    fun `切换且退后台事件延迟1秒时判定为切换`() {
        // A 实际在 B 开始时（3000）退出，但 A 的 PAUSED 到 4000 才到（延迟 1s < 1500ms 阈值）
        // 场景构造：A: 1000-4000, B: 3000-7000 → 重叠 1000ms ≤ 1500ms → 切换
        val sessions = listOf(
            session("com.app.a", 1000, 4000),
            session("com.app.b", 3000, 7000),
        )
        val groups = ParallelDetector.detect(sessions)

        assertTrue(groups.isEmpty(), "1500ms 内结束的重叠应判为切换")
    }

    // —— 场景 3：真分屏（持续重叠）→ 高置信并行 ——
    @Test
    fun `真分屏产生高置信并行组`() {
        // A: 1000-10000, B: 2000-9500 —— 重叠 7500ms，远超阈值
        val sessions = listOf(
            session("com.app.a", 1000, 10000),
            session("com.app.b", 2000, 9500),
        )
        val groups = ParallelDetector.detect(sessions)

        assertEquals(1, groups.size)
        val g = groups[0]
        assertEquals(CONFIDENCE_HIGH, g.confidence)
        assertEquals(setOf("com.app.a", "com.app.b"), g.packages.toSet())
        // 并行段 ≈ 2000-9500
        assertEquals(2000, g.startMs)
        assertEquals(9500, g.endMs)
    }

    // —— 场景 4：分屏退出（一方结束后另一方继续）——
    @Test
    fun `分屏退出后一方继续使用`() {
        // A: 1000-8000, B: 2000-4000 —— B 先退出，A 继续
        val sessions = listOf(
            session("com.app.a", 1000, 8000),
            session("com.app.b", 2000, 4000),
        )
        val groups = ParallelDetector.detect(sessions)

        assertEquals(1, groups.size)
        val g = groups[0]
        assertEquals(2000, g.startMs)
        assertEquals(4000, g.endMs)
        // 置信度：只有 B 高重合（段内占比 1.0），A 与段重合度低（2000/7000≈0.29）。
        // 规则：≥2 个应用重合 ≥0.5 才高置信；此处与画中画场景结构相同 → 低置信
        assertEquals(CONFIDENCE_LOW, g.confidence)
    }

    // —— 场景 5：崩溃无退后台事件（openEnded）→ 切换截断，无并行 ——
    @Test
    fun `崩溃未闭合会话被截断不产生并行`() {
        // A 崩溃（openEnded，结束时间延伸到 B 之后很远），B 随后进前台
        val sessions = listOf(
            SessionData("com.app.a", 1000, 9000, openEnded = true),
            session("com.app.b", 3000, 7000),
        )
        val groups = ParallelDetector.detect(sessions)

        // A 被截断到 B 的开始 → 无重叠 → 无并行组
        assertTrue(groups.isEmpty(), "崩溃场景不应产生并行组")
    }

    // —— 场景 6：画中画（时长悬殊、重合度低）→ 低置信 ——
    @Test
    fun `画中画场景判为低置信`() {
        // 视频应用 A 长时间驻留（PiP），用户主要在 B 中操作，A 相对较短？
        // 更真实的 PiP：A 全程活跃（视频一直在放），B 短时进入。
        // A: 1000-9000（8 秒长驻）, B: 3000-3500（0.5 秒闪过）—— B 太短被过滤？
        // 用 1.2 秒：B: 3000-4200
        val sessions = listOf(
            session("com.app.video", 1000, 9000),
            session("com.app.chat", 3000, 4200),
        )
        val groups = ParallelDetector.detect(sessions)

        assertEquals(1, groups.size)
        // 视频应用与并行段重合度高，但聊天应用在并行段中的占比高 → 两者都是高重合？
        // 判定逻辑：≥2 个应用重合度 ≥0.5 → HIGH；这里 chat 区间(1200ms) 完全在并行段内
        // 重合度 = 1200/1200 = 1.0；video 重合度 = 1200/8000 = 0.15 → 只有 1 个高 → LOW
        assertEquals(CONFIDENCE_LOW, groups[0].confidence, "时长悬殊应判低置信")
    }

    // —— 场景 7：三应用并行 ——
    @Test
    fun `三应用并行输出一个三应用并行组`() {
        val sessions = listOf(
            session("com.app.a", 1000, 10000),
            session("com.app.b", 2000, 9500),
            session("com.app.c", 3000, 9000),
        )
        val groups = ParallelDetector.detect(sessions)

        assertEquals(1, groups.size)
        val g = groups[0]
        assertEquals(setOf("com.app.a", "com.app.b", "com.app.c"), g.packages.toSet())
        assertEquals(CONFIDENCE_HIGH, g.confidence)
    }

    // —— 场景 8：系统包被排除 ——
    @Test
    fun `系统包不参与并行检测`() {
        val sessions = listOf(
            session("com.android.launcher", 1000, 9000),
            session("com.app.b", 2000, 8000),
            session("com.app.a", 1000, 9000),
        )
        val groups = ParallelDetector.detect(
            sessions,
            excludePackages = setOf("com.android.launcher", "com.android.systemui")
        )

        // launcher 被排除后，只有 a、b 并行
        assertEquals(1, groups.size)
        assertEquals(setOf("com.app.a", "com.app.b"), groups[0].packages.toSet())
    }

    // —— 场景 9：切换 + 真分屏混合 ——
    @Test
    fun `切换与真分屏混合时正确区分`() {
        // A: 1000-3000（B 后快速结束 → 切换），B: 2500-9000，C: 4000-8000（与 B 真并行）
        val sessions = listOf(
            session("com.app.a", 1000, 3000),
            session("com.app.b", 2500, 9000),
            session("com.app.c", 4000, 8000),
        )
        val groups = ParallelDetector.detect(sessions)

        // 只有 B+C 是真并行（A 与 B 的重叠 500ms < 1500ms 被截断）
        assertEquals(1, groups.size)
        assertEquals(setOf("com.app.b", "com.app.c"), groups[0].packages.toSet())
    }

    // —— 场景 10：空输入 ——
    @Test
    fun `空会话列表返回空`() {
        assertTrue(ParallelDetector.detect(emptyList()).isEmpty())
    }
}
