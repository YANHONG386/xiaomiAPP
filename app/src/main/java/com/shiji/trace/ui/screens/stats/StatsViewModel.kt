// 时迹 —— 统计页视图模型
// 提供：日/周/月切换、柱状图数据、应用排行、统计洞察

package com.shiji.trace.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shiji.trace.core.di.AppContainer
import com.shiji.trace.data.repository.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 统计周期 */
enum class StatsPeriod { DAY, WEEK, MONTH }

/** 柱状图数据项（label 为应用包名或日期） */
data class BarEntry(
    val label: String,
    val valueMs: Long,
    /** true 时 label 是应用包名（需要解析为显示名） */
    val isPackage: Boolean = false,
)

/** 排行数据项 */
data class RankEntry(val packageName: String, val totalTimeMs: Long)

/** 统计页界面状态 */
data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.DAY,
    val loading: Boolean = true,
    /** 段内总使用时长（毫秒） */
    val totalTimeMs: Long = 0,
    /** 段内会话数 */
    val sessionCount: Int = 0,
    /** 柱状图数据（DAY=应用 Top，WEEK/MONTH=每日总时长） */
    val barData: List<BarEntry> = emptyList(),
    /** 应用排行（Top10 取前 10） */
    val ranking: List<RankEntry> = emptyList(),
    /** 统计洞察（最长使用 / 深夜占比） */
    val longestSession: Pair<String, Long>? = null,
    val nightRatio: Double = 0.0,
)

/**
 * 统计页视图模型
 * 切换周期时重新查询：柱状图 + 排行 + 洞察
 */
class StatsViewModel(private val container: AppContainer) : ViewModel() {

    private val repository: UsageRepository = container.repository

    // 注意：日期格式化器必须声明在 init 块之前！
    // Kotlin 属性按声明顺序初始化：若放在类尾部，init 里 selectPeriod 调用它时还是 null，
    // 直接 NPE（真机回归：一切到统计页就崩溃闪退，根因就是初始化顺序）
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        selectPeriod(StatsPeriod.DAY)
    }

    /** 切换统计周期（日/周/月），重新加载数据 */
    fun selectPeriod(period: StatsPeriod) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // 先清空旧数据，避免切换时闪现上一周期的内容
            _uiState.value = StatsUiState(period = period, loading = true)

            // 计算日期段：DAY=今天，WEEK=近 7 天，MONTH=近 30 天
            val days = when (period) {
                StatsPeriod.DAY -> 0
                StatsPeriod.WEEK -> 6
                StatsPeriod.MONTH -> 29
            }
            val dayMs = 24L * 60 * 60 * 1000
            val startMs = startOfDay(now) - days * dayMs
            val startDate = dateFormat.format(Date(startMs))
            val endDate = dateFormat.format(Date(now))

            // —— 柱状图：DAY 显示今日 Top 应用，WEEK/MONTH 显示每日总时长 ——
            val barData: List<BarEntry> = if (period == StatsPeriod.DAY) {
                repository.appTotals(endDate, endDate)
                    .take(7) // 柱状图最多 7 根，避免拥挤
                    .map { BarEntry(it.packageName, it.totalTimeMs, isPackage = true) }
            } else {
                repository.dailyTotals(startDate, endDate)
                    .map { BarEntry(it.date, it.totalTimeMs) }
            }

            // —— 排行：整段聚合 ——
            val ranking = repository.appTotals(startDate, endDate)
                .map { RankEntry(it.packageName, it.totalTimeMs) }

            // —— 洞察 ——
            val insights = repository.insights(startMs, now)

            _uiState.value = StatsUiState(
                period = period,
                loading = false,
                totalTimeMs = ranking.sumOf { it.totalTimeMs },
                sessionCount = insights.sessionCount,
                barData = barData,
                ranking = ranking,
                longestSession = insights.longestSession,
                nightRatio = insights.nightRatio,
            )
        }
    }

    /** 当天 0 点毫秒（时区本地） */
    private fun startOfDay(ms: Long): Long {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 工厂（注入 AppContainer） */
    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatsViewModel(container) as T
            }
    }
}
