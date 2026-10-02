# 跨维度耗时诊断与独立对照候选

本目录属于 task23 的独立 Git 分支 `diagnosis/transfer-latency`。只用于父级协调的隔离测试，未加入正式包或 task14 冻结候选。目标是测量并缩短传送耗时；没有实现伤害保护。

当前最强性能线索是正式 Iris / Euphoria 的两段重建：Iris 在切换目标维度时销毁并创建管线；Euphoria 的 `setLevel` 回调随后又安排完整 `Iris.reload()`，重新载入光影包并再次构建管线。Sodium 重置渲染器会等待旧构建线程退出，旧线程退出和新落点区段构建也需要分别计时。

正式 JAR 的静态控制流已确认。原始 Euphoria 回调的低资源回放已完成：8 次回调请求 8 次重载，对照策略移除其中 6 次 Core 三世界之间的额外重载。回放使用依赖计数桩，不运行图形编译；此结果不是“传送已缩短多少秒”的证明。

## 文件

- `build/muxi-transfer-probe-0.1.0-task23-diag.jar`：自有代码的独立诊断模组，未内嵌或改写第三方 JAR。
- `build/build-receipt.json`：构建哈希和纯 Java 测试结果。
- `build/target-signature-receipt.json`：读取真实类字节进行的方法、字段和回调签名检查。
- `build/original-replay-receipt.json`：固定 SHA1 的原始 Euphoria 回调回放结果。
- `analyze_logs.py`：从标准日志抽取计时并分开计算客户端和服务端本地阶段。
- `replay_original.py`：64 MiB 原始回调回放，不启动 Minecraft。

## 测试开关

默认不应用任何探针 mixin，实验改动也默认关闭。父级在协调好的隔离客户端／服务端副本中添加诊断 JAR，运行参数只加以下开关：

```text
基线客户端：-Dmuxi.transferProbe=true
对照客户端：-Dmuxi.transferProbe=true -Dmuxi.transferProbe.skipExtraDimensionReload=true
隔离服务端：-Dmuxi.transferProbe=true
```

对照开关仅阻止 Euphoria 维度回调发起的额外完整重载，限家园 `minecraft:overworld`、生存 `muxi_game_core:overworld`、冒险 `muxi_game_core:adventure` 之间。原生 Iris 管线切换仍执行。登录、注销、同维度操作、其他世界及手动光影重载保持原路径。

Euphoria 在完整重载时会刷新维度相关预处理宏，因此该开关是 **隔离 A/B 候选**。需要确认当前光影没有依赖被省略的维度宏刷新，并检查画面、天空、雾、阴影和 Create/Sable 渲染后，才可形成正式修复。不能把该开关直接推广到所有模组世界。

## 一次真实传送需要采集什么

使用正式 1.4.26 / Core 1.12.2 的隔离副本。不要使用 task14 尚未发布的组合候选来解释正式投诉。131 的内存／发布任务控制由父级安排，本目录没有启动游戏脚本，也没有远端部署动作。

1. 先保持用户光影、视距、帧率设置，记录光影是否开启和具体档位；完成探针启动兼容性检查。
2. 使用已生成、无危险的相同落点做 `家园 → 生存 → 家园`，至少两次往返。第一遍冷启动单独列出，暖重复用于比较。
3. 基线客户端收集 `latest.log`，然后只加对照开关，重复相同路径和设置；收集对照客户端与隔离服务端日志。不要以改变视距、清区块或关闭光影冒充本补丁收益。
4. 如果基线 `iris_reload` 占主要耗时，而对照里对应请求被跳过并且 `total_client_transfer_ms` 明显下降，就有直接因果证据。计算每次、暖运行中位数与范围；保留原始事件。
5. 若 `iris_reload` 不发生或很短，按日志转查 `sodium_shutdown`、区块发送队列、区块到达后到区段就绪的阶段。若用户本来关闭光影，也不要把光影重载预设为主因。

日志解析仅在本地运行：

```powershell
python analyze_logs.py baseline-client-latest.log baseline-server-latest.log
python analyze_logs.py candidate-client-latest.log candidate-server-latest.log
```

## 计时含义与边界

标准日志中每条记录以 `MUXI_TRANSFER_TRACE` 开头。每个进程有独立 session 和单调时钟，transfer 序号属于本进程。通过目标维度、顺序和 `teleport_id` 关联两端，**不直接相减两端时钟**。

- 服务端 `request` 指引擎 `ServerPlayer.changeDimension` 入口，不能包含此前 Core 传送门搜索的全部耗时。
- `position_send`、`landing_chunk_send` 是包交给服务端连接的时点，不能称为数据已经到达网卡或客户端。Bundle 内区块包也会识别。
- 每秒采样 pending、客户端需求速率、配额、未确认批次数和落点是否仍在队列。
- 客户端记录 respawn 处理、世界／引擎切换、位置处理、落点区块处理返回、区段就绪、加载界面退出。
- `iris_pipeline` 和 `iris_reload` 单独记录；后者包含前者的嵌套耗时，不应把两者相加。
- `sodium_reset` 可能包含 `sodium_shutdown`，同样不相加。
- 区块处理返回不保证光照更新和网格构建已结束；该差值由 `chunk_to_section_ms` 捕获。
- `loading_screen_lifecycle_ms` 从设置 screen 算起；`rendered_screen_phase_ms` 从第一次进入 screen 的 render 算起。两者都不是 GPU 实际呈现帧的精确计时。
- 原版 30 秒兜底仍保留，不用缩短超时或绕过真实 section 就绪来掩盖延迟。

探针限定两分钟窗口、采样日志有数量预算，并给关键一次性阶段保留终点记录。不记录凭据、聊天或精确落点坐标。没有新的网络 payload、区块生成命令、配置自动写入或保护状态。

## 已验证与仍待验证

已完成离线 Java 编译、纯 Java 边界检查、4 项日志分析测试、固定原始回调回放、真实目标注入和字段签名校验。目标签名校验不等价于运行时 Mixin 应用成功。

还需父级安排一次真实客户端／隔离服务端运行：验证 Mixin 启动、抓到基线时间线、跑对照时间线并检查画面。没有这一步，不能声称 10～20 秒的根因已完全闭环，或宣称已获得具体秒数的提升。
