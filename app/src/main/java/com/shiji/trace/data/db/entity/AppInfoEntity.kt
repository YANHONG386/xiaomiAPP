// 时迹 —— 应用元信息缓存表实体
// 缓存应用名、系统应用标记、用户排除项，避免每次查询包管理器的开销

package com.shiji.trace.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用元信息实体
 * - label：应用名缓存（显示用）
 * - isSystem：系统应用标记（launcher、systemui 等不参与排行与并行检测）
 * - excluded：用户手动排除（不参与任何统计）
 * 图标不落库：用内存 LruCache + PackageManager 加载，滚动流畅且无文件管理复杂度
 */
@Entity(tableName = "app_info")
data class AppInfoEntity(
    /** 应用包名（主键） */
    @PrimaryKey
    @ColumnInfo(name = "packageName")
    val packageName: String,

    /** 应用显示名（缓存） */
    @ColumnInfo(name = "label")
    val label: String,

    /** 是否系统应用 */
    @ColumnInfo(name = "isSystem", defaultValue = "0")
    val isSystem: Boolean = false,

    /** 用户是否手动排除（不参与统计） */
    @ColumnInfo(name = "excluded", defaultValue = "0")
    val excluded: Boolean = false,

    /** 首次出现时间（epoch 毫秒） */
    @ColumnInfo(name = "firstSeenMs")
    val firstSeenMs: Long,

    /** 最近出现时间（epoch 毫秒） */
    @ColumnInfo(name = "lastSeenMs")
    val lastSeenMs: Long,
)
