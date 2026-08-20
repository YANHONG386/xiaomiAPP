// 时迹 —— 应用详情页视图模型
// 提供：近 14 天每日时长曲线、24 小时时段分布、会话列表

package com.shiji.trace.ui.screens.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shiji.trace.core.di.AppContainer
import com.shiji.trace.data.db.entity.AppSessionEntity
import com.shiji.trace.data.repository.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 详情页固定查看窗口：近 14 天 */
private const val DETAIL_DAYS = 14

/** 应用详情页界面状态 */
data class AppDetailUiState(
    val loading: Boolean = true,
    /** 每日时长曲线（date, 时长） */
    val dailyTotals: List<Pair<String, Long>> = emptyList(),
    /** 24 小时时段分布（hour, 时长） */
    val hourly: List<Pair<Int, Long>> = emptyList(),
    /** 会话列表（近 14 天） */
    val sessions: List<AppSessionEntity> = emptyList(),
    /** 段内总时长 */
    val totalTimeMs: Long = 0,
)

/**
 * 应用详情页视图模型
 * 根据包名查询近 14 天的使用数据（曲线 + 时段分布 + 会话明细）
 */
class AppDetailViewModel(
    container: AppContainer,
    private val packageName: String,
) : ViewModel() {

    private val repository: UsageRepository = container.repository

    // 注意：日期格式化器必须声明在 init 块之前！
    // Kotlin 属性按声明顺序初始化：若放在类尾部，init 里 load() 调用它时还是 null，
    // 直接 NPE（与 StatsViewModel 同款崩溃：一切到详情页就闪退）
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 加载近 14 天数据 */
    private fun load() {
        viewModelScope.launch {
            _uiState.value = AppDetailUiState(loading = true)
            val now = System.currentTimeMillis()
            val startMs = startOfDay(now) - (DETAIL_DAYS - 1) * 24L * 60 * 60 * 1000
            val startDate = dateFormat.format(Date(startMs))
            val endDate = dateFormat.format(Date(now))

            // 三个查询并行执行，互不依赖
            val daily = repository.appDailyTotals(packageName, startDate, endDate)
            val hourly = repository.appHourlyDistribution(packageName, startMs, now)
            val sessions = repository.appSessions(packageName, startMs, now)

            _uiState.value = AppDetailUiState(
                loading = false,
                dailyTotals = daily.map { it.date to it.totalTimeMs },
                hourly = hourly.map { it.hour to it.totalMs },
                sessions = sessions,
                totalTimeMs = sessions.sumOf { it.durationMs },
            )
        }
    }

    /** 当天 0 点毫秒 */
    private fun startOfDay(ms: Long): Long {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 工厂（注入 AppContainer + 包名参数） */
    companion object {
        fun factory(container: AppContainer, packageName: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppDetailViewModel(container, packageName) as T
            }
    }
}
