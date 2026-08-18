// 时迹 —— 并行结果缓存表实体
// 存储并行检测结果：同一时间段使用了哪些应用（分屏等场景）

package com.shiji.trace.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// —— 置信度常量 ——
/** 低置信（疑似画中画等，需用户确认） */
const val CONFIDENCE_LOW = 1
/** 中置信 */
const val CONFIDENCE_MEDIUM = 2
/** 高置信（真分屏） */
const val CONFIDENCE_HIGH = 3

/**
 * 并行组实体
 * 表示一段时间内同时使用的多个应用（分屏、画中画等场景）
 *
 * 计算策略：当日实时算，历史按需算后缓存（索引 (date) 加速）
 */
@Entity(
    tableName = "parallel_group",
    indices = [
        Index(value = ["date"]),
    ]
)
data class ParallelGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 归档日期 yyyy-MM-dd */
    @ColumnInfo(name = "date")
    val date: String,

    /** 并行开始时间（epoch 毫秒） */
    @ColumnInfo(name = "startMs")
    val startMs: Long,

    /** 并行结束时间（epoch 毫秒） */
    @ColumnInfo(name = "endMs")
    val endMs: Long,

    /** 并行持续时长（毫秒） */
    @ColumnInfo(name = "durationMs")
    val durationMs: Long,

    /** 并行应用包名列表（JSON 数组，支持 2-3 个并行） */
    @ColumnInfo(name = "packages")
    val packagesJson: String,

    /** 置信度（见上方 CONFIDENCE_* 常量） */
    @ColumnInfo(name = "confidence")
    val confidence: Int,
)
