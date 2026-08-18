// 时迹 —— 每日快照表实体（长期保底层）
// 即使系统剪裁事件、用户长期不开应用，历史日期依然完整

package com.shiji.trace.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每日快照实体
 * 按（日期 + 应用）聚合每日使用时长，**永不清理**。
 *
 * 双轨来源：
 * 1. 事件流汇总（精确）
 * 2. 系统聚合口径（queryUsageStats(INTERVAL_DAILY)）交叉校验——
 *    系统聚合跨重启持久，而事件流在重启后会丢，快照层正是为此存在。
 */
@Entity(
    tableName = "daily_snapshot",
    primaryKeys = ["date", "packageName"],
    indices = [
        // 按日期查询（统计页）
        Index(value = ["date"]),
        // 按应用查询（应用详情页）
        Index(value = ["packageName", "date"]),
    ]
)
data class DailySnapshotEntity(
    /** 日期 yyyy-MM-dd（与事件表同规则固化） */
    @ColumnInfo(name = "date")
    val date: String,

    /** 应用包名 */
    @ColumnInfo(name = "packageName")
    val packageName: String,

    /** 当日总使用时长（毫秒） */
    @ColumnInfo(name = "totalTimeMs")
    val totalTimeMs: Long,

    /** 当日会话次数 */
    @ColumnInfo(name = "sessionCount")
    val sessionCount: Int,

    /** 当日最后一次使用时间（epoch 毫秒） */
    @ColumnInfo(name = "lastUsedMs")
    val lastUsedMs: Long,

    /** 快照记录时间（epoch 毫秒，用于判断快照新鲜度） */
    @ColumnInfo(name = "recordedAtMs")
    val recordedAtMs: Long,
)
