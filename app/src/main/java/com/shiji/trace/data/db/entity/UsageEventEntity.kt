// 时迹 —— 原始事件表实体（明细层）
// 由系统事件流（queryEvents）增量写入，是唯一权威数据源

package com.shiji.trace.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// —— 事件类型常量（与系统 UsageEvents.Event 的类型号一致，只存需要的）——
/** 应用进入前台（Activity 可见） */
const val EVENT_RESUMED = 1
/** 应用退到后台（Activity 不可见） */
const val EVENT_PAUSED = 2
/** 应用移动到前台（Activity 启动） */
const val EVENT_MOVE_TO_FOREGROUND = 5
/** 应用移动到后台 */
const val EVENT_MOVE_TO_BACKGROUND = 6
/** 锁屏界面显示 */
const val EVENT_KEYGUARD_SHOWN = 7
/** 锁屏界面隐藏 */
const val EVENT_KEYGUARD_HIDDEN = 8
/** 屏幕关闭（非交互） */
const val EVENT_SCREEN_NON_INTERACTIVE = 9
/** 设备关机 */
const val EVENT_DEVICE_SHUTDOWN = 10

/**
 * 使用事件实体
 * 存储系统上报的每个应用前台/后台切换事件
 *
 * 去重设计：四字段唯一索引 (eventTimeMs, packageName, eventType, className)，
 * 配合 INSERT OR IGNORE 实现幂等写入（重复同步不会产生脏数据）。
 * 注意：className 必须用空串而非 NULL——SQLite 唯一索引中 NULL 互不冲突，会导致去重失效。
 */
@Entity(
    tableName = "usage_event",
    indices = [
        // 幂等去重核心索引（四字段唯一）
        Index(
            value = ["eventTimeMs", "packageName", "eventType", "className"],
            unique = true
        ),
        // 按时间查询（增量同步）
        Index(value = ["eventTimeMs"]),
        // 按日期归档/清理
        Index(value = ["date"]),
        // 按应用查询
        Index(value = ["packageName", "eventTimeMs"]),
    ]
)
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 事件发生时间（epoch 毫秒） */
    @ColumnInfo(name = "eventTimeMs")
    val eventTimeMs: Long,

    /** 应用包名 */
    @ColumnInfo(name = "packageName")
    val packageName: String,

    /** 事件类型（见上方 EVENT_* 常量） */
    @ColumnInfo(name = "eventType")
    val eventType: Int,

    /** 组件类名（参与去重键；用空串默认值避免 NULL 破坏唯一索引） */
    @ColumnInfo(name = "className", defaultValue = "")
    val className: String = "",

    /** 归档日期 yyyy-MM-dd（写入时按本地时区固化，避免时区变化后重算出错） */
    @ColumnInfo(name = "date")
    val date: String,
)
