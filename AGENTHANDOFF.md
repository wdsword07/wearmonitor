# WearMonitor 项目交接文档 (Agent Handoff)

> 最近更新：`c43d418`（修复 su 会话 token 换行导致的“全量指标归零”重大 bug）
> 目的：切换到新会话/新窗口时，可仅凭本文档恢复完整上下文。

---

## 1. 项目概述与定位
* **项目名称**：WearMonitor (`com.github.wdsword07.wearmonitor`)
* **目标平台**：Wear OS (API 30 ~ 37)，针对已 Root 设备深度优化，同时兼容非 Root 状态下的基础系统监控。
* **产品核心形态**：
  1. **手表端系统面板主界面**：**Wear Compose Material 3** 构建，圆形表盘平铺卡片仪表盘。
     - 卡片顺序：`StatusHeaderCard`（标题/根状态，可点击）→ `CpuCard` → `ThermalCard` → `BatteryCard` → `MemoryCard`（RAM + Swap）→ `StorageCard`（ROM，独立拆分）。
  2. **系统环境页（SYSTEM_INFO）**：点击顶部 Title 卡片进入，展示设备固件、**深度 Root 特权信息**、屏幕规格、基带固件。
  3. **配套 Wear OS 磁贴（Tile）**：**ProtoLayout Material 3**，侧滑即览 CPU+RAM+电量温度，点击唤醒主应用。
  4. **各指标 1 分钟历史趋势二级页面**：CPU、温度、电池供电、RAM & Swap 均可点击进入（存储无趋势页）。
  5. **只读安全监控原则**：无任何破坏性控制功能，专注精准、极低开销的系统状态监控。

---

## 2. 系统核心架构与模块划分

```
wearmonitor/
├── app/src/main/
│   ├── java/com/github/wdsword07/wearmonitor/
│   │   ├── data/
│   │   │   ├── model/
│   │   │   │   └── SystemModels.kt            # CpuInfo(含 SoC 字段), ThermalInfo, BatteryInfo,
│   │   │   │                                  # MemoryInfo(含 Swap/ZRAM 字段), StorageInfo,
│   │   │   │                                  # SystemDeviceInfo, MetricDetailType, SystemSnapshot
│   │   │   ├── root/
│   │   │   │   └── RootShell.kt               # 单例持久化 su 管道；execCommand() 带 </dev/null、
│   │   │   │                                  # token 前置换行、独立线程硬超时；sysfs 失败黑名单
│   │   │   └── monitor/
│   │   │       ├── CpuMonitor.kt              # 全局使用率(/proc/stat + /proc/loadavg 兜底)、
│   │   │       │                              # 各核频率/调速器、SoC 平台/ABI/架构
│   │   │       ├── ThermalMonitor.kt          # SoC 温区探测缓存、电池温度（BatteryManager 优先）
│   │   │       ├── BatteryMonitor.kt          # 电量、电流(mA)、电压(mV)、功率(W)
│   │   │       ├── MemoryStorageMonitor.kt    # ActivityManager 原生内存、ZRAM 压缩比、StatFs 存储
│   │   │       ├── SystemInfoMonitor.kt       # 静态系统信息：后台协程探测 + 立即返回缓存；
│   │   │       │                              # 禁止嵌套 su；Magisk/KSU/APatch 识别
│   │   │       └── SystemMonitorRepository.kt # 单次快照 + 前台按需轮询 Flow（2s）
│   │   ├── presentation/
│   │   │   ├── MainActivity.kt                # 入口、SwipeToDismissBox 前后台切换、滚动防抖
│   │   │   ├── SystemMonitorViewModel.kt      # 状态流、30 点(60s)历史队列、滚动静默调度
│   │   │   ├── theme/
│   │   │   │   └── Theme.kt                   # MaterialTheme + dynamicColorScheme 系统动态配色
│   │   │   └── ui/
│   │   │       ├── MonitorCards.kt            # StatusHeaderCard(可点击) / CpuCard / ThermalCard /
│   │   │       │                              # BatteryCard / MemoryCard / StorageCard
│   │   │       ├── MetricTrendChart.kt        # 动态纵轴缩放 + Max/Min/Avg + 60s/30s/0s 横轴标尺
│   │   │       └── MetricDetailScreen.kt      # 5 类详情：SYSTEM_INFO / CPU(含 SoC) / THERMAL /
│   │   │                                      # BATTERY / MEMORY(RAM+Swap 双曲线 + ZRAM 明细)
│   │   └── tile/
│   │       └── MainTileService.kt             # ProtoLayout M3 磁贴
│   ├── res/                                   # 自适应矢量图标、Splash、各密度 WebP
│   └── keepRules/rules.keep                   # R8 保留规则（ViewModel/Startup/组件/模型）
├── wearmonitor_icon.svg                       # 512x512 Master 矢量图
├── build.gradle.kts / settings.gradle.kts     # 根工程 + Aliyun Maven 镜像
├── AGENTHANDOFF.md                            # 本文档
└── README.md
```

---

## 3. 关键优化与技术方案沉淀

### 3.1 消除滑动严重掉帧（Jank）
1. **彻底移除 `transformedHeight`**：避免每帧重算高度与卡片堆叠 Overdraw；改用原生 `TransformingLazyColumn` + `Arrangement.spacedBy(6.dp)` + 固定 `key`。
2. **消除反复 Fork `su`**：单例持久化 su 会话 + 普通 Java IO 优先（95%+ 文件直读）。
3. **滚动感知静默轮询**：监听 `listState.isScrollInProgress`，滚动期间下游静默，停止后刷新。
4. **温区与核心“探针缓存”**：首次只探测一次温区与核数，I/O 削减 90%+。

### 3.2 Release 混淆闪退 (`IllegalAccessError`)
* 根因：AGP 9 实验性 `optimization { packageScope = ... }` 激进重打包 Kotlin 标准库。
* 解决：移除该配置，改用 `isMinifyEnabled = true` + `rules.keep`，保留 `androidx.startup`、生命周期初始化器、ViewModel 反射构造。

### 3.3 RAM / Swap / ZRAM 解析
* 内存用 `ActivityManager.getMemoryInfo()`，扣除 Page Cache，反映真实可用 RAM。
* Swap 为 ZRAM（内存压缩盘），高占用属正常：内核 `vm.swappiness` 主动压缩冷数据换取前台连续 RAM，CPU 微秒级解压。
* ZRAM 明细读 `/sys/block/zram0/`：`mm_stat`（orig/compr）、`comp_algorithm`，计算压缩比与节省内存。
* `MemoryInfo` 已内置 `swapUsedMb` / `swapUsedPercent` / `zramOrigMb` / `zramComprMb` / `zramRatio` / `zramAlgorithm`；`StorageInfo` 提供 `totalGb/freeGb/usedGb`。

### 3.4 CPU 进度条视觉优化
* `/proc/stat` 解析兼容多空格与 SELinux 权限，`/proc/loadavg` 兜底。
* `LinearProgressIndicator` 最小可视宽度 `0.02f`，消除极低负载“空条”假象。

### 3.5 【重要·务必遵守】持久化 su 会话的致命陷阱（曾导致全量指标归零，commit `c43d418`）
1. **token 与无换行内容粘连 → 永久阻塞**
   * `/sys/fs/selinux/enforce`、`/sys/block/zram0/mm_stat`、`thermal_zone*/temp` 等 sysfs 文件**末尾无换行**；
   * `cat f; echo $TOKEN` 会输出 `1__END__...`（同一行），若用 `line == token` 精确比较则永不匹配 → `readLine()` 无限阻塞 → 快照 Flow 卡死 → 所有卡片 0；
   * 修复：命令拼成 `...; echo; echo $token`（先补换行），读取端再用 `endsWith(token)` 兜底剥离。
2. **禁止在持久化 su shell 内嵌套执行 `su`**（如 `su -v`）
   * 子 `su` 会继承父 shell 的 stdin（即 App 写命令的管道），抢走后续命令，token 永不回显；
   * 修复：Root 管理器识别改用 `magisk -v` / `ksud -V` / `apd -V`；所有命令强制 `</dev/null`。
3. **必须有硬超时**：阻塞式 `readLine()` 无法被协程取消 → 独立线程 + `Future.get(timeout)`；超时销毁会话释放 fd 并返回 null 降级（冷启动 6s，热会话 1.5s）。
4. **静态信息不得阻塞数据流**：`SystemInfoMonitor` 后台探测 + 立即返回缓存/基础信息。

> **环境注意**：`pm uninstall` 重装后 UID 变化（如 10186→10187），Magisk 按 UID 记录授权 → 会显示 `NO ROOT`，需在 Magisk 重新授权。

### 3.6 图表增强（commit `e036140`）
* **横轴**：60s / 30s / 0s 标尺 + 对应竖向网格线；采样点右对齐当前时刻。
* **纵轴**：完全动态缩放（含 10% 余量），顶部显示 `低 / 均 / 高`，Canvas 内绘制均值虚线。
* 已移除 `fixedMinY/fixedMaxY` 参数。

### 3.7 系统跟随配色 + 侧滑返回（commit `e036140`）
* `Theme.kt` 使用 `dynamicColorScheme(context)`，不可用时回退 `ColorScheme()`。
* 二级页移除显式返回按钮（圆屏易被裁切），改用 `SwipeToDismissBox` 手势返回 + `BackHandler` 兜底。

### 3.8 系统环境页 & Root 深度探测（commit `53c5a01`）
* 入口：`StatusHeaderCard` 的 `onClick` → `MetricDetailType.SYSTEM_INFO`。
* 内容分 4 卡：设备与固件（型号/代号/Android 版本/固件号/安全补丁/运行时间）、
  **特权与安全环境**（Root 方案+版本、su 路径、SELinux、Bootloader 状态、内核版本）、
  屏幕与显示（分辨率/圆屏/DPI/刷新率）、基带与网络固件。
* **SoC 硬件规格拆到 CPU 详情页**（平台、硬件代号、指令集架构、ABI、物理核心数）。

---

## 4. Git 提交记录一览

```
c43d418 fix(root): resolve total data blackout caused by su session token/newline hang
53c5a01 feat(sysinfo): add title click system technical info and move SoC details to CPU screen
e036140 feat(ui): add 30/60s axes and dynamic scale to charts, adopt system theme, and enable swipe-to-dismiss
536376f feat(mem): split RAM/Swap from Storage, add Swap progress bar, trend chart, and ZRAM details
009defe docs: add comprehensive AGENTHANDOFF.md documenting architecture, optimizations, and roadmap
88b9648 feat(ui): add custom svg and adaptive launcher icons for wearmonitor
4617e9f update readme
3a808f8 Add initial README.md
3fad272 feat: fix cpu progress calculation and add 1-minute trend chart detail screens for cpu, thermal, battery, and memory
26aee4f fix(build): resolve IllegalAccessError crash in release by replacing experimental packageScope with standard R8 keep rules
8c9842a fix(mem): use ActivityManager for accurate RAM metrics and configure debug signing for release build
e0c2524 fix(ui): remove transformedHeight stacking to adopt native flat Wear OS cards and eliminate render jank
367fec8 perf: eliminate scroll jank with persistent root session, probe caching, and scroll-aware polling
6dd7dc3 feat: implement companion Wear OS Tile for system dashboard
df62944 feat: implement Wear Compose Material 3 system dashboard UI
c163d9f feat: implement system hardware monitors and root shell bridge
7371152 build: clean up complication template and optimize gradle configuration
2c4fc4a Initial commit
```

---

## 5. 构建与调试指引

### 5.1 环境要求
* **JDK**：Android Studio 内置 JBR（`/opt/android-studio/jbr`）
* **Gradle**：9.6.0（wrapper 自带）
* **构建系统**：AGP 9.5.1，Kotlin 2.2.10，compileSdk/targetSdk 37，minSdk 30
* 设备：Pixel Watch 2（`aurora`，Android 17 / API 37，Magisk 31.1，已验证）

### 5.2 常用命令
```bash
# 编译
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug assembleRelease

# 安装（Release 用 Debug 证书签名，可直接覆盖安装）
JAVA_HOME=/opt/android-studio/jbr ./gradlew installRelease

# 启动
adb shell am start -n com.github.wdsword07.wearmonitor/.presentation.MainActivity

# 日志（关键 tag）
adb logcat -s WearMonitor_Root:V WearMonitor_SysInfo:V AndroidRuntime:E
```

### 5.3 真机验证清单（回归时逐项检查）
```bash
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell uiautomator dump /sdcard/wd.xml >/dev/null && adb shell cat /sdcard/wd.xml
```
* 主面板：CPU% 非 0、核心在线数、频率、调速器
* 温度：电池/SoC 温度非 0
* 供电：电量、电压 mV
* RAM/Swap：已用/总量、百分比
* 存储：已用/总量 GB
* 点击 Title → 系统环境页：Root 方案、su 路径、SELinux、Bootloader、内核版本
* 日志中**不应出现** `su exec timeout`

### 5.4 Root 授权（易踩坑）
```bash
# 查看当前授权 UID
adb shell "su -c 'magisk --sqlite \"SELECT * FROM policies;\"'"
# 查看应用 UID
adb shell "su -c 'dumpsys package com.github.wdsword07.wearmonitor | grep appId'"
# 重装后如 UID 变更，可临时手工授权（policy=2 表示允许）
adb shell "su -c 'magisk --sqlite \"INSERT OR REPLACE INTO policies (uid,policy,until,logging,notification) VALUES (<UID>,2,0,1,1);\"'"
```

---

## 6. 后续演进建议
1. **磁贴丰富度**：支持用户自定义 Tile 展示项（纯温度 / 纯功率 / 纯存储）。
2. **温度/功耗告警震动**：超过阈值（如芯片 48°C）通过 Haptic Feedback 提示。
3. **系统环境页补充**：GPU 型号/驱动版本、可用传感器列表、eSIM/基带状态细分。
4. **历史窗口可配置**：当前固定 30 点 / 60s，可按指标提供 5min/15min 视图（需持久化环形缓冲）。
5. **性能看护**：为 `execCommand` 增加命中率/超时统计（Debug 面板），防止 su 退化为热点路径。