# 官网下行与游戏 OP

本功能使用固定 UID 和 `OfflinePlayer:<UID>` 对应的稳定 UUID。昵称仅用于展示，不参与授权匹配。

官网仅 `platform_permissions.platform_admin` 为真的会话用户可以按 UID 查询并修改授权等级。账户中心的 `admin` 角色、游戏 OP4、游戏状态回报均不会获得这项权限。官网保存要求同源 JSON、目标身份确认、期望等级、期望版本和变更原因；授权、版本和审计在同一个数据库事务中提交。

官网每次明确保存都递增目标 UID 的版本，包括保存相同等级。Core 分页读取这些版本，写入原生 `ops.json`，随后回报实际等级。同一版本只执行一次；重复拉取和丢失回报后的重试只读取当前游戏等级。游戏内合法修改不会改变官网授权值，下一个更高版本才会重新设置该玩家的游戏 OP。

游戏玩家必须具有原生 OP4，且命令最初来自具有可信 UID/UUID 的非 FakePlayer 玩家。可信服务端控制台与 RCON 保持管理能力。`CommandSourceStack` 保存原始权限来源，并通过所有原生 `with*` 和 `facing` 派生方法传递；降低权限后不能再通过提升有效权限恢复 OP 管理能力。`execute as`、重定向别名、`withSource`、函数、命令方块及自动游戏循环来源都不能借用另一位 OP4 的身份。原生 `opPlayers`、`deopPlayers` 的执行入口与 `/muxiop <UID> <0-4>` 均执行同一权限检查；旧命令源在其原玩家被降级后也会失效。

`<世界目录>/muxi-op-sync-applied.json` 使用 schema 2，包含已应用版本和处理中版本。兼容 schema 1 的已应用记录。顺序为：持久写入处理中记录 → 保存并刷新原生 OP 文件 → 原子提交已应用版本。原生 OP 保存继续调用 Minecraft 的条目序列化器，采用同目录临时文件、刷新和原子替换，避免中断时截断 `ops.json`；其他原生用户列表不受影响。崩溃后先恢复处理中操作；若原生等级已经匹配，只补提交记录。同步或 `/muxiop` 的原生保存失败会回滚内存等级。存在处理中操作或日志损坏时，本地 OP 管理会拒绝执行，防止故障期间的修改随后被恢复操作覆盖。日志损坏不会清空或自动重建。

玩家中心区分官网设定与服务端实际回报。没有回报显示未知，超过五分钟显示过期。只读回报不创建官网用户、不修改授权值、不赋予官网管理权。

## 配置与范围

官网接口默认关闭，启用条件是 `BMC_GAME_OP_SYNC_ENABLED=1`，使用现有 `BMC_GAME_SERVICE_KEY`。Core 的 `features.opSync` 默认关闭，配置字段为 `enabled`、`endpoint`、`serverKey`，endpoint 路径必须是 `/api/internal/game/ops-sync/`，并要求 `identity` 与 `login` 同时启用。密钥不会进入配置字符串表示或错误日志。配置及凭据仅由部署负责人另行决定；本次没有修改生产设置。

初始权限表不会自动转换成下行事件，也不会自动修改现有 UID10000。需由官网管理员明确保存新版本后才下行。原生服务端 OP 文件与消费日志应作为同一份服务器存档备份、恢复；单独回退或删除日志会破坏幂等历史。服务端模组及持有服务密钥的进程属于可信边界，不防御恶意模组直接修改 `PlayerList` 或人工篡改存档。

## 验证

`OpSyncSelfTest` 覆盖离线授予/撤销、重复与冲突版本、本地调整、重启、原生保存失败、写入前日志失败、保存后崩溃恢复、日志损坏及非法身份。

`tests/run_op_sync_smoke.py` 用合成 UID10090/10091 与临时 SQLite 数据库连接真实官网 API，并启动两个独立 NeoForge JVM。测试包括原生 OP0–3 命令拒绝、命名空间/重定向别名、execute、函数、抬高有效权限、命令方块、OP4 正常管理、原生离线文件保存、丢失一次 ACK、本地调整跨 JVM 保留、新官网版本撤销及原生磁盘保存失败后恢复。测试 JVM 禁用 Minecraft TCP 监听，仅临时 HTTP 桥绑定 `127.0.0.1`；不复制生产凭据、配置、世界或玩家。

Windows JDK 21 在本机的 Unix-domain 回环管道出现 `Invalid argument: connect`。夹具只为测试 JVM 选择 Windows selector，并将 Unix socket 临时目录设为不存在的实验路径，使 JDK 回退至 TCP 回环；不修改系统或生产 JVM 设置。

运行示例（所有输出均在隔离仓库 `build/`）：

```powershell
python build.py --server <公开依赖目录> --java-home <JDK21> --client-game <已有客户端依赖> --pack-mods <已有模组依赖> --test
python tests/run_op_sync_smoke.py --server <公开依赖目录> --website <隔离官网仓库>/server --java-home <JDK21>
```

实验结果、两次启动日志、原生 OP 文件、消费日志和合成审计均保存在 `build/op-sync-smoke-*/`。测试专用模组不会打入 Core 发布 jar。本次验证是原生服务端/API 联调，未进行生产服或真实客户端登录测试。

2026-10-01 追加检查：使用实际服务端 340 个模组（无遗漏，仅替换 Core）和全新世界启动。原生 op/deop、FTB CommandReward 的 permissionLevel=4、EasyNPC 玩家来源及 MCPitanLib.withLevel/execute 均无法让原生 OP3 授权他人。实际模组及嵌套 jar 的方法引用扫描未发现其他直接写原生 OP 的入口；模组、脚本和真正控制台仍属于可信边界，扫描不能证明任意反射代码安全。整包停服后有后台线程滞留，测试程序在 30 秒后结束自己的测试 JVM；命令检查通过与正常退出检查分别记录，不将后者记为通过。

浏览器使用真实官网 API 和合成会话验证所有人自己的只读状态、过期与未知提示、OP4/账户 admin 不显示 OP 管理、platform_admin 的 UID 确认保存，以及手机宽度显示。检查使用独立无头 Edge，没有声称新 Core 在实际 MCEF 客户端登录或正式游戏服已生效。
