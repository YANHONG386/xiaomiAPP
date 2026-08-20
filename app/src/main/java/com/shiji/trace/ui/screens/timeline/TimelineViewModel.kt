// 时迹 —— 时间线页视图模型
// 提供：某日会话列表、并行组（响应式）、日期切换、手动修正并行结果

package com.shiji.trace.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shiji.trace.core.di.AppContainer
import com.shiji.trace.data.db.entity.AppSessionEntity
import com.shiji.trace.data.db.entity.ParallelGroupEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 时间线页视图模型
 * - 当前查看的日期（默认今天，可横滑切换）
 * - 该日会话列表 + 并行组（响应式）
 * - 并行结果手动修正（把"疑似画中画"解除并行）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(private val container: AppContainer) : ViewModel() {

    /** 当前查看的日期（yyyy-MM-dd） */
    private val _selectedDate = MutableStateFlow(container.repository.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    /** 该日会话列表 */
    val sessions: StateFlow<List<AppSessionEntity>> =
        _selectedDate.flatMapLatest { date ->
            container.repository.observeSessions(date)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 该日并行组（仅展示未解除的；用户手动解除后 confidence=0 不再显示） */
    val parallelGroups: StateFlow<List<ParallelGroupEntity>> =
        _selectedDate.flatMapLatest { date ->
            container.repository.observeParallelGroups(date)
        }.map { list -> list.filter { it.confidence > 0 } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 切换日期 */
    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    /** 上一页 / 下一页 */
    fun moveDay(days: Int) {
        val repo = container.repository
        val millis = repo.parseDate(_selectedDate.value) + days * 86_400_000L
        selectDate(repo.todayDateFromMillis(millis))
    }

    /** 手动修正：解除某并行组（置信度改为低，UI 不再展示为并行） */
    fun dismissParallelGroup(id: Long) {
        viewModelScope.launch {
            container.database.parallelGroupDao().updateConfidence(id, 0)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TimelineViewModel(container) as T
            }
    }
}
