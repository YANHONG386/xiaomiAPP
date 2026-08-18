// 时迹 —— 并行检测器（纯函数，无安卓依赖，可单测）
// 从会话区间中识别"同一时间段使用了哪些应用"（分屏/画中画场景）

package com.shiji.trace.domain

import com.shiji.trace.data.db.entity.CONFIDENCE_HIGH
import com.shiji.trace.data.db.entity.CONFIDENCE_LOW
import com.shiji.trace.data.db.entity.CONFIDENCE_MEDIUM

/**
 * 切换判定阈值（毫秒）。
 * 当 A 会话在 B 开始后 1500ms 内结束 → 视为"切换应用"而非并行。
 * 覆盖事件上报延迟与系统调度抖动；真分屏的双前台事件间隔通常 < 500ms，不会被误杀。
 */
const val SWITCH_THRESHOLD_MS = 1_500L

/**
 * 高置信并行所需的区间重合度（重叠时长 / 较短区间时长）。
 * 分屏的两个应用区间时长接近、重合度高（≥ 0.5）；
 * 画中画时长悬殊、重合度低 → 判为低置信"疑似"。
 */
const val HIGH_CONFIDENCE_OVERLAP_RATIO = 0.5

/**
 * 并行组（检测结果）
 */
data class ParallelGroupData(
    /** 并行开始时间（epoch 毫秒） */
    val startMs: Long,
    /** 并行结束时间（epoch 毫秒） */
    val endMs: Long,
    /** 参与并行的应用包名列表（按首次出现顺序） */
    val packages: List<String>,
    /** 置信度：CONFIDENCE_HIGH / MEDIUM / LOW */
    val confidence: Int,
) {
    /** 并行持续时长（毫秒） */
    val durationMs: Long get() = endMs - startMs
}

/**
 * 并行检测器：识别同一时间段内同时使用的应用
 *
 * 关键事实：普通手机同一时刻只有一个主前台应用。
 * - 切换应用 = 事件严格串行（A 退后台在 B 进前台前后毫秒级到达）
 * - 真并行（分屏）= A、B 都保持活跃，形成持续重叠
 *
 * 算法（两段式）：
 * 1. 【切换修正】对每个重叠对 (A, B)（A 先开始）：
 *    - A 在 B 开始后 1500ms 内结束 → 判为切换，A 截断到 B 开始（重叠清零）
 *    - A 是未闭合会话（崩溃，其后无任何事件）→ 判为切换，同样截断
 *    - 其余 → 保留重叠，是真并行候选
 * 2. 【重叠扫描】扫描线算法输出所有重叠时间段及其应用集合，
 *    连续重叠段合并为并行组；按重合度分级置信度
 */
object ParallelDetector {

    /**
     * 检测并行组
     * @param sessions 会话区间列表（来自 SessionBuilder，含 openEnded 标记）
     * @param excludePackages 排除的应用（系统包：桌面、systemui 等，不参与并行）
     * @return 并行组列表（按开始时间升序）
     */
    fun detect(
        sessions: List<SessionData>,
        excludePackages: Set<String> = emptySet(),
    ): List<ParallelGroupData> {
        // 过滤排除项并按开始时间排序
        val valid = sessions
            .filter { it.packageName !in excludePackages }
            .sortedBy { it.startMs }
        if (valid.size < 2) return emptyList()

        // —— 第一步：切换修正（截断伪重叠）——
        val corrected = correctSwitches(valid)

        // —— 第二步：重叠扫描 → 并行组 ——
        return scanOverlaps(corrected)
    }

    /**
     * 切换修正：
     * 两两检查重叠对，把"快速结束的第二个应用"场景修正为切换（截断）。
     * 注：n 为单日会话数（通常 < 1000），O(n²) 开销可接受且实现直观。
     */
    private fun correctSwitches(sessions: List<SessionData>): List<SessionData> {
        // 用可变副本做截断修正
        val result = sessions.map { it.copy() }.toMutableList()
        for (i in result.indices) {
            val a = result[i]
            for (j in i + 1 until result.size) {
                val b = result[j]
                // a 在 b 开始前已结束 → 无重叠，跳过
                if (a.endMs <= b.startMs) continue
                // a 与 b 重叠：判定是否"切换"而非并行
                val isSwitch = isSwitchPair(a, b)
                if (isSwitch) {
                    // 截断 a 到 b 开始时刻（切换语义：a 实际在 b 开始时就已退出）
                    result[i] = a.copy(endMs = b.startMs)
                    break // a 已修正，无需再与后面的区间比较
                }
            }
        }
        // 截断后可能产生过短会话，过滤
        return result.filter { it.durationMs >= MIN_SESSION_DURATION_MS }
    }

    /**
     * 判定重叠对 (a 先开始, b 后开始) 是否为"切换"而非"并行"
     */
    private fun isSwitchPair(a: SessionData, b: SessionData): Boolean {
        // 情况 1：a 在 b 开始后快速结束（1500ms 内）→ 切换
        if (a.endMs - b.startMs <= SWITCH_THRESHOLD_MS) return true
        // 情况 2：a 是未闭合会话（崩溃/强制停止，b 是其后的"最后写入者"）→ 切换
        // （真并行的 a 一定会有自己的退后台事件，不会是 openEnded）
        if (a.openEnded) return true
        return false
    }

    /**
     * 重叠扫描（扫描线算法）：
     * 所有区间按开始时间排序后，维护活跃区间集合；
     * 每段"≥2 个应用同时活跃"的时间区间即一个并行段，
     * 相邻并行段（应用集合变化）切分，分别输出。
     */
    private fun scanOverlaps(sessions: List<SessionData>): List<ParallelGroupData> {
        // 事件点：开始(+1) / 结束(-1)，按时间处理
        data class Edge(val timeMs: Long, val session: SessionData, val isStart: Boolean)

        val edges = ArrayList<Edge>(sessions.size * 2)
        for (s in sessions) {
            edges.add(Edge(s.startMs, s, isStart = true))
            edges.add(Edge(s.endMs, s, isStart = false))
        }
        edges.sortWith(compareBy({ it.timeMs }, { !it.isStart }))

        val active = LinkedHashMap<String, SessionData>() // 活跃会话（保序）
        val groups = ArrayList<ParallelGroupData>()
        var overlapStart: Long? = null // 当前并行段起点

        for (edge in edges) {
            if (edge.isStart) {
                active[edge.session.packageName] = edge.session
            } else {
                // 同包名可能有多个会话（不应发生，防御性处理：按包名移除即可）
                active.remove(edge.session.packageName)
            }

            if (active.size >= 2 && overlapStart == null) {
                // 并行段开始
                overlapStart = edge.timeMs
            } else if (active.size < 2 && overlapStart != null) {
                // 并行段结束：输出一段并行组
                val start = overlapStart
                val end = edge.timeMs
                overlapStart = null
                if (end - start >= MIN_SESSION_DURATION_MS) {
                    groups.add(buildGroup(start, end, active.keys.toList(), sessions))
                }
            }
        }
        return groups
    }

    /** 构建并行组并计算置信度 */
    private fun buildGroup(
        startMs: Long,
        endMs: Long,
        packages: List<String>,
        allSessions: List<SessionData>,
    ): ParallelGroupData {
        val confidence = when {
            // 至少两个应用与本并行段有高重合度 → 高置信（分屏）
            packages.count { pkg -> overlapRatio(pkg, startMs, endMs, allSessions) >= HIGH_CONFIDENCE_OVERLAP_RATIO } >= 2 ->
                CONFIDENCE_HIGH
            // 部分重合（如画中画视频应用长期驻留）→ 低置信"疑似"
            packages.any { pkg -> overlapRatio(pkg, startMs, endMs, allSessions) > 0 } -> CONFIDENCE_LOW
            // 兜底
            else -> CONFIDENCE_MEDIUM
        }
        return ParallelGroupData(startMs, endMs, packages, confidence)
    }

    /**
     * 计算某应用与并行段 [startMs, endMs) 的重合度
     * = 该应用在此段内的活跃时长 / 该应用在段内涉及的区间时长
     * （高 = 应用长时间与并行段共存，典型分屏；低 = 短暂路过，疑似画中画）
     */
    private fun overlapRatio(
        packageName: String,
        startMs: Long,
        endMs: Long,
        allSessions: List<SessionData>,
    ): Double {
        var intersectMs = 0L
        var totalMs = 0L
        for (s in allSessions) {
            if (s.packageName != packageName) continue
            val segStart = maxOf(s.startMs, startMs)
            val segEnd = minOf(s.endMs, endMs)
            if (segEnd > segStart) intersectMs += segEnd - segStart
            // 该应用与并行段有交集的区间总时长
            if (s.endMs > startMs && s.startMs < endMs) totalMs += s.durationMs
        }
        if (totalMs <= 0) return 0.0
        return intersectMs.toDouble() / totalMs
    }
}
