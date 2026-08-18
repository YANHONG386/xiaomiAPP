// 时迹 —— 原始事件表数据访问接口

package com.shiji.trace.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shiji.trace.data.db.entity.UsageEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * 原始事件表数据访问接口
 * 增删查；幂等写入靠唯一索引 + IGNORE（重复同步不产生脏数据）
 */
@Dao
interface UsageEventDao {

    /**
     * 批量插入事件（幂等：已存在的事件自动跳过）
     * @return 实际插入的行数
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<UsageEventEntity>): List<Long>

    /**
     * 按时间段查询事件（增量同步用）
     * @param startMs 起始时间（含）
     * @param endMs 结束时间（含）
     */
    @Query(
        "SELECT * FROM usage_event WHERE eventTimeMs BETWEEN :startMs AND :endMs " +
            "ORDER BY eventTimeMs ASC"
    )
    suspend fun queryBetween(startMs: Long, endMs: Long): List<UsageEventEntity>

    /**
     * 查询某时间之后的最大事件时间（用于游标推进）
     * @return 该时间段内最大 eventTimeMs，无数据返回 null
     */
    @Query("SELECT MAX(eventTimeMs) FROM usage_event WHERE eventTimeMs BETWEEN :startMs AND :endMs")
    suspend fun maxEventTimeBetween(startMs: Long, endMs: Long): Long?

    /** 按日期查询全部事件（会话重建/并行检测用） */
    @Query("SELECT * FROM usage_event WHERE date = :date ORDER BY eventTimeMs ASC")
    suspend fun queryByDate(date: String): List<UsageEventEntity>

    /** 删除早于某时间的事件（30 天滚动清理） */
    @Query("DELETE FROM usage_event WHERE eventTimeMs < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long): Int

    /** 统计事件总数（调试用） */
    @Query("SELECT COUNT(*) FROM usage_event")
    suspend fun count(): Int
}
