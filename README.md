# muxi Game Core

muxigame 整合包的**服务端功能集成模组**。昵称同步是第一个模块，不再把整个模组限定为昵称工具。

- 模组 ID：`muxi_game_core`
- 当前版本：`1.0.0`
- 当前目标：Minecraft `1.21.1` / NeoForge `21.1.250` / Java `21`
- 当前仅装在游戏服务端。客户端仍需要独立的 Simple Nicknames 显示模组。
- 本地构建、手动安装；没有 CI、GitHub Actions、云端构建或自动发布配置。

## 当前实现

`identity`：固定平台 UID 游戏身份、平台中文昵称同步、定期刷新，以及禁止玩家通过昵称命令覆盖平台昵称。

Core 负责公共入口、配置和功能生命周期；新增整合包功能实现 `ServerFeature` 并添加独立的 `features.<id>` 配置。
将来的活动、任务、服务器规则等可放入对应模块，但**这些功能目前尚未实现**。

```text
src/main/java/net/muxigame/core/
├─ MuxiGameCore.java          # 服务端入口与功能注册
├─ config/CoreConfig.java    # 按功能分组的配置
└─ feature/
   ├─ ServerFeature.java     # 注册、关闭约定
   └─ identity/              # UID 与昵称同步
```

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
