package com.github.wdsword07.wearmonitor.data.monitor

import android.content.Context
import com.github.wdsword07.wearmonitor.data.model.ProcessEntry
import com.github.wdsword07.wearmonitor.data.model.ProcessSummary
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
    private val systemInfoMonitor = SystemInfoMonitor(appContext)
    private val processMonitor = ProcessMonitor(appContext)

    suspend fun getSnapshot(): SystemSnapshot = coroutineScope {
        val rootDeferred = async { RootShell.isRootAvailable() }
        val cpuDeferred = async { cpuMonitor.sampleCpuInfo() }
        val thermalDeferred = async { thermalMonitor.sampleThermalInfo() }
        val batteryDeferred = async { batteryMonitor.sampleBatteryInfo() }
        val memoryDeferred = async { memoryStorageMonitor.sampleMemoryInfo() }
        val storageDeferred = async { memoryStorageMonitor.sampleStorageInfo() }

        val isRoot = rootDeferred.await()
        val deviceInfo = systemInfoMonitor.getDeviceInfo(isRoot)

        SystemSnapshot(
            timestamp = System.currentTimeMillis(),
            isRootGranted = isRoot,
            cpu = cpuDeferred.await(),
            thermal = thermalDeferred.await(),
            battery = batteryDeferred.await(),
            memory = memoryDeferred.await(),
            storage = storageDeferred.await(),
            deviceInfo = deviceInfo
        )
    }

    fun getSnapshotFlow(pollIntervalMs: Long = 2000L): Flow<SystemSnapshot> = flow {
        while (true) {
            emit(getSnapshot())
            delay(pollIntervalMs)
        }
    }.flowOn(Dispatchers.IO)

    /** 进程总数摘要：默认 10s 刷新，供主面板入口卡片使用 */
    suspend fun getProcessSummary(forceRefresh: Boolean = false): ProcessSummary =
        processMonitor.getSummary(forceRefresh = forceRefresh)

    /** 完整进程列表：默认 2.5s 刷新，供进程管理二级页使用 */
    suspend fun getProcesses(forceRefresh: Boolean = false): List<ProcessEntry> =
        processMonitor.getProcesses(forceRefresh = forceRefresh)

    suspend fun killProcess(entry: ProcessEntry): Boolean = processMonitor.killProcess(entry)

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
