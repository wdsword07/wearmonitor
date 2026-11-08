package com.github.wdsword07.wearmonitor.data.monitor

import android.content.Context
import com.github.wdsword07.wearmonitor.data.model.SystemSnapshot
import com.github.wdsword07.wearmonitor.data.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SystemMonitorRepository(context: Context) {
    private val appContext = context.applicationContext
    private val cpuMonitor = CpuMonitor()
    private val thermalMonitor = ThermalMonitor(appContext)
    private val batteryMonitor = BatteryMonitor(appContext)
    private val memoryStorageMonitor = MemoryStorageMonitor(appContext)

    suspend fun getSnapshot(): SystemSnapshot = coroutineScope {
        val rootDeferred = async { RootShell.isRootAvailable() }
        val cpuDeferred = async { cpuMonitor.sampleCpuInfo() }
        val thermalDeferred = async { thermalMonitor.sampleThermalInfo() }
        val batteryDeferred = async { batteryMonitor.sampleBatteryInfo() }
        val memoryDeferred = async { memoryStorageMonitor.sampleMemoryInfo() }
        val storageDeferred = async { memoryStorageMonitor.sampleStorageInfo() }

        SystemSnapshot(
            timestamp = System.currentTimeMillis(),
            isRootGranted = rootDeferred.await(),
            cpu = cpuDeferred.await(),
            thermal = thermalDeferred.await(),
            battery = batteryDeferred.await(),
            memory = memoryDeferred.await(),
            storage = storageDeferred.await()
        )
    }

    fun getSnapshotFlow(pollIntervalMs: Long = 2000L): Flow<SystemSnapshot> = flow {
        while (true) {
            emit(getSnapshot())
            delay(pollIntervalMs)
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        @Volatile
        private var instance: SystemMonitorRepository? = null

        fun getInstance(context: Context): SystemMonitorRepository {
            return instance ?: synchronized(this) {
                instance ?: SystemMonitorRepository(context).also { instance = it }
            }
        }
    }
}
