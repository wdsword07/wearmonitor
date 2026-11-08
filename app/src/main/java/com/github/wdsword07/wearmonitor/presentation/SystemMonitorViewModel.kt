package com.github.wdsword07.wearmonitor.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.wdsword07.wearmonitor.data.model.MetricDetailType
import com.github.wdsword07.wearmonitor.data.model.SystemSnapshot
import com.github.wdsword07.wearmonitor.data.monitor.SystemMonitorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class SystemMonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SystemMonitorRepository.getInstance(application)
    private val isScrollingState = MutableStateFlow(false)

    // 当前选中的二级详情页面，null 表示主面板
    private val _currentDetailType = MutableStateFlow<MetricDetailType?>(null)
    val currentDetailType: StateFlow<MetricDetailType?> = _currentDetailType.asStateFlow()

    // 近 1 分钟历史记录（30 个点，每点 2 秒）
    private val _history = MutableStateFlow<List<SystemSnapshot>>(emptyList())
    val history: StateFlow<List<SystemSnapshot>> = _history.asStateFlow()

    val snapshotState: StateFlow<SystemSnapshot> = flow {
        repository.getSnapshotFlow(pollIntervalMs = 2000L).collect { snapshot ->
            // 追加进 1 分钟历史滑动窗口
            val currentList = _history.value
            val updated = if (currentList.size >= 30) {
                currentList.drop(1) + snapshot
            } else {
                currentList + snapshot
            }
            _history.value = updated

            // 如果用户正在滑动，等待滚动平息后再向下游发射更新，杜绝滑动掉帧
            isScrollingState.first { !it }
            emit(snapshot)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 3000L),
        initialValue = SystemSnapshot()
    )

    fun openDetail(type: MetricDetailType) {
        _currentDetailType.value = type
    }

    fun closeDetail(): Boolean {
        if (_currentDetailType.value != null) {
            _currentDetailType.value = null
            return true
        }
        return false
    }

    fun setScrolling(scrolling: Boolean) {
        isScrollingState.value = scrolling
    }
}
