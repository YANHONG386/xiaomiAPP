// 时迹 —— 会话构建器（纯函数，无安卓依赖，可单测）
// 把系统事件流转换为"应用会话区间"列表

package com.shiji.trace.domain

import com.shiji.trace.data.db.entity.EVENT_DEVICE_SHUTDOWN
import com.shiji.trace.data.db.entity.EVENT_KEYGUARD_SHOWN
import com.shiji.trace.data.db.entity.EVENT_MOVE_TO_BACKGROUND
import com.shiji.trace.data.db.entity.EVENT_MOVE_TO_FOREGROUND
import com.shiji.trace.data.db.entity.EVENT_PAUSED
import com.shiji.trace.data.db.entity.EVENT_RESUMED
import com.shiji.trace.data.db.entity.EVENT_SCREEN_NON_INTERACTIVE

/** 最短有效会话时长（毫秒）。小于此值的会话丢弃（系统 UI 弹层等抖动） */
const val MIN_SESSION_DURATION_MS = 1_000L

/**
 * 简化事件（纯数据，供构建器使用）
 * 由同步引擎从系统事件流转换而来
 */
data class SessionEvent(
    /** 事件时间（epoch 毫秒） */
    val timeMs: Long,
    /** 应用包名 */
    val packageName: String,
    /** 事件类型（EVENT_* 常量） */
    val type: Int,
)

/**
 * 会话区间（纯数据）
 * @param openEnded 会话是否未闭合（应用崩溃/强制停止，没有收到退后台事件）
 *                  —— 用于并行检测的"最后写入者胜"截断判断
 */
data class SessionData(
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
    val openEnded: Boolean = false,
) {
    /** 会话时长（毫秒） */
    val durationMs: Long get() = endMs - startMs
}

/**
 * 会话构建器：事件流 → 会话区间
 *
 * 算法（单遍扫描 + 每应用状态机 idle/active）：
 * - 前台事件（RESUMED/MOVE_TO_FOREGROUND）：idle → 开新区间
 * - 后台事件（PAUSED/MOVE_TO_BACKGROUND）：active → 关区间
 * - 锁屏/熄屏/关机事件：关闭全部活跃区间（屏幕不可见，所有应用都算退出前台）
 * - 同应用连续前台事件（无后台事件间隔）：忽略（去重抖动）
 * - 未闭合区间：给一个结束时间（所有事件的最大时间），标记 openEnded
 * - 最短会话 < 1 秒：丢弃
 */
object SessionBuilder {

    /** 关闭全部区间的事件类型（屏幕不再可见） */
    private val CLOSE_ALL_TYPES = setOf(
        EVENT_KEYGUARD_SHOWN,
        EVENT_SCREEN_NON_INTERACTIVE,
        EVENT_DEVICE_SHUTDOWN,
    )

    /**
     * 构建会话列表
     * @param events 事件流（按时间升序；若未排序会自动排）
     * @return 会话区间列表（按开始时间升序），可能为空
     */
    fun build(events: List<SessionEvent>): List<SessionData> {
        if (events.isEmpty()) return emptyList()

        // 事件流按时间升序（系统查询本就有序，防御性排序保证正确性）
        val sorted = events.sortedBy { it.timeMs }
        // 记录所有事件的最大时间：用于给未闭合区间一个结束点
        val lastEventTime = sorted.last().timeMs

        // 每个应用当前活跃的会话（key: 包名）
        val openSessions = HashMap<String, SessionData>()
        val result = ArrayList<SessionData>()

        for (event in sorted) {
            when {
                // —— 屏幕级中断：关闭全部活跃区间 ——
                event.type in CLOSE_ALL_TYPES -> {
                    closeAll(openSessions, event.timeMs, result)
                }
                // —— 进入前台：开新区间 ——
                event.type == EVENT_RESUMED || event.type == EVENT_MOVE_TO_FOREGROUND -> {
                    // 该应用已活跃（连续前台事件）→ 忽略去重
                    if (event.packageName !in openSessions) {
                        openSessions[event.packageName] =
                            SessionData(event.packageName, event.timeMs, event.timeMs)
                    }
                }
                // —— 退到后台：关区间 ——
                event.type == EVENT_PAUSED || event.type == EVENT_MOVE_TO_BACKGROUND -> {
                    val open = openSessions.remove(event.packageName) ?: continue
                    // 结束时间取事件时间（该时刻应用已不可见）
                    closeSession(open, event.timeMs, result)
                }
            }
        }

        // 处理未闭合区间（应用崩溃等）：用最后事件时间截断
        closeAll(openSessions, lastEventTime, result)

        return result
    }

    /** 关闭全部活跃区间（锁屏/熄屏/关机/扫描结束） */
    private fun closeAll(
        openSessions: HashMap<String, SessionData>,
        closeTime: Long,
        result: MutableList<SessionData>,
    ) {
        if (openSessions.isEmpty()) return
        for (session in openSessions.values) {
            closeSession(session, closeTime, result, openEnded = true)
        }
        openSessions.clear()
    }

    /** 关闭单个区间：过滤过短会话后收入结果 */
    private fun closeSession(
        session: SessionData,
        closeTime: Long,
        result: MutableList<SessionData>,
        openEnded: Boolean = false,
    ) {
        val end = maxOf(closeTime, session.startMs)
        val closed = session.copy(endMs = end, openEnded = openEnded)
        // 过短会话丢弃（系统 UI 弹层等抖动，不是真实使用）
        if (closed.durationMs >= MIN_SESSION_DURATION_MS) {
            result.add(closed)
        }
    }
}
