package com.github.wdsword07.wearmonitor.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.wdsword07.wearmonitor.data.model.SystemSnapshot
import com.github.wdsword07.wearmonitor.data.monitor.SystemMonitorRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SystemMonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SystemMonitorRepository.getInstance(application)

    val snapshotState: StateFlow<SystemSnapshot> = repository.getSnapshotFlow(pollIntervalMs = 1500L)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 3000L),
            initialValue = SystemSnapshot()
        )
}
