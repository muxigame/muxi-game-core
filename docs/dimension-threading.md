# 原生维度 tick 与区块加载线程（开发版）

`1.10.0-dev` 已接入 Minecraft 1.21.1 / NeoForge 21.1.250 原生管线，不再只是独立调度模型。默认关闭，仅在一次性本地测试环境启用；尚未安装到正式服务器或分发包。

## 执行模型

每个世界有固定 tick 执行器与固定加载执行器。主服务器线程先处理全局阶段，再同时提交所有世界 tick，等待本轮全部结束，随后处理跨维度传送和其他全局工作。世界及 ServerChunkCache 的线程归属在阶段切换时移交，结束后归还协调线程。没有把 `MinecraftServer.isSameThread()` 对所有工作线程放宽。

区块加载执行器接入 `ServerLevel -> ServerChunkCache -> ChunkMap`，保留原生 ChunkStatus 依赖、票据、主线程激活队列与存档格式。原生群系/噪声的异步计算续接到同一维度加载执行器。磁盘检查、磁盘读写、网络等继续使用辅助线程；尤其不能把 `IOWorker.isOldChunkAround` 同步等待的后台任务改派到正在等待的同一个加载线程。

区块 FULL 激活及网络/玩家全局阶段仍有串行工作。理论上世界 tick 部分由总和变为近似最大值，但服务器总耗时还包括协调、激活、发包、共享状态和线程争用。加载独立能降低跑图对 tick 的阻塞，不能承诺跑图对 TPS 毫无影响。

## 并发兼容与防护

- 原生传送门计算和跨维度实体转移移到本轮世界 tick 的同步点之后，避免同时写入源/目标实体列表。
- NeoForge `Block.capturedDrops` 静态掉落物捕获改为线程局部的嵌套作用域，修复真实客户端测试出现的掉落物列表空指针。
- 红石导线计算中的共享 `shouldSignal` 临时状态按线程隔离。
- Serene Seasons 的三份全局 `HashMap` 在访问时分别加锁，修复整包双维度更新中实际触发的 `ConcurrentModificationException`；锁只覆盖映射操作，不串行化整次世界 tick。
- 世界方块修改及维度 tick 中的实体添加检查实际线程归属；维度线程同步获取其他世界区块会报错，不伪装为允许访问。
- tick 线程名含 `server`，适配 Cupboard 用线程名识别服务器线程的队列规则；加载线程不使用此名称，仍受原生加载规则约束。
- 工作线程使用独立的空栈 profiler，真实耗时另外记录；NeoForge 的 perWorldTickTimes 在同步点补回。不能把原主线程 profiler 当成全部 worker 的性能数据。
- 执行器队列有界，拒绝时抛出错误，不在提交者线程执行昂贵任务；这不是对原生所有内部票据队列的统一限额。
- 所有世界 tick 全部结束后才允许进入全局/保存阶段；异常不吞掉。死锁由原生 watchdog 与本地测试进程超时发现，测试保存线程转储后修复根因。

## 开关与复现

仅服务端 JVM 增加 `-Dmuxi.dimensionThreads=true`。启用时不允许同时加载 C2ME；移除操作只在测试目录执行。客户端无需该 JVM 开关，两端使用匹配的核心 JAR。

在核心仓库目录运行：

```powershell
python build.py --java-home ../perf-lab/java21/jdk-21.0.2 --test
python tests/run_tasks_smoke.py --dimensions --threaded --java-home ../perf-lab/java21/jdk-21.0.2
python tests/run_tasks_smoke.py --worldgen-audit --threaded --without-c2me --seed 12345 --java-home ../perf-lab/java21/jdk-21.0.2
python tests/run_dimensions_e2e.py --threaded --java-home ../perf-lab/java21/jdk-21.0.2
python tests/run_thread_load_e2e.py --threaded --full-pack --profile --java-home ../perf-lab/java21/jdk-21.0.2
python tests/run_thread_load_e2e.py --full-pack --profile --java-home ../perf-lab/java21/jdk-21.0.2
```

最后两条是并行/串行对照，必须顺序执行，避免彼此争用 CPU。两者均移除实验目录中的 C2ME；不是“自研版 vs C2ME”的性能排名。

## 真实负载夹具

两个隐藏窗口的原生 Minecraft 客户端，使用测试账号和普通移动网络包，仅连接随机的 `127.0.0.1` 端口。每个世界运行 64 个熔炉、64 个漏斗及其箱子、8 个 Create 创造马达和 128 根轴；检查真实铁锭产出、物品转移和非零转速。

两端确认进入游戏后预热 600 tick，再补充机器物料，记录 400 tick 机器阶段和 600 tick 探索阶段；每个漏斗装满五组物料，避免客户端加载期间物料耗尽。客户端沿固定 384 格路线飞行。之后跨维度、断线重连，核对维度、23 颗钻石与 7 级经验，保存截图。最后停止服务器并重新启动，逐一检查两世界全部 128 台熔炉和 128 个箱子的产物已保存恢复。

报告同时包含 ServerTickEvent 内耗时与相邻 tick 开始的实际间隔，后者包含两次 tick 之间工作的影响。机器位置被强加载，玩家离开后继续运行。全包客户端复用本机公开 FML 兼容配置，不复制账户数据；仅排除 C2ME、旧核心/身份包以及不参与玩法的 CrashAssistant 弹窗诊断器。本机旧测试客户端缺少的 Custom NPCs、Crawl on Demand、冠军随从兼容包从配对测试服务端补齐；没有修改原客户端。服务端使用脚本中的公开配置与数据包白名单，不加载生产身份和网络接入配置。

## 2026-09-28 本机整包验收

产物：`build/libs/muxi-game-core-1.10.0-dev.jar`，SHA-256 `be21c245c22b0f35a04453f0e9a5def458e6f905782dbdd7daa5e9745fc12d3c`。

机器为 Ryzen 7 9800X3D，系统报告 8 个逻辑处理器。测试服务端设置 8 GiB 堆、`ActiveProcessorCount=4`，每个原生客户端设置 8 GiB 堆、2 个处理器，视距/模拟距离 3。服务端 338 个 JAR，客户端各 416 个 JAR（含测试夹具和嵌入库外层包）；两组均没有 C2ME。正式服务器现有堆设置为 8–20 GiB，本次受控 8 GiB 对照不能直接代替正式配置的容量评估。

同一 JAR、种子 12345、固定 384 格路线，串行与并行**顺序**运行，均开启 JFR。两端就绪后预热 600 tick，机器测量 400 tick，探索测量 600 tick。以下是一次配对实测，不是跨机器或重载场景的普遍加速比例。

| 指标 | 串行 | 维度并行 |
| --- | ---: | ---: |
| 机器阶段平均 tick 耗时 | 3.441 ms | 2.188 ms |
| 机器阶段 P95 / P99 | 4.192 / 8.369 ms | 2.807 / 6.442 ms |
| 探索阶段平均 tick 耗时 | 9.450 ms | 7.724 ms |
| 探索阶段 P95 / P99 | 25.086 / 51.248 ms | 29.096 / 56.523 ms |
| 探索实际 tick 间隔平均 | 49.999 ms | 50.000 ms |
| 探索实际 tick 间隔 P99 | 90.142 ms | 95.911 ms |

这次平均 tick 耗时分别减少约 36.4% 和 18.3%，两组平均约 20 TPS；探索尾部延迟没有改善。并行版探索时记录到 12 次 GC 暂停，最长 125.354 ms。JFR 执行采样及 ChunkStatus 追踪都确认家园/生存的生成步骤分别在 `muxi-load-minecraft:overworld` / `muxi-load-muxi_game_core:overworld` 执行，但同一 JVM 的 GC 暂停仍能阻塞全部维度。不能把这次结果描述为“跑图不影响 TPS”。

两组均通过：双玩家到达 x=384.5、两世界共 128 台熔炉产铁、128 个漏斗向箱子搬运、256 根 Create 轴保持非零转速、跨维度物品/等级/维度同步、断线重连、正常停服及重启后全部熔炉与箱子产物恢复。自然刷怪在该机器负载夹具中关闭；本结果不是实体战斗压力测试。

- 串行证据：`build/thread-load-20260928-204142-326562/load-result.json`、`load-save-result.json`、`native-load.jfr`。
- 并行证据：`build/thread-load-20260928-204934-797097/load-result.json`、`load-save-result.json`、`native-load.jfr`、`profile-summary.json`。
- 最终构建的地形 A/B：种子 12345 为 `build/tasks-smoke-20260928-205759-300133/tasks-smoke-result.json`，种子 67890 为 `build/tasks-smoke-20260928-205759-492772/tasks-smoke-result.json`。每种子 12,675 个群系采样、12 个 FULL 区块（1,179,648 个方块位置）和 96 个结构候选区块均零差异；C2ME 模块列表为空，两次退出码均为 0。有限采样不代表已经穷尽所有坐标或全部模组实体行为。
- 最终构建的原生实体门回归：`build/dimensions-e2e-20260928-205759/e2e-result.json`；客户端 `build/tasks-client-smoke-20260928-205825-160604-529a6f13/client-smoke-result.json`，29 项建门、点火、双向传送及重连断言通过，客户端和服务端均正常退出。
- 构建自测 6,910 项通过，安装脚本测试 8 项通过；这些既有自测不应计作 6,910 项新的并发兼容验证。`git diff --check` 通过。完成后没有残留的测试 Java 进程。
- 较早的短预热整包功能回归：`build/thread-load-20260928-203227-419815` 通过；入服初始化影响其耗时，未混入上表。
- 最小 Create 集成场景：`build/thread-load-20260928-200524-431776`；早期实体门客户端回归 29 项：`build/dimensions-e2e-20260928-200054`。

## 范围

2026-09-29 后续实验增加前方区块票据、玩家/载具移动包准入、全局生成并发预算和限速预生成，详见 [区块移动加载](chunk-travel.md)。这些改动不消除所有 FULL 激活、模组读取或全局屏障等待；新旧性能结果必须按构建哈希和实际路线完成时间区分。

2026-09-29 扩展四玩家验证中，Sable 2.0.5 的两个全局方块碰撞判定缓存暴露并发扩容越界。新增 `SableVoxelCacheThreadMixin`，仅在实验线程开关开启时将两份引用键布尔缓存包装为同步映射，保持原有键/值语义，形状计算留在锁外。真实目标类的定向回归覆盖两种判定、468 种方块状态、4 个线程重复并发访问，并验证补丁实际生效。新的构建及四玩家/GC 对照记录见 [多人 GC 基准](multiplayer-gc-benchmark.md)；上节 9 月 28 日的性能、地形与传送门记录仍对应其标明的历史 SHA，不混为新构建结果。

本次验证覆盖指定机器、自动控制的真实客户端、跑图及传送保存流程，不代表所有模组 Boss、跨维度机器网络或长时间多人服行为均已穷尽。原生区块依赖系统仍承担邻区块访问管理；线程归属检查也不是对所有第三方内存写入的通用检测器。更新模组或修改区块管线后需重跑整包验收，实验开关保持默认关闭。

模组直接从世界 tick 调用 `changeDimension` 时会延后执行并立即返回 `null`。原生传送门的整个处理过程已一起延后；依赖同步返回新实体的其他模组调用方仍需单独适配，不能据此宣称任意跨维度模组都兼容。日志仍包含串行基线也有的整包诊断，例如 LootJS 的 NumberProvider 配方错误与 AdvancedLootInfo 的村民交易解析警告；功能测试通过不代表整包已有的全部告警都已修复。
