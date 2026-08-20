// 时迹 —— 原始事件表实体（明细层）
// 由系统事件流（queryEvents）增量写入，是唯一权威数据源

package com.shiji.trace.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// —— 事件类型常量（与系统 UsageEvents.Event 的类型号一一对应，只存需要的）——
// 注意：类型号必须与系统一致（以 SDK android.jar 中 UsageEvents$Event 的常量为准，
// 凭记忆写过两版错的：7/9/10 与 11/13/15，都导致锁屏误判/幽灵会话）。
// 官方值：1=进前台 2=退后台 11=待机桶变更(噪音) 15=亮屏 16=熄屏 17=锁屏 18=解锁 26=关机
/** 应用移动到前台（Activity 可见）—— 会话开区间 */
const val EVENT_MOVE_TO_FOREGROUND = 1
/** 应用移动到后台（Activity 不可见）—— 会话关区间 */
const val EVENT_MOVE_TO_BACKGROUND = 2
/** 屏幕非交互（息屏）—— 关闭全部活跃会话 */
const val EVENT_SCREEN_NON_INTERACTIVE = 16
/** 锁屏界面显示 —— 关闭全部活跃会话 */
const val EVENT_KEYGUARD_SHOWN = 17
/** 设备关机 —— 关闭全部活跃会话 */
const val EVENT_DEVICE_SHUTDOWN = 26

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
