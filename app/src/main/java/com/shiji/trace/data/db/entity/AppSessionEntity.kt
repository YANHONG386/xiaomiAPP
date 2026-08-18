// 时迹 —— 会话区间表实体（时间线主干）
// 由事件流后处理生成，供时间线/今日/统计直接查询

package com.shiji.trace.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 应用会话实体
 * 表示某个应用的一次完整前台使用区间（开始时间 → 结束时间）
 *
 * 重建策略：每次同步后删除当日全量会话并重算（事件量小，幂等且简单，
 * 避免增量 upsert 的边界错误）。
 */
@Entity(
    tableName = "app_session",
    indices = [
        // 按日期+开始时间查询（时间线页）
        Index(value = ["date", "startTimeMs"]),
        // 按应用查询（应用详情页）
        Index(value = ["packageName", "startTimeMs"]),
    ]
)
data class AppSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 应用包名 */
    @ColumnInfo(name = "packageName")
    val packageName: String,

    /** 会话开始时间（epoch 毫秒） */
    @ColumnInfo(name = "startTimeMs")
    val startTimeMs: Long,

    /** 会话结束时间（epoch 毫秒） */
    @ColumnInfo(name = "endTimeMs")
    val endTimeMs: Long,

    /** 会话时长（毫秒） */
    @ColumnInfo(name = "durationMs")
    val durationMs: Long,

    /** 归档日期 yyyy-MM-dd */
    @ColumnInfo(name = "date")
    val date: String,

    /** 是否系统应用（桌面 launcher、systemui 等，默认不参与排行与并行检测） */
    @ColumnInfo(name = "isSystem", defaultValue = "0")
    val isSystem: Boolean = false,
)
