// 时迹 —— 本地数据库定义
// 五张表：原始事件 / 会话区间 / 每日快照 / 应用信息 / 并行结果

package com.shiji.trace.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shiji.trace.data.db.dao.AppInfoDao
import com.shiji.trace.data.db.dao.AppSessionDao
import com.shiji.trace.data.db.dao.DailySnapshotDao
import com.shiji.trace.data.db.dao.ParallelGroupDao
import com.shiji.trace.data.db.dao.UsageEventDao
import com.shiji.trace.data.db.entity.AppInfoEntity
import com.shiji.trace.data.db.entity.AppSessionEntity
import com.shiji.trace.data.db.entity.DailySnapshotEntity
import com.shiji.trace.data.db.entity.ParallelGroupEntity
import com.shiji.trace.data.db.entity.UsageEventEntity

/**
 * 时迹数据库
 * 版本 1：初始五表结构
 */
@Database(
    entities = [
        UsageEventEntity::class,      // 原始事件（明细层，30 天滚动）
        AppSessionEntity::class,      // 会话区间（时间线主干，30 天滚动）
        DailySnapshotEntity::class,   // 每日快照（长期保底，永不清理）
        AppInfoEntity::class,         // 应用信息（永久）
        ParallelGroupEntity::class,   // 并行结果（永久）
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageEventDao(): UsageEventDao
    abstract fun appSessionDao(): AppSessionDao
    abstract fun dailySnapshotDao(): DailySnapshotDao
    abstract fun appInfoDao(): AppInfoDao
    abstract fun parallelGroupDao(): ParallelGroupDao

    companion object {
        /** 数据库文件名 */
        const val DATABASE_NAME = "shiji.db"

        /**
         * 创建数据库实例（单例）
         * @param context 应用上下文
         */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    // 本应用单进程读写，无并发竞争，可关掉主线程写限制的检查
                    // （保持默认即可，此处留注释说明）
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
