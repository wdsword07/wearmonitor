package com.github.wdsword07.wearmonitor.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.wdsword07.wearmonitor.data.model.MetricDetailType
import com.github.wdsword07.wearmonitor.data.model.ProcessEntry
import com.github.wdsword07.wearmonitor.data.model.ProcessSort
import com.github.wdsword07.wearmonitor.data.model.ProcessSummary
import com.github.wdsword07.wearmonitor.data.model.SystemSnapshot
import com.github.wdsword07.wearmonitor.data.monitor.SystemMonitorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    // 进程管理：排序方式、列表、摘要
    private val _processSort = MutableStateFlow(ProcessSort.MEMORY)
    val processSort: StateFlow<ProcessSort> = _processSort.asStateFlow()

    private val _processes = MutableStateFlow<List<ProcessEntry>>(emptyList())
    val processes: StateFlow<List<ProcessEntry>> = _processes.asStateFlow()

    private val summaryRefreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * 主面板入口卡片：进程总数摘要。
     * 默认 10s 一次，结束进程后可通过 [summaryRefreshTrigger] 立即刷新；
     * 交给 ProcessMonitor 内部缓存，详情页轮询也会顺带刷新。
     */
    val processSummary: StateFlow<ProcessSummary> = merge(
        flow {
            while (true) {
                emit(Unit)
                delay(PROCESS_SUMMARY_INTERVAL_MS)
            }
        },
        summaryRefreshTrigger
    ).map { repository.getProcessSummary() }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 3000L),
            initialValue = ProcessSummary()
        )

    private var processPollingJob: Job? = null

    fun openDetail(type: MetricDetailType) {
        _currentDetailType.value = type
        if (type == MetricDetailType.PROCESSES) {
            startProcessPolling()
        } else {
            stopProcessPolling()
        }
    }

    fun closeDetail(): Boolean {
        if (_currentDetailType.value != null) {
            _currentDetailType.value = null
            stopProcessPolling()
            return true
        }
        return false
    }

    fun toggleProcessSort() {
        _processSort.value = if (_processSort.value == ProcessSort.MEMORY) ProcessSort.CPU else ProcessSort.MEMORY
    }

    fun killProcess(entry: ProcessEntry) {
        viewModelScope.launch {
            val success = repository.killProcess(entry)
            if (success) {
                // 结束后立刻重新枚举，避免列表仍显示已死进程
                _processes.value = repository.getProcesses(forceRefresh = true)
                summaryRefreshTrigger.tryEmit(Unit)
            }
        }
    }

    private fun startProcessPolling() {
        if (processPollingJob?.isActive == true) return
        processPollingJob = viewModelScope.launch {
            var forceRefresh = true
            while (isActive) {
                val list = repository.getProcesses(forceRefresh = forceRefresh)
                forceRefresh = false
                // 与主面板一致的滚动静默策略，避免刷新打断滑动
                isScrollingState.first { !it }
                _processes.value = list
                delay(PROCESS_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopProcessPolling() {
        processPollingJob?.cancel()
        processPollingJob = null
    }

    fun setScrolling(scrolling: Boolean) {
        isScrollingState.value = scrolling
    }

    private companion object {
        const val PROCESS_SUMMARY_INTERVAL_MS = 10_000L
        const val PROCESS_POLL_INTERVAL_MS = 3_000L
    }
}
