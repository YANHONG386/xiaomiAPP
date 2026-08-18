// 时迹 —— 每日快照表数据访问接口

package com.shiji.trace.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shiji.trace.data.db.entity.DailySnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * 每日快照表数据访问接口
 * 快照**永不清理**，是历史数据的长期保底层
 */
@Dao
interface DailySnapshotDao {

    /** 写入快照（同日期同应用冲突时覆盖为最新值） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(snapshots: List<DailySnapshotEntity>)

    /** 查询某日全部快照（统计页） */
    @Query("SELECT * FROM daily_snapshot WHERE date = :date ORDER BY totalTimeMs DESC")
    suspend fun queryByDate(date: String): List<DailySnapshotEntity>

    /** 响应式查询某日快照（今日页实时刷新） */
    @Query("SELECT * FROM daily_snapshot WHERE date = :date ORDER BY totalTimeMs DESC")
    fun observeByDate(date: String): Flow<List<DailySnapshotEntity>>

    /** 查询某日期段内每日总时长（周/月统计柱状图） */
    @Query(
        "SELECT date, SUM(totalTimeMs) AS totalTimeMs FROM daily_snapshot " +
            "WHERE date BETWEEN :startDate AND :endDate GROUP BY date ORDER BY date ASC"
    )
    suspend fun queryTotalsBetween(startDate: String, endDate: String): List<DailyTotalRow>

    /** 查询某日期段内应用聚合（周/月排行） */
    @Query(
        "SELECT packageName, SUM(totalTimeMs) AS totalTimeMs FROM daily_snapshot " +
            "WHERE date BETWEEN :startDate AND :endDate GROUP BY packageName " +
            "ORDER BY totalTimeMs DESC"
    )
    suspend fun queryAppTotalsBetween(startDate: String, endDate: String): List<AppTotalRow>

    /** 数据行（按日期聚合结果） */
    data class DailyTotalRow(val date: String, val totalTimeMs: Long)

    /** 数据行（按应用聚合结果） */
    data class AppTotalRow(val packageName: String, val totalTimeMs: Long)
}
