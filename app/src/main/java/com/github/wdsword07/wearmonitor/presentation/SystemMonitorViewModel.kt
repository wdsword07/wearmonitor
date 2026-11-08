package com.github.wdsword07.wearmonitor.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.wdsword07.wearmonitor.data.model.SystemSnapshot
import com.github.wdsword07.wearmonitor.data.monitor.SystemMonitorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class SystemMonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SystemMonitorRepository.getInstance(application)
    private val isScrollingState = MutableStateFlow(false)

    val snapshotState: StateFlow<SystemSnapshot> = flow {
        repository.getSnapshotFlow(pollIntervalMs = 2000L).collect { snapshot ->
            // 如果用户正在滑动屏幕或旋转表冠，等待滚动平息后再发射更新，避免滑动期间重组掉帧
            isScrollingState.first { !it }
            emit(snapshot)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 3000L),
        initialValue = SystemSnapshot()
    )

    fun setScrolling(scrolling: Boolean) {
        isScrollingState.value = scrolling
    }
}
