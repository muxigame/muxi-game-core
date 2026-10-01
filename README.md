> 小游戏拆分迁移：僵尸枪战已迁出本模组，由独立 `muxi_zombie_challenge` 注册；Core 仅提供既有可信账号证明和通用参与状态查询。旧挑战章节属于历史资料，现行安装与回归说明见 `../muxi-minigames/docs/migration.md`。运行必须配套新版 `muxi_minigames`；禁止与旧 Core 混用。

# muxi Game Core

muxigame 整合包的**功能集成模组**：服务端功能（登录核验、昵称同步、玩法规则），以及客户端的显示兼容（到处显示昵称而不是 UID）。

- 模组 ID：`muxi_game_core`
- 1.11.2 发布候选：以已验证的 `1.11.1` 多维度版本为基线，新增长期保留的冒险世界；当前正式服仍为 `1.11.0`，需在无人维护窗口与客户端同步切换
- 当前目标：Minecraft `1.21.1` / NeoForge `21.1.250` / Java `21`
- 服务端和客户端装同一个 jar：服务端入口 `MuxiGameCore`（`dist = DEDICATED_SERVER`），客户端入口 `client/MuxiGameCoreClient`。
  每日任务使用可选的 `daily-tasks-2` 自定义网络通道；两端建议同时更新至 1.7.x。
  老客户端不接收任务包，仍可使用文本任务指令。昵称数据继续来自 Simple Nicknames（两边都装）。
- 本地构建、手动安装；没有 CI、GitHub Actions、云端构建或自动发布配置。

## 当前实现

`dimensions`：原主世界显示为家园并保留存档 ID；新增可重置的生存世界和长期保留的冒险世界。家园↔生存仍使用现有实体门，冒险世界当前只开放管理员 `/muxiworld adventure` 入口并可建返回家园的门；暮色、下界、末地入口限定在家园。详见 [维度细分说明](docs/dimensions.md)。

新玩家不再先进入家园后再传送：首次登录在 `PlayerList.placeNewPlayer` 构造登录包之前直接选择生存世界，并在距世界原点 10000 格半径内寻找随机安全陆地点。海洋、流体、树干顶、危险方块、墙体和不足两格净空的位置全部拒绝；成功落点同时作为初始重生点，之后睡床可按原版规则覆盖。已被旧首次手册流程处理的老玩家不会迁移。

远端维度更新补齐两个主世界型探索维度的 Blueprint 群系、地表与噪声盐值，Mowzie 自然生成维度条件、季节白名单、独立 Goblin Traders 计时器和 Paster Dream 世界生成存储。家园、生存、冒险均保留真实独立维度 ID；同种子 A/B 的测试范围及单线程配置见 [生成兼容审计](docs/dimensions-worldgen-audit.md)。

维度双线程已 [接入原生 tick 与区块管线](docs/dimension-threading.md)，通过 JVM 实验开关启用，默认关闭。[独立调度模型](experiments/dimension-threads/README.md) 保留为早期协议验证。所有测试运行于无 C2ME 的隔离目录，不修改正式模组目录。

`zombie-challenge`：81×81三层研究所、44房间、错位单梯与安全连续下落路线。一层弹药点长按0.5秒补弹，冷却由每个客户端分别显示；三枪槽（主1、主2、手枪），固定饥饿和两瓶治疗II。战斗力计价的携枪费及B键分类战术轮盘；局内战术点与结算分、永久兑换币独立。每批10–20只随清怪速度持续施压，上限96只/房间；Boss存活无限增援、极限25波。退出/死亡/重连恢复原背包与原有未过期效果。详见 [1.11僵尸挑战说明](docs/challenge-1.11.md)。

`login`：进服凭据核验。**这是这台服务器上唯一的身份关口。**

服务器是 `online-mode=false`，Minecraft 自己不做任何身份校验：客户端在握手里报什么用户名，
服务端就认什么。我们的用户名是平台 UID，而 UID 是从 10000 开始的顺号——不加这道关口，把
用户名填成别人的 UID 就是别人。换一个"不好猜"的登录名并不能解决问题：游戏内 `/msg` 的 Tab
补全本来就会把所有在线玩家的登录名列出来，名字从来不是秘密。

所以这里不问"你叫什么"，而是问平台"这个 UID 刚刚有人拿本人的账号换过票吗"。票由启动器在
玩家发起连接那一刻用本人的 access token 换走，一次性，180 秒过期。冒名者拿得到 UID，拿不到票。

核验失败**一律拒绝**，包括平台超时、502、连不上。放行等于这个功能在最需要它的时候不存在，
而且没有人会发现。代价是 muxi-auth 一挂全服进不来，运维上靠 `features.login.enabled=false`
手动放行。

`identity`：固定平台 UID 游戏身份、平台中文昵称同步、定期刷新，以及禁止玩家通过昵称命令覆盖平台昵称。

`champions`：只让敌对生物成为 Champions 强敌。Champions Unofficial 21.1 把任何 Mob 都当候选，鱼、动物、村民都会带词条、死了掉锭，它自己没有任何配置能收窄。新生成的由 mixin 拦在 `ChampionSpawnHandler.isEligible`；修复前已经变成强敌的，在区块加载时摘掉强敌数据和属性。不需要配置，装了 Champions 就生效。

`chat-completion`（服务端）：普通聊天按 Tab 能补在线玩家的昵称。用原版 `ClientboundCustomChatCompletionsPacket`，客户端不装本模组也生效。

`daily-tasks`：左侧常驻紧凑文字 HUD，同时显示每日任务与主线任务、任务详情、原生物品奖励图标和经验球图标。两个分区都可独立展开/折叠并保存客户端状态；打开聊天或背包后可悬浮查看完整说明，并直接领取或刷新任务。
按 **F8** 打开不暂停游戏的任务界面，鼠标查看详情并领取；生存背包左侧有足够空间时也显示可交互侧栏。
默认每日北京时间 00:00 刷新 3 个普通任务 + 第四个困难任务；普通任务覆盖采矿、战斗、生活，困难任务要求指定种类的 Champions 传奇或更高阶敌人。
默认池 27 项，避开上一任务日出现过的任务，每天所有任务共享一次免费更换。每项奖励经验等级 +1；领奖时应用。
任务类型先从可用任务池随机抽取，再在对应模板的范围/档位中随机生成当天具体要求与奖励数量；生成后立即固化到玩家任务快照，同一天重登、打开界面或领奖都不会重新随机。
普通/传奇/枪械击杀按真实主人归属，支持女仆、傀儡和宠物；练枪命中/爆头仍须本人完成。成熟作物收割和花朵采集由服务端收获事件计数，不扫描世界。
传奇给出 2–3 种敌人任选其一，含幻翼、溺尸和女仆妖精；附魔金苹果只出现在困难任务，数量 1–2。随机目标数与奖励在分配时固定并存档。
新增农夫乐事食品制作，要求数量随机、绿宝石在 4–8 间随机；普通任务不奖励食物。采矿任务使用按稀有度加权的矿物奖励池，含煤、铜、红石、青金石、铁、金、绿宝石和钻石，数量严格落在 2/4/8/16/32 档。任务状态、跨天历史、更换次数与背包存于同一个玩家文件。
详见 [每日任务说明](docs/daily-tasks.md)。

### 显示兼容：昵称而不是 UID（`compat/`）

玩家的登录名就是平台 UID，而且必须是：原版不收中文登录名，皮肤、指令、地图、领地也都按登录名认人。
Simple Nicknames 只换了 `getDisplayName`、头顶名字、Tab 列表和计分板，其余直接读登录名的地方（小地图、指令补全……）仍显示 UID。
`compat/` 下的 mixin 只在**要画到屏幕上的那一刻**把 UID 换成昵称（`nickname/Nicknames`，按登录名精确查 Simple Nicknames 的表）：

- 地图：Xaero 小地图雷达标签、队友追踪标签、分享路径点提示；大地图玩家标记、玩家列表与筛选；OPAC 领地/队伍默认名（两边都套：地图悬浮提示、进领地的动作栏）。
- 指令：补全弹框每行画成 `UID 昵称`，插进指令的仍是 UID；玩家参数可以按昵称、拼音、首字母补全（拼音借 JECh 的 PinIn，服务端没有 JECh 只按子串）。
- 其他：Jade 主人行、墓碑、FTB 队伍界面、"正在输入"、Ping Wheel 标记、排行榜、RS2 的"最后修改者"、原版社交互动界面。

**不变的**：GameProfile、插进指令的文字、Xaero 传送指令、存档和网络里的名字、搜索框的匹配——这些都是身份，一律还是 UID。
昵称允许重名，别拿昵称反查人。每个 mixin 按 `compat.mixin.<键>` 归到目标模组，没装的模组由 `CompatMixinPlugin` 跳过；
兼容配置 `defaultRequire = 0`：目标模组升级、调用点挪走时只是那一处退回显示 UID，不会启动崩溃。升级这些模组后要进游戏看一眼。

Core 负责公共入口、配置和功能生命周期；新增整合包功能实现 `ServerFeature` 并使用独立配置。
登录/身份的私密配置保留在 `muxi-game-core.json`；每日任务的公开玩法配置单独存于 `muxi-daily-tasks.json`。
每日任务已实现；F8 任务界面另有“主线任务”页签，当前只显示“请联系服务器管理员联系夏意”，后续主线任务仍可独立扩展。

```text
src/main/java/net/muxigame/core/
├─ MuxiGameCore.java          # 服务端入口与功能注册
├─ client/                   # 客户端入口
├─ config/CoreConfig.java    # 按功能分组的配置
├─ nickname/Nicknames.java   # 登录名 → 昵称（只给显示用）
├─ compat/                   # 显示兼容：mixin/<目标模组>/ 与各自的纯逻辑
├─ mixin/                    # 强敌规则的 mixin
└─ feature/
   ├─ ServerFeature.java     # 注册、关闭约定
   ├─ login/                 # 进服凭据核验
   ├─ identity/              # UID 与昵称同步
   ├─ champions/             # 强敌只挑敌对生物（配合 mixin/）
   ├─ chat/                  # 普通聊天 Tab 补昵称
   └─ tasks/                 # 每日分配、服务端统计、存档、奖励和可选网络协议
```

### 开 `login` 之前必须先做的两件事

顺序错了会把所有玩家关在门外，而且他们看到的只是"进不去"：

1. **muxi-auth 先上线换票 / 核销两个端点**（`/api/launcher/minecraft/join`、
   `/api/internal/minecraft/join/{uid}`）。服务端开了核验而平台没有这两个端点，
   等于所有人都没票。
2. **启动器先发版并强制更新**。票是启动器换的，旧版启动器不会换票。
   `launcher-release.json` 里把 `minSupportedVersion` 设到带换票功能的那一版，
   旧版才会被挡下来提示更新，而不是让玩家连上去撞一鼻子灰。

两件事都落地之后，再把 `features.login.enabled` 改成 `true` 并重启服务端。

## 工作区布局

```text
muxigame/
├─ muxi-game-core/           # 本仓库
├─ bmc5server/               # 独立的实际服务端包，不进入本仓库
│  ├─ mods/
│  ├─ config/
│  ├─ world/
│  └─ start-muxi.ps1
├─ BMC5Server.zip            # 用户原始压缩包，不修改
├─ better-mc-remake/         # 启动器、官网/API、客户端内容发布
└─ muxi-auth/                # 统一账户
```

## 本地构建

Windows 工作区可直接运行：

```powershell
.\build.ps1 -Test
```

跨平台入口：

```sh
python -m unittest discover -s tests -v
python build.py --server ../bmc5server --java-home /path/to/jdk-21 --test
```

构建只读取本机已有的依赖。无需 Gradle，不访问网络，不下载 Minecraft/模组，不启动游戏。
要求服务端目录已有固定版本 NeoForge libraries 与 `dependencies.json` 中固定哈希的 Simple Nicknames；
客户端部分还要一个装好 1.21.1 客户端库的游戏目录（`--client-game`，默认 `../_client_test/game`）
和整合包的模组目录（`--pack-mods`），`dependencies.json` 的 `compileOnly` 列出要对着编译的目标模组。
这些只参与编译，不进产物。
编译器可使用 JDK 21 或更新版本，输出固定为 Java 21 字节码。

产物：

```text
build/libs/muxi-game-core-1.7.0.jar
build/release.json
```

## 安装到停止状态的服务端

```sh
python install.py --server ../bmc5server --dry-run
python install.py --server ../bmc5server
```

默认服务端即相邻的 `../bmc5server`。安装器检查产物 SHA-256，备份旧 JAR/配置，再原子替换。
它只更新服务端自己的模组、配置及启动脚本，不写客户端、不读写 muxi-auth 的 `.env`、不修改玩家世界。

`muxi_identity` 的旧配置自动迁到 `config/muxi-game-core.json` 下的 `features.identity`，密钥不轮换。
旧 `muxi-identity-*.jar` 会移出生效目录，避免新旧模组同时加载。
私密备份位于 `bmc5server/.muxi-game-core-backups/`，不要公开上传。

## 配置与离线边界

模板见 `config-examples/muxi-game-core.json`。新装没有私密配置时默认不启用登录核验和昵称同步，可以离线加载。
每日任务独立配置、默认启用，无 HTTP 请求。启用 `identity` 或 `login` 后，对应功能需要访问账户服务；
这些是昵称同步/进服凭据核验必需的请求，并非本地构建依赖。旧服务端迁移时保留原先已启用状态。

生产配置含服务端专用密钥，仅保存在游戏服务器的 `config/muxi-game-core.json`。
仓库只提供空密钥模板，程序的配置字符串和错误输出会隐藏密钥。

## 注意

进服凭据验证只在 `features.login.enabled=true` 时生效。UID 是公开标识，不是密码，不能仅凭 UID 发放付费权益。
第一次从旧用户名切为 UID 的玩家存档迁移仍须单独安排。

Simple Nicknames 是独立的上游依赖，不打包或转载到本 JAR/Git 仓库中。
本仓库代码使用 MIT 许可证。来源与提取历史见 [docs/provenance.md](docs/provenance.md)。
