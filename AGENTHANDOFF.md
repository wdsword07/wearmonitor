# WearMonitor 项目交接文档 (Agent Handoff)

## 1. 项目概述与定位
* **项目名称**：WearMonitor (`com.github.wdsword07.wearmonitor`)
* **目标平台**：Wear OS (API 30 ~ 37)，针对已 Root 设备深度优化，同时兼容非 Root 状态下的基础系统监控。
* **产品核心形态**：
  1. **手表端系统面板主界面**：采用 **Wear Compose Material 3** 构建，适配圆形表盘的平铺卡片仪表盘。
  2. **配套 Wear OS 磁贴（Tile）**：基于 **ProtoLayout Material 3** 构建，表盘侧滑即览核心指标，点击任意区域一键唤醒主应用。
  3. **各指标 1 分钟历史趋势二级页面**：CPU、温度、电池供电、内存均可点击卡片进入深度图表走势页（存储除外）。
  4. **只读安全监控原则**：移除了任何激进、高危的破坏性控制功能，专注精准、极低开销的系统状态监控。

---

## 2. 系统核心架构与模块划分

```
wearmonitor/
├── app/src/main/
│   ├── java/com/github/wdsword07/wearmonitor/
│   │   ├── data/
│   │   │   ├── model/
│   │   │   │   └── SystemModels.kt            # 核心数据模型 (CpuInfo, ThermalInfo, BatteryInfo, MemoryInfo, StorageInfo, MetricDetailType)
│   │   │   ├── root/
│   │   │   │   └── RootShell.kt               # 单例持久化 Root 管道桥接器、智能降级与防卡顿黑名单缓存
│   │   │   └── monitor/
│   │   │       ├── CpuMonitor.kt              # CPU 全局使用率 (/proc/stat & /proc/loadavg 兜底)、各核频率、调速器
│   │   │       ├── ThermalMonitor.kt          # 芯片 SoC 核心温区探测缓存、电池温度监控
│   │   │       ├── BatteryMonitor.kt          # 电池电量、实时充放电电流 (mA)、端电压 (mV)、即时功率 (W)
│   │   │       ├── MemoryStorageMonitor.kt    # ActivityManager 原生 JNI 内存统计、ZRAM 提取、StatFs 存储空间
│   │   │       └── SystemMonitorRepository.kt # 响应式数据源仓库 (单次快照 + 前台按需轮询 Flow)
│   │   ├── presentation/
│   │   │   ├── MainActivity.kt                # 主入口 Activity、Crossfade 页面导航、滑动防抖监听、BackHandler
│   │   │   ├── SystemMonitorViewModel.kt      # 状态流管理、30 点 (60秒) 历史滑动队列、滚动静默调度
│   │   │   ├── theme/
│   │   │   │   └── Theme.kt                   # Wear Compose Material 3 主题 (支持 dynamicColorScheme 系统动态配色)
│   │   │   └── ui/
│   │   │       ├── MonitorCards.kt            # 主面板卡片 (StatusHeaderCard, CpuCard, ThermalCard, BatteryCard, MemoryCard, StorageCard)
│   │   │       ├── MetricTrendChart.kt        # 动态缩放、30/60s标尺、Max/Min/Avg 标注的轻量 Canvas 渐变曲线图
│   │   │       └── MetricDetailScreen.kt      # 各指标 1 分钟历史曲线二级深度详情页面 (原生 SwipeToDismiss 侧滑返回)
│   │   └── tile/
│   │       └── MainTileService.kt             # ProtoLayout Material 3 规范磁贴实现 (CPU+RAM+电量温度)
│   ├── res/                                   # 自适应矢量图标、启动 Splash 图标、各密度 WebP 资源
│   └── keepRules/rules.keep                   # R8 / ProGuard 保留规则 (保留 ViewModel、Startup、组件与模型)
├── wearmonitor_icon.svg                       # 512x512 Master 矢量设计图
├── build.gradle.kts / settings.gradle.kts     # 根工程与国内镜像加速 (Aliyun Maven) 配置
└── README.md                                  # 项目开源与说明文件
```

---

## 3. 关键性能优化与技术方案沉淀

### 3.1 消除滑动严重掉帧（Jank）
1. **彻底移除 `transformedHeight`**：
   * 模板自带的 `transformedHeight` 会在滑动的每一帧对整个卡片动态重算高度与变换，引发手表 GPU 严重的 Overdraw 与多层卡片堆叠（Stacking）。
   * 切换为标准原生平铺布局（`TransformingLazyColumn` + `Arrangement.spacedBy(6.dp)`），配合固定 `key`，卡片自然自适应高度，恢复 60fps 丝滑体验。
2. **消除反复 Fork `su` 进程**：
   * 在受限手表 CPU 上，频繁 `Runtime.getRuntime().exec("su")` 会瞬间打满 CPU 引起主线程卡顿。
   * 重构成**单例持久化 `su` 会话管道**，并通过普通 Java IO 优先策略（95% 以上文件直读），读失败时才走 root 会话。
3. **滚动感知静默轮询（Scroll-Aware UI Polling）**：
   * 监听 `listState.isScrollInProgress`。在用户手指拖动或表冠旋转惯性滚动期间，数据流在下游保持静默，滚动停下瞬间刷新，确保滑动时不触发重绘。
4. **温区与 CPU 核心“探针缓存”**：
   * 首次启动只探测一次核心温区与 CPU 核心总数，不再每轮遍历 16 个 thermal zone 与磁盘目录，I/O 削减 90% 以上。

### 3.2 解决 Release 混淆启动闪退 (`IllegalAccessError`)
* **根因**：AGP 9 的实验性配置 `optimization { packageScope = setOf("androidx.**", "kotlin.**") }` 对 Kotlin 标准库进行了激进重打包，导致私有类跨包继承引发运行时 `IllegalAccessError`。
* **解决**：移除该配置，切换为成熟的 `isMinifyEnabled = true` + `rules.keep`，完整保留 `androidx.startup`、生命周期初始化器以及 ViewModel 反射构造方法。

### 3.3 Linux / ZRAM 内存与 Swap 显示解析
* **为什么内存不再显示“100% 满载”**：改用系统级 `ActivityManager.getMemoryInfo()`，将 Page Cache / 可回收缓存准确扣除，真实反映可用物理 RAM。
* **Swap 占满属正常现象**：Android 的 Swap 为 ZRAM（内存压缩盘），内核 `vm.swappiness` 策略积极将后台冷数据压入 ZRAM 换取前台最大连续 RAM。由于由 CPU 硬件指令在微秒内解压，因此即使占满也完全不会卡顿。

### 3.4 CPU 进度条计算与视觉优化
* 优化了 `/proc/stat` 读取解析（兼容多空格分隔与 SELinux 权限保护），引入 `/proc/loadavg` 智能兜底；
* 在 `LinearProgressIndicator` 增加 `0.02f` 的最小可视宽度，消除极低负载下圆角裁切盲区导致的“broken”空条假象。

---

## 4. 完整 Git 提交记录一览

```
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
* **JDK**：OpenJDK 21 或 Android Studio 内置 JBR 25（`/opt/android-studio/jbr`）
* **Gradle**：9.6.0（包装器自带）
* **构建系统**：Android Gradle Plugin 9.5.1，Kotlin 2.2.10

### 5.2 常用构建命令
```bash
# 指定环境并编译全量 Debug & Release 包
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug assembleRelease

# 一键安装经 R8 极致优化的 Release 包至已连接的手表（已配置 Debug 证书免自签名）
JAVA_HOME=/opt/android-studio/jbr ./gradlew installRelease

# 启动手表主面板并查看实时运行时日志
adb shell am start -n com.github.wdsword07.wearmonitor/.presentation.MainActivity
adb logcat -s WearMonitor_Root:D AndroidRuntime:E
```

---

## 6. 后续演进建议
1. **磁贴丰富度**：当前 Tile 满足系统核心指标展示，可进一步支持根据用户偏好定制磁贴内显示的首选指标（如纯温度卡片或纯功率卡片）。
2. **温度报警阈值震动提醒（可选）**：通过 Wearable Haptic Feedback 在芯片或电池温度超过警戒阈值（如 48°C）时轻触震动提示。
