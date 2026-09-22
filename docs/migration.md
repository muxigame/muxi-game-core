# 从 muxi-identity 迁移

| 原名称 | 新名称 |
|---|---|
| `muxi_identity` | `muxi_game_core` |
| `muxi-identity-1.0.0.jar` | `muxi-game-core-1.0.0.jar` |
| `net.muxigame.identity` | `net.muxigame.core` |
| `config/muxi-identity-bridge.json` | `config/muxi-game-core.json` 的 `features.identity` |

服务端停止后执行 `python install.py --dry-run`，核对后去掉 `--dry-run` 安装。
只转换配置结构，保留原 endpoint、serverKey 和刷新间隔；不生成新密钥，不操作账户服务。

安装前，旧文件会备份到 `bmc5server/.muxi-game-core-backups/<时间>/`，
`restore-index.json` 记录原路径。备份包含私密配置，不可公开上传。
任一安装写入失败，会还原之前已修改的生效文件。相同版本再次安装为 no-op。
已有新配置优先，不会被旧配置覆盖。

游戏世界、玩家数据、其他模组、客户端干净源及原始 ZIP 不参与替换。
服务端现位于 `muxigame/bmc5server`，原来的重复嵌套目录已移除。
新 `start-muxi.ps1` 只选择 Java 21 并启动本目录，不启动或终止 FRP，不操作 RCON。
