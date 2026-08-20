// 时迹 —— 会话区间表数据访问接口

package com.shiji.trace.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shiji.trace.data.db.entity.AppSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 会话区间表数据访问接口
 * 重建策略：删除当日全量 → 重算插入（幂等简单，避免增量边界错误）
 */
@Dao
interface AppSessionDao {

    /** 批量插入会话（重建后使用，冲突覆盖） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<AppSessionEntity>)

    /** 删除某日全部会话（重建前调用） */
    @Query("DELETE FROM app_session WHERE date = :date")
    suspend fun deleteByDate(date: String)

    /** 查询某日全部会话（时间线页） */
    @Query("SELECT * FROM app_session WHERE date = :date ORDER BY startTimeMs ASC")
    suspend fun queryByDate(date: String): List<AppSessionEntity>

    /** 查询某日全部会话（响应式，时间线页实时刷新） */
    @Query("SELECT * FROM app_session WHERE date = :date ORDER BY startTimeMs ASC")
    fun observeByDate(date: String): Flow<List<AppSessionEntity>>

    /** 查询某应用在某日/某时间段的会话（应用详情页） */
    @Query(
        "SELECT * FROM app_session WHERE packageName = :packageName " +
            "AND startTimeMs BETWEEN :startMs AND :endMs ORDER BY startTimeMs ASC"
    )
    suspend fun queryForApp(packageName: String, startMs: Long, endMs: Long): List<AppSessionEntity>

    /** 查询某时间段内全部会话（统计洞察计算：最长使用、深夜占比） */
    @Query("SELECT * FROM app_session WHERE startTimeMs BETWEEN :startMs AND :endMs")
    suspend fun queryAllBetween(startMs: Long, endMs: Long): List<AppSessionEntity>

    /** 删除早于某时间的会话（30 天滚动清理） */
    @Query("DELETE FROM app_session WHERE endTimeMs < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long): Int

    /** 统计某日会话总数（调试用） */
    @Query("SELECT COUNT(*) FROM app_session WHERE date = :date")
    suspend fun countByDate(date: String): Int
}
