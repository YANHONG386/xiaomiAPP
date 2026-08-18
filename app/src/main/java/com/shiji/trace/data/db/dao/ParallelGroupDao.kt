// 时迹 —— 并行结果表数据访问接口

package com.shiji.trace.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shiji.trace.data.db.entity.ParallelGroupEntity
import kotlinx.coroutines.flow.Flow

/**
 * 并行结果表数据访问接口
 * 当日实时计算，历史按需计算后缓存
 */
@Dao
interface ParallelGroupDao {

    /** 批量写入并行组（冲突覆盖） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<ParallelGroupEntity>)

    /** 查询某日并行组（时间线页并行徽标） */
    @Query("SELECT * FROM parallel_group WHERE date = :date ORDER BY startMs ASC")
    suspend fun queryByDate(date: String): List<ParallelGroupEntity>

    /** 响应式查询某日并行组 */
    @Query("SELECT * FROM parallel_group WHERE date = :date ORDER BY startMs ASC")
    fun observeByDate(date: String): Flow<List<ParallelGroupEntity>>

    /** 删除某日全部并行组（重算前调用） */
    @Query("DELETE FROM parallel_group WHERE date = :date")
    suspend fun deleteByDate(date: String)

    /** 更新置信度（用户手动修正后） */
    @Query("UPDATE parallel_group SET confidence = :confidence WHERE id = :id")
    suspend fun updateConfidence(id: Long, confidence: Int)
}
