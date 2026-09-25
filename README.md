# muxi Game Core

muxigame 整合包的**功能集成模组**：服务端功能（登录核验、昵称同步、玩法规则），以及客户端的显示兼容（到处显示昵称而不是 UID）。

- 模组 ID：`muxi_game_core`
- 当前版本：`1.3.0`
- 当前目标：Minecraft `1.21.1` / NeoForge `21.1.250` / Java `21`
- 服务端和客户端装同一个 jar：服务端入口 `MuxiGameCore`（`dist = DEDICATED_SERVER`），客户端入口 `client/MuxiGameCoreClient`。
  没有自定义网络通道，两边版本不一致也能进服，只是缺的那一半功能不生效。昵称数据来自 Simple Nicknames（两边都装）。
- 本地构建、手动安装；没有 CI、GitHub Actions、云端构建或自动发布配置。

## 当前实现

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

Core 负责公共入口、配置和功能生命周期；新增整合包功能实现 `ServerFeature` 并添加独立的 `features.<id>` 配置。
将来的活动、任务、服务器规则等可放入对应模块，但**这些功能目前尚未实现**。

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
   └─ chat/                  # 普通聊天 Tab 补昵称
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
build/libs/muxi-game-core-1.3.0.jar
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

模板见 `config-examples/muxi-game-core.json`。新装没有配置时 Core 默认不启用任何功能，可以离线加载。
启用 `identity` 后，只有这一模块需要通过 HTTPS 读取账户昵称；这是昵称实时同步必需的请求，
并非本地构建依赖。旧服务端迁移时保留原先已启用状态。

生产配置含服务端专用密钥，仅保存在游戏服务器的 `config/muxi-game-core.json`。
仓库只提供空密钥模板，程序的配置字符串和错误输出会隐藏密钥。

## 注意

本模组没有实现服务端 OAuth 进服凭证验证。UID 是公开标识，不是密码，不能仅凭 UID 发放付费权益。
第一次从旧用户名切为 UID 的玩家存档迁移仍须单独安排。

Simple Nicknames 是独立的上游依赖，不打包或转载到本 JAR/Git 仓库中。
本仓库代码使用 MIT 许可证。来源与提取历史见 [docs/provenance.md](docs/provenance.md)。
