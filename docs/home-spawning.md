# 家园环境生成限制

仅匹配真实维度键 `minecraft:overworld`（家园）。`muxi_game_core:overworld` 是生存，`muxi_game_core:adventure` 是冒险；下界、末地、挑战及其他模组维度不受此规则影响。

## 当前运行基线与根因

源码基线 `d265d829bc5ea263cbed8e40137fd7ed4a1ed9ff`，正式目录实际安装的是 Core `1.12.0`，README 中较早的版本描述不作为运行证据。运行目录 KubeJS 没有家园生成拦截；只读存档快照中的 `doMobSpawning`、`doInsomnia`、`doPatrolSpawning`、`doTraderSpawning` 均为 `true`。存档快照不是实时 gamerule 查询。

`SpawnCategoryFilter` 是类别候选为空时的性能优化，不是禁自然生成策略，且 MONSTER 类别始终保留。幻翼从 `PhantomSpawner.tick` 调用独立生成循环，直接完成初始化并加入实体，不经过 `NaturalSpawner.spawnForChunk` 的类别过滤，也不走 PositionCheck。故仅改普通刷怪类别或位置事件不能解决幻翼。

NeoForge 21.1.250 的 coremod 会将目标类中的 `finalizeSpawn` 调用重定向到生成事件。仅 `FinalizeSpawnEvent.setCanceled(true)` 会跳过初始化，却不禁止实体加入；需要 `setSpawnCancelled(true)`。本修复先在幻翼专用 `PlayerSpawnPhantomsEvent` 拒绝家园环境幻翼，再用 placement、position、finalize 事件覆盖其他生成路径。普通区块循环提前跳过家园只是优化，事件限制是安全兜底。

## 原因边界

| 生成路径 | 家园行为 | 核查依据 |
| --- | --- | --- |
| 普通自然生成、区块生成生物 | 拒绝 `NATURAL`、`CHUNK_GENERATION` | placement/position/finalize 事件；区块循环提前跳过 |
| 失眠幻翼 | 拒绝 | 真实 PhantomSpawner 的玩家专用事件；NATURAL finalize 兜底 |
| 村庄、女巫小屋的环境猫 | 拒绝 NATURAL | 原生 CatSpawner 直接 finalize 后插入，同样需要 finalize 兜底 |
| 掠夺者巡逻 | 拒绝 `PATROL` | 原生巡逻 helper 完成初始化后插入实体，不依赖 PositionCheck |
| 僵尸增援 | 拒绝 `REINFORCEMENT` | Zombie.hurt 的 SpawnPlacements 条件与后续 finalize；不能仅依赖 PositionCheck |
| 游商与商人羊驼 | 拒绝这两种实体的 `EVENT` | WanderingTraderSpawner 使用 EntityType.spawn(EVENT) |
| 地精游商 | 拒绝 goblin/vein_goblin 的 `EVENT` | 原生 GoblinTraderSpawner 使用 EntityType.spawn(EVENT)；原有生存/冒险独立计时器与分发不改 |
| 村庄夜间僵尸围攻 | 拒绝 zombie 的 `EVENT` | 独立 VillageSiege 路径使用 EVENT |
| 其他 `EVENT` 生物 | 保留 | 模组也以 EVENT 表示主动召唤，不能一刀切 |
| 指令、刷怪蛋、发射器、水桶、繁殖、召唤、转换、骑乘、结构、触发生物、刷怪笼、试炼刷怪笼 | 保留 | 不属于本次环境自然生成原因集合；不改变现有可用路径 |
| 已存在、存档加载、跨维度迁入的实体与宠物 | 保留 | 不注册清实体或 EntityJoinLevel 拦截，不删除世界实体 |

不修改 gamerule；尤其没有全局关闭 `doInsomnia`。不改生产配置、KubeJS、运行 JAR、矿物或战利品，不部署、重启或热重载。

## 隔离验证

```powershell
python build.py --java-home ../perf-lab/java21/jdk-21.0.2 --test
python -m unittest discover -s tests -v
python tests/run_home_spawning_smoke.py --java-home ../perf-lab/java21/jdk-21.0.2
python tests/run_home_spawning_smoke.py --full-pack --java-home ../perf-lab/java21/jdk-21.0.2
```

新测试独立于原有 `tests/run_tasks_smoke.py`，保留其本机 Waystones/Balm 修改。使用新存档、新配置、测试专用 JAR 和禁监听 mixin，不读取玩家存档或账户配置。生成检查覆盖原生失眠/夜空条件的 PhantomSpawner、竞争 ALLOW、原生巡逻 helper、实际 EntityType.spawn(EVENT/COMMAND)、所有 MobSpawnType 的事件与插入矩阵、宠物与召唤、地精探索计时器以及 gamerule 不变。

本执行环境中的 JDK HTTP selector 在 Windows 本地 AF_UNIX 管道初始化时失败。仅最小隔离测试使用测试专用 mixin 跳过无关的 `DailyTasksFeature.onStarted`（账户积分 HTTP 初始化）；生产 Core 无此修改，此测试不验证账户积分生命周期。完整包测试不再跳过账户积分或 Collective HTTP 初始化，先做真实 HttpClient 构造预检，失败即记录 `success=false` 并退出。完整模组测试额外复制运行目录的公开 KubeJS 脚本，但使用新默认配置并在测试目录排除 C2ME；不能代表生产服全部配置的长期夜间行为。

首次完整模组尝试在 Collective/Villager Names 的 `RegisterMod` 静态 HTTP 初始化中遇到同一 AF_UNIX 管道错误，未进入生成测试。该失败报告原样保留。复核本地字节码后确认 `enableUpdateChecker=false` 只跳过后续检查，不跳过静态 HTTP 创建。曾用测试专用 `DisableCollectiveUpdateCheckMixin` 隔离更新检查后，net_music_list 静态 HTTP 构造仍以同一错误失败；这不是运行环境修复，也不算完整包验收通过。该隔离现在仅能以显式诊断参数 `--isolate-collective-update-checker` 选择，默认完整包验收不启用；不进入 Core 产物或生产目录。

最小隔离测试使用真实 NeoForge、Core、Waystones/Balm、Goblin Traders/Framework，已进入并执行实际生成路径。

测试日志与 JSON 保留在 `build/home-spawning-smoke-*`。这些证据只证明实际经过已审计事件链的生成路径；未通过 NeoForge 生成事件而直接加入实体的模组自定义路径不保证被覆盖，不能将结果描述成全整合包所有动态生物生成均已穷尽验收。

## 已复核结果

- 离线构建、10,025 项 Java 自检与 8 项 Python 安装回归通过；构建 SHA-256 `4feb1d4b741cdd6431b80e085b63839eeb44992b4cf83b4fbc3f8fdb22551500`。
- 原生隔离生成基线 391 项通过：`build/home-spawning-smoke-20260930-230945-260925/home-spawning-result.json`。
- 最后补充测试实际 413 项通过、退出码 0：`build/home-spawning-smoke-20260930-231341-964700/home-spawning-result.json`。真实失眠/夜空条件下家园尝试 60 次、环境幻翼加入 0；生存和冒险各首次尝试加入 3。竞争 ALLOW 仍被家园拒绝，随后家园显式 COMMAND 幻翼召唤成功。
- 调整完整包预检后，最小隔离测试再次通过 413 项、退出码 0：`build/home-spawning-smoke-20260930-234519-544422/home-spawning-result.json`。没有启用 Collective 更新检查隔离；Core 构建校验值不变。
- 同次测试覆盖所有 17 种原因在家园、生存、冒险、下界、末地的边界，原生巡逻 helper、两类地精/游商/羊驼/围攻僵尸 EVENT、原生增援 placement、明确召唤、宠物、地精探索计时器，以及 gamerule 不变。没有实际触发 Zombie.hurt 的整段随机增援选址，也没有等待完整村庄围攻/游商计时周期。
- 首次完整包失败证据：`build/home-spawning-smoke-20260930-231040-987574/boot.log`；该次 `success=false`，不能因服务器退出码为 0 判为通过。
- 恢复连接后的完整包补跑：`build/home-spawning-smoke-20260930-232808-193764/boot.log`。测试专用更新检查隔离已越过 Collective/Villager Names，随后 `net_music_list` 的 `com.gly091020.netMusicListNeoforge.util.NetMusicListUtil` 静态 HTTP 初始化又遇到 AF_UNIX `Invalid argument: connect`，仍未进入生成阶段；该次 `success=false`。后续转为独立 JDK/socket 最小复现，没有逐个移除模组。
- 只读运行维度、存档 gamerule 快照和生产 JAR/公开 KubeJS 校验值：`build/home-spawning-runtime-audit.json`。复核生产 Core JAR SHA-256 仍为 `38d817a8d0469365e8c33cc799da9f354f7d728e24847ee648ecb9a1e9a6837c`，未替换。
- 恢复后的报告校验汇总：`build/home-spawning-verification.json`。包含成功与失败报告的 SHA-256，以及生产 JAR/KubeJS 未变、测试专用隔离 mixin 未进入 Core JAR 的检查结果。尚未验证生产全配置和长期计时周期，现有报告不等于整合包所有动态生成均已覆盖。

## HTTP 初始化报错诊断

独立探针 `tests/socket-runtime-probe/SocketRuntimeProbe.java` 不加载任何模组，不发起 HTTP 请求，只构造 HttpClient 或进行进程本地 socket 自连接。JDK 21.0.2 与生产使用的 OpenJDK 23.0.2 在当前工具执行环境均表现为：HttpClient 创建失败、AF_UNIX 自连接失败、127.0.0.1 TCP 自连接成功。AF_UNIX 绑定成功，绑定地址与连接地址相同，路径为 58 字节；故远端音乐接口、HTTP 请求代理设置和路径过长不是这些最小复现的直接原因。证据在 `build/socket-runtime-probe/result.json` 及其引用日志。

生产日志记录相同 OpenJDK 23.0.2 正常启动完成，没有此 AF_UNIX 致命错误；不能据此将测试环境问题当作生产模组 bug。仍未唯一定位到 Windows 主机、执行沙箱或其本地 socket 代理中的哪一层。无权限拒绝结果可证明具体层，不能把 `Invalid argument: connect` 表述成已经确认的安全策略拒绝。

核查本地 JDK 源码：Windows PipeImpl 优先 AF_UNIX；当前连接失败位置不触发其 bind 失败时的 TCP 回退。尝试 IPv4 偏好、WindowsSelectorProvider、生产同版 JDK，以及官方支持的 `jdk.net.unixdomain.tmpdir` 指向规范化 TEMP 均未解决。该路径属性见 [Oracle Java 21 Networking Properties](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/doc-files/net-properties.html)。没有替换 JDK 内部实现、改 socket 钩子、安全设置或代理，也没有逐个移除模组来宣布修复。

最新完整包尝试 `build/home-spawning-smoke-20260930-234212-525554/home-spawning-result.json` 在真实 HTTP 预检阶段报告失败，`serverStarted=false`，没有启用 Collective/账户积分绕过。预检 Java 捕获异常后返回 0，脚本仍检查成功标记并以失败退出，避免把进程退出码误判为验收通过。底层 AF_UNIX 问题尚未修复；完整包生成验收需要可正常创建本地 AF_UNIX 通道的执行环境后重跑。

再次使用生产同版 JDK 和官方临时目录属性的失败证据：`build/socket-runtime-probe/openjdk-23.0.2-supported-tmpdir-http.log`。目前未确认一个适用于本故障且不改变安全策略的受支持修复方案，不能把临时目录属性或更换 Java 版本描述成已有效。

所需最小用户操作：在用户自己的普通 PowerShell 中运行下面的无模组、无 HTTP 请求探针，将输出保留给平台/主机兼容性排查；无需管理员权限或生产操作。若 `success=true`，说明普通宿主进程可用而当前工具执行环境不可用，应由平台排查本地 AF_UNIX 兼容性；若同样失败，还需继续区分宿主系统/JDK 环境。此对照只做诊断，不用于绕过工具执行限制运行完整包。

```powershell
& 'C:\Users\Administrator\.jdks\openjdk-23.0.2\bin\java.exe' 'C:\Users\Administrator\WorkSpace\muxigame\muxi-game-core\tests\socket-runtime-probe\SocketRuntimeProbe.java' http $env:TEMP
```
