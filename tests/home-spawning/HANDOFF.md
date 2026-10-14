# 家园被动刷怪修复：当前验收交接

状态：真实源代码已实现；离线测试和真实专用服务器功能回归通过。最终原生 267 项检查全部通过，全部本轮实例正常退出。尚未部署；最终生产整包组装由发布负责人处理。

## 实际目录与生产映射

- 源码：`C:\Users\ranzh\workspace\dev\muxigame\muxi-game-core`，dev HEAD `c07917ef819ed2321d535339d8b94baebee98fb6`。
- 家园：`minecraft:overworld`；生存：`muxi_game_core:overworld`；冒险：`muxi_game_core:adventure`。
- 三个 ID 来自生产 level.dat、Core 类、现有传送端点及只读 RCON 查询交叉确认，详见 `build/home-spawning-20261013/production-audit.json`。没有迁移世界。
- 本地与生产 NeoForge server 库 SHA 一致：`1808fab692dc44b2d474295d1cdd9f1fe8a7dceab4f594210873646fafdf1359`，见 `production-neoforge-runtime-proof.json`。

## 已落地

- 修改 `feature/spawning/HomeSpawningFeature.java`，新增临时生成来源、策略、玩家孵化来源接口及 `mixin/home` 下 24 个守卫类。
- fresh 来源不从持久化 `neoforge:spawn_type` 推断；阻止 NATURAL、世界生成直接插入、无可信来源的 EVENT/TRIGGERED/JOCKEY/MOB_SUMMONED/假 SPAWNER，以及 CustomNPC 周期生成的异步提交。
- 独立包裹真实刷怪笼、试炼刷怪笼、玩家使用/交互、命令原有权限入口、NPC 原有工具入口、繁殖/孵化与目标世界搬运插入路径。
- 保留从磁盘加载实体；不删除现存动物或 NPC。EasyNPC 自动替换在丢弃旧 NPC 之前拦截；CustomNPC 仅拦截 tickDeath 的自动 reset 调用，保留直接手动 reset。
- 阻止家园雷击的骷髅马工厂及陷阱增殖。实际固定版本使用 `tick` 和 `lambda$tick$0`，已纠正早先错误的 `start` 目标。已有陷阱马安全解除 trap 标记并保留实体，实际雷电工厂与其他维度逻辑保留。
- `muxi_game_core.mixins.json` 只增补 home.*。未改并行 YSM、登录桥、传送网络文件；没有提交、推送或生产部署。

## 当前候选

`build/home-spawning-20261013/muxi-game-core-1.12.3-homeguard.1.jar`

SHA256：`7637311f7ea3705d3776609f2ce83f38c3bbb58b90010f1dd371b82a04ff0249`，504759 字节。

构造方式公开记录在 `guard-candidate.json`：只将本轮真实源文件针对已验证的当前生产 ABI 重新编译；所有非本轮条目保留当前生产字节码。不是完整最新工作树构建，避免混入尚在开发的并行登录/YSM 桥。当前生产基线 SHA `46acc0ad4bd4b3cd0ff3ed812f649cfbbe6fc825820b3c9bca9267b8e0547d59`，无缺失条目，未改动的生产条目逐字节相同。

## 已验证与待验证

- 最新离线策略/上下文测试：61 项通过，`policy-test-result.txt`。
- 最新固定字节码注入目标及调用点审计：35 项通过，`target-audit-result.txt`。此审计不等价于运行时 Mixin 转换成功。
- 最新独立原生验收 267 项通过：`native-acceptance.json`；逐实例退出、资源边界与失败修复：`native-final-summary.json`；测试输入 SHA：`native-preparation.json`。
- 测试覆盖设计包括三维度真实实体入场、真实刷怪笼、繁殖、命令、NPC 工具、NPC 异步伪 COMMAND 生成、两类 NPC 自动复活、旧实体加载与搬运目标路径、雷击分支及资源维度骑士对照。
- 补充调用实际 Ice `SeaSerpentSpawnFeature.place` 和 Alex `BeachedCachalotWhaleSpawner.attemptSpawnWhale`：控制夹具环境/概率条件后，两维度均真实到达插入，家园 0 新实体、生存 +1。Ice 使用代理水体/高度条件，不声称完整地图生成；WorldGenRegion 的实际守卫入口另测。雷击测试实际转换方法分支，不声称随机雷暴长测；搬运验证目标插入入口，不声称完整门户行程。孵化和试炼完整遭遇、其他可选模组和客户端整包不在本次原生范围。

## 原生逐项结果

| 场景 | 原生结果 |
| --- | --- |
| 三维度、全部生成原因 | 家园按来源拒绝；生存/冒险全部原因正向对照通过 |
| 雷击陷阱马首次工厂 | 家园 null，不调用马工厂；实际雷电工厂仍执行；生存马工厂保留 |
| 已有陷阱马与骑手触发 | 家园旧马保留并解除 trap，无新增马/骑手；生存新增 3 匹马与 4 名骑手 |
| 冰火海蛇真实 feature | 同一实际生成函数到达插入：家园拒绝，生存 +1；独立 WorldGenRegion 插入守卫通过 |
| Alex 搁浅鲸真实 routine | 模组使用 SPAWNER 的周期生成真实到达插入：家园拒绝，生存 +1 |
| CustomNPC 特殊生成 | 实际异步任务携旧 COMMAND NBT 仍拒绝；线程上下文清理，后续玩家生成不受污染 |
| NPC 自动复活 | CustomNPC 家园自动 reset 拒绝、资源自动 reset 与直接手动 reset 保留；EasyNPC 在 discard 旧实体前拒绝自动替换 |
| 主动生成正向对照 | 实际原版刷怪笼、动物繁殖、原权限命令、CustomNPC 玩家工具全部创建成功 |
| 已有实体和搬运 | 实际磁盘加载入口保留；实际搬运目标插入入口保持 UUID 与血量；旧 NBT 不当作新来源 |

## 失败修复、资源与退出

- 前期静态审计发现实际骷髅陷阱入口为 tick/异步 lambda，已修正产品源码并通过原生验证。
- 首轮 251 项通过。补充 Alex 真实 routine 时，夹具将两个维度不同生物群系放入同一个 AND 组，先返回 false，不能算阻止成功；诊断后改为两个 OR 备选组，最终 267 项通过。此修正只改测试，产品候选 SHA 未变。中间失败证据仍保留在各独立 lab。
- 发布负责人 19:03 UTC 的资源核查与父线程明确授权允许 4 GiB / 2 CPU 并行；全程零客户端、无窗口、无键鼠、独立 loopback 端口与新世界，非性能基准。
- 本轮四个自有实例 PID：34644、59316、59756、46940。均正常停止、Java 退出码 0，实际 PID 不存在；详见 `native-final-summary.json`。没有结束其他 Java/MC 进程。
- 最终 lab：`better-mc-remake/build/local-mc-debug/home-spawn-native-20261014-031431`。实际夹具 `server/home-spawn-native-result.json` 是玩法判定；协调器 run-result 只作为连接及正常退出证据，不能替代夹具结果。
- 完整日志无 Mixin 应用/注入错误。隔离精简环境有未安装农夫乐事的兼容配方解析日志、初次创建配置日志及批量功能操作的延迟提示；服务器正常启动并完成全部断言，这些日志不作为整包或性能结论。

`tests/home-spawning/run_native_qa.py` 保持默认离线准备模式；已有授权启动通过独立协调器进行。脚本不修改共享 helper，只在自身进程中限制 2 CPU；结果落盘后正常停止自身实例。

当前 `nativeAcceptancePassed=true`、267 项通过。`readyForRelease=false` 表示尚未由发布负责人组装最终生产整包、未部署，不表示上述功能验收失败。候选不包含本轮 QA 模组，也未混入并行登录/YSM 桥。生产不重启、不 reload、不迁移、不删除实体。
