// 时迹 —— 应用信息表数据访问接口

package com.shiji.trace.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shiji.trace.data.db.entity.AppInfoEntity
import kotlinx.coroutines.flow.Flow

/**
 * 应用信息表数据访问接口
 * 缓存应用名、系统标记、用户排除项
 */
@Dao
interface AppInfoDao {

    /** 批量写入应用信息（冲突覆盖为最新） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(infos: List<AppInfoEntity>)

    /** 查询单个应用信息（按需查询） */
    @Query("SELECT * FROM app_info WHERE packageName = :packageName")
    suspend fun query(packageName: String): AppInfoEntity?

    /** 查询全部应用信息（设置页排除管理） */
    @Query("SELECT * FROM app_info ORDER BY label ASC")
    fun observeAll(): Flow<List<AppInfoEntity>>

    /** 更新排除标记（用户手动排除/恢复） */
    @Query("UPDATE app_info SET excluded = :excluded WHERE packageName = :packageName")
    suspend fun setExcluded(packageName: String, excluded: Boolean)
}
