// 时迹 —— 今日页视图模型
// 提供：今日总时长、应用列表、授权状态、同步触发

package com.shiji.trace.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shiji.trace.core.di.AppContainer
import com.shiji.trace.data.db.entity.DailySnapshotEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 今日页视图模型
 * - 今日快照列表（响应式：数据库变化自动刷新）
 * - 授权状态（每次回到页面时刷新检查）
 */
class TodayViewModel(private val container: AppContainer) : ViewModel() {

    /** 今日各应用使用数据（响应式） */
    val todaySnapshots: StateFlow<List<DailySnapshotEntity>> =
        container.repository.observeToday()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 授权状态 */
    private val _hasUsageAccess = MutableStateFlow(false)
    val hasUsageAccess: StateFlow<Boolean> = _hasUsageAccess.asStateFlow()

    init {
        refreshUsageAccess()
    }

    /** 刷新授权状态（从系统设置页返回时调用） */
    fun refreshUsageAccess() {
        val granted = container.repository.hasUsageAccess()
        _hasUsageAccess.value = granted
        if (granted) {
            // 授权成功后：触发回填/同步（Worker 注册由容器处理）
            container.triggerSync()
        }
    }

    /** 打开系统授权设置页 */
    fun openUsageAccessSettings() {
        container.usageStatsDataSource.openUsageAccessSettings()
    }

    /** 手动下拉刷新：重新同步数据 */
    fun refresh() {
        container.triggerSync()
    }

    /** 工厂（注入 AppContainer） */
    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TodayViewModel(container) as T
            }
    }
}
