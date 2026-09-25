# muxi Game Core

muxigame 整合包的**服务端功能集成模组**。昵称同步是第一个模块，不再把整个模组限定为昵称工具。

- 模组 ID：`muxi_game_core`
- 当前版本：`1.2.0`
- 当前目标：Minecraft `1.21.1` / NeoForge `21.1.250` / Java `21`
- 当前仅装在游戏服务端。客户端仍需要独立的 Simple Nicknames 显示模组。
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

Core 负责公共入口、配置和功能生命周期；新增整合包功能实现 `ServerFeature` 并添加独立的 `features.<id>` 配置。
将来的活动、任务、服务器规则等可放入对应模块，但**这些功能目前尚未实现**。

```text
src/main/java/net/muxigame/core/
├─ MuxiGameCore.java          # 服务端入口与功能注册
├─ config/CoreConfig.java    # 按功能分组的配置
└─ feature/
   ├─ ServerFeature.java     # 注册、关闭约定
   ├─ login/                 # 进服凭据核验
   ├─ identity/              # UID 与昵称同步
   └─ champions/             # 强敌只挑敌对生物（配合 mixin/）
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

构建只读取已安装服务端的依赖。无需 Gradle，不访问网络，不下载 Minecraft/模组，不启动游戏。
要求服务端目录已有固定版本 NeoForge libraries 与 `dependencies.json` 中固定哈希的 Simple Nicknames。
编译器可使用 JDK 21 或更新版本，输出固定为 Java 21 字节码。

产物：

```text
build/libs/muxi-game-core-1.0.0.jar
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
