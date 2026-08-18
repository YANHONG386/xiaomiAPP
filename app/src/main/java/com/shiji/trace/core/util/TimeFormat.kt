// 时迹 —— 时间格式化工具（纯 Kotlin，可单测）
// 统一时长与时间的显示格式

package com.shiji.trace.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时长格式化工具
 */
object TimeFormat {

    /** 时长格式化：X小时Y分钟（如 "2小时30分钟"） */
    fun formatDuration(ms: Long): String {
        val totalMinutes = ms / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
            hours > 0 -> "${hours}小时"
            minutes > 0 -> "${minutes}分钟"
            else -> "不足1分钟"
        }
    }

    /** 简短时长：Xh Ym（如 "2h30m"） */
    fun formatDurationShort(ms: Long): String {
        val totalMinutes = ms / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分"
            hours > 0 -> "${hours}小时"
            minutes > 0 -> "${minutes}分钟"
            else -> "${ms / 1000}秒"
        }
    }

    /** 时间点：HH:mm（如 "14:30"） */
    fun formatTime(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    /** 时间点：HH:mm:ss（时间线刻度用） */
    fun formatTimeSeconds(ms: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

    /** 百分比：保留一位小数（如 "23.5%"） */
    fun formatPercent(part: Long, total: Long): String {
        if (total <= 0) return "0%"
        val pct = part * 100.0 / total
        return String.format(Locale.getDefault(), "%.1f%%", pct)
    }
}
