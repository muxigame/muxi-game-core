# 生存主世界 / 家园 homeworld：离线候选交接

当前不是“杂项初始化待命”，而是维度迁移开发。没有收到新的暂停或替换指令；最近直接用户原话是“我直接授权，允许你的所有操作”。此前杂项回执误用了早期初始化范围。本轮未停生产、未迁移生产、未部署、未删除任何原世界/玩家实体，也未启动 GUI。

## 映射及已解决的三个问题

| 原世界 | 原 ID | 目标 ID |
| --- | --- | --- |
| 家园 | minecraft:overworld | muxi_game_core:homeworld |
| 生存 | muxi_game_core:overworld | minecraft:overworld |
| 冒险 | muxi_game_core:adventure | 不变 |

- 下界和末地原目录 DIM-1/DIM1 保留。保留既有家园入口/回程规则，迁移元数据 portalHub 默认 home；原生下界/末地回程及无有效床/锚的回退通过角色信息找 HOME。有效床/锚仍优先。世界外 Aether/Twilight 回程配置已定位，但尚未进入配置写入事务。
- 原生存不是没有默认出生点：DerivedLevelData 与原主世界共享 (-496,74,304)。实际原生存区块已存在且 full，y73 草方块、y74 短草、y75 空气。默认保留原坐标，不清理地形。家园原出生点独立保存在角色元数据。
- 原生验收沿用已授权 4 GiB/2 CPU、零客户端、低优先级、正常停服。CPU 曾 97%–99%，期间继续离线工作；降到 29% 后执行自建小存档验收。资源问题属于工程容量判断，不是未取得用户授权。

生产 seed 8324402536652151177；家园/生存/冒险原生成器均 noise / overworld。维度类型、噪声设置和群系 preset 等资源 ID 不做维度 ID 替换。

## 本轮修正与证据

- 实际 Core 路径 C:\Users\ranzh\workspace\dev\muxigame\muxi-game-core；不切分支、不建源码副本、不覆盖其他负责人脏文件。
- 候选 JAR build/world-role-migration-20261014/muxi-game-core-1.12.3-worldroles.1.jar，512242 字节，SHA256 5e47f57e36ae145914d7732f61533ed8002c68252f76c22ab6dc530a2dbe1a88。保持已验证生产 ABI 的增量候选；并非其他负责人的完整最新 Core 发布包，正式发布仍须发布负责人整合。
- 冷测试证实仅改 level.dat 不足：自定义家园生成器会被模组数据包重建覆盖。工具现在把原家园和生存 stem 写入并启用 world/datapacks/muxi-world-roles；格式 48 来自实际 Minecraft 1.21.1 server version.json。seed、生成器、原地形与实体保持原世界绑定。必须长期保留该数据包；重复 apply 会校验三份保留产物的哈希，缺失或改动则拒绝。
- 空 MCA 是合法 NeoForge 占位文件；同时支持非整扇区结尾的 MCA。玩家/NPC .dat_old 纳入 NBT 迁移，level.dat_old 单独同步，均支持精确回滚。
- 已按存储调用方确认 33 种 SavedData 归属，当前顶层生产清单未分类文件名为零。纠正 book_progressions/tournaments/Blueprint/铁魔法等“接口接受 ServerLevel 所以按维度”的错误推断；实际全局入口仍保留根目录。航空气球、NeoForge 附件、季节等随所属世界移动。旧生存 muxi_goblin_trader.dat 改为原生主世界 goblintraders_goblin_trader_spawner.dat，计时器原字节保留；若两套生存计时器实际并存，工具阻断冲突。
- OPAC 实际位于 world/data/openpartiesandclaims，原始未压缩 .nbt 已有合成覆盖。实际 447 路石记录及 18 门户键的原有内存变换证据保留，未写回生产。
- Xaero 单服务器缓存根目录工具 tools/migrate_xaero_world_roles.py 已实现 plan/apply/rollback。Minimap dim%0 / dim%muxi_game_core$overworld；WorldMap null 或 DIM0 / muxi_game_core$overworld，分别迁移为对应 homeworld 目录和主目录。WorldMap 主目录必须明确选定；两套主目录或已有目标都不合并。负载文件不改写，提交/回滚中断可恢复；实际用户缓存未读写。此工具覆盖目录绑定，世界外配置及任何内嵌设置另审计。

验证结果（均在真实项目或自建隔离存档，非生产写入）：

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 迁移合成存档 / 各操作边界 / 原字节回滚 | 124 项通过 | synthetic-result.json |
| 33 项 SavedData 策略 / 商人计时器 / 各移动边界 | 108 项通过 | metadata-synthetic-result.json |
| Xaero 三种目录编码 / 提交及回滚中断 / 防覆盖 | 94 项通过 | cache-synthetic-result.json |
| 真实 Mixin 静态目标 | 44 项通过 | target-audit-result.txt |
| 新映射全新隔离世界 | 270 项通过 | native-fresh-acceptance.json |
| 已迁移隔离存档冷启动 | 275 项通过，正常退出 0 | native-cold-acceptance.json |

270/275 原生证据只针对固定 16 模组无客户端配置，不代表全生产模组或客户端视觉验收。最后冷启动实例 world-roles-native-20261014-114829，PID 57556；先前失败实例 world-roles-native-20261014-114442 PID 40472 也正常退出。失败记录不能计为通过；正确冷测试新增五项验证原平坦家园、原噪声生存、家园回程策略、独立家园出生点及生存默认点。

## Sable / 世界外配置补齐结果

本轮没有访问或修改生产，没有新建生产/世界备份，没有启动 Minecraft 或占用 GUI。实际运行的是 256 MiB/1 CPU 的独立 Java codec 验证器和自建合成输入。

- tools/world_roles/sable.py 支持已审计 Sable 2.0.5 gzip 容器：slvlr 128 字节扇区、slvls 4096 字节扇区、共同 4096 字节索引头、外置标志 16。Sable 运行 JAR 必须匹配 SHA256 c8710f85bc780bbf523e6726ceaaf76dd0b8c61479db497b382c6bb34317e7ab。slvlr holding pointers、slvls uuid/pose/world_bounds/plot(data_version=1)/dependencies 的结构必须识别；holding 指针的 storageIndex/subLevelIndex 必须对应现存、可识别的 slvls 槽位。父世界绑定从所属世界 data 同级 sublevels 路径获得，迁移随对应家园/生存目录移动。
- 内部改写采用追加扇区、保留旧扇区及索引原像；外置原件移入同一事务恢复目录后写新版本。UUID、指针、plot 方块、实体属性、姿态、边界和加载依赖不改写。仅明确的维度字段/映射重写；显示名、资源字段保持原样。未知版本/标志/结构、非 gzip、共享或孤立外置文件、悬空指针、未知/嵌入式维度引用及不透明字节旧 ID 都阻断。内部改写超过 255 扇区而需要转换为外置文件，当前明确拒绝，不猜测转换。
- tools/world_roles/configs.py 把明确指定的同级 config 根目录、已审查字段、源文件哈希纳入同一世界日志。JSON/TOML 都先校验结构，字段以外语义必须相同；TOML 保留注释/布局。配置旧 ID 必须列入策略，否则拒绝。默认 home 回程策略下 Aether/Twilight 原家园回程、Paster 出生维度、YIGD 按世界设置等保持原物理世界绑定。服务器存在 config 却只计划 world，或历史已提交 world-only 日志未覆盖 config，都不能误判为完整服务器迁移。defaultconfigs/configureddefaults/kubejs 中出现旧标记，会明确列为未覆盖的生产者/schema 阻塞，注释和资源用途不猜测。
- apply 在持有世界锁时重新生成审计计划，手工删除 blockers/操作不能绕过识别门槛。world/config 快照变更、目标冲突或迁移后进度会拒绝覆盖。写入/回滚都有持久化状态；配置及元数据原子 staging 用独占创建，已确认的本事务中断 staging 才可清理恢复。回滚自身中断后可以继续。

验证：统一事务 507 项通过，覆盖 64 个写入故障点和 44 个回滚故障点；实际 Sable 容器/serializer/存储加载器在迁移前后各 24 项通过，包含指针解析到构造物和现存依赖目标。额外活动根目录边界 4 项通过。原迁移 124、元数据 108、缓存 94 项在本轮复核通过。8 个本地配置与先前生产元数据 SHA 完全一致，15 个实际字段在内存改写验证通过，源配置未改、配置正文未保存；immersive_aircraft.json 本地不存在，当前没有替它宣称完成真实 schema 核查。

精确工具候选：build/world-role-migration-20261014/offline-sable-config-candidate.json，候选名 world-roles-offline-sable-config.1。它列出每个工具/测试源文件 SHA、固定 Sable/companion 输入、配置策略、验证结果、明确拒绝的数据及真实迁移前置条件。运行 Core JAR 仍是上文 5e47f57e… 候选，本轮未改其运行源码/二进制，不混入其他负责人工作；owned-files.json 列出全部本任务文件。

## 剩余原生验收与真实迁移前置条件

1. 在实际完整模组组合下验证 Sable 构造物/plot/实体的冷加载、强制加载票据、首次保存/重启和物理运行；核对 UUID、加载依赖、方块/姿态/边界。独立 Java codec 通过并不证明完整游戏/原生物理后端通过。
2. 验证迁移后 Aether/Twilight/Paster/YIGD/路石与原生下界/末地回程、有效床/锚、首次登录等实际运行行为；此前固定 16 模组零客户端 275 项不能覆盖全部生产模组。
3. 真实迁移必须另有维护/切换授权，正常停服且所有 world/config 写入者停止，取得 session.lock，确认磁盘空间。重新核对当前完整 Core 发布包和 Sable SHA，由发布负责人整合并行 Core 改动；当前候选基线不能被当作当前生产版本保证。
4. 使用实际离线 world/config 和已审查 SavedData、Sable JAR、配置策略做完整计划；未知结构、未核查配置、默认配置/脚本旧引用、哈希变化和真实数据冲突必须全部清零。只对真正已有目标/玩家进度冲突作明确选择，不合并、删除或猜写。实际 immersive_aircraft.json 等缺失本地对应文件须现场识别，不可因元数据没有值引用便推定无绑定。
5. 开放玩家写入前核对生成器/seed/出生点、实体与构造物、领地/门户及明确选定的客户端缓存。回滚只适用于原字节条件仍成立的阶段，且需要匹配旧世界角色的运行版本；不能丢弃上线后的进度。

当前 readyForRelease=false、readyForProductionMigration=false。已完成审计范围内的离线实现；没有宣布生产可迁移，也未给可直接执行的生产切换命令。

此前自动审批拒绝了通过 SSH 传输生产配置全文；元数据替代已完成。本轮只用了本地文件及既有字段/哈希记录，没有再请求或执行全文传输。

## 切换与恢复边界

CLI tools/migrate_world_roles.py 默认计划；写入要求明确离线授权和 session.lock 锁。当前生产只能读取必要元数据，不执行停服/迁移/部署。主世界与家园原 terrain/entities/poi/sublevels 同卷移动；全球与每世界 SavedData 分别处理，不整世界复制，不删原数据。旧 MCA 扇区保留，修改块追加，外置 MCC 原件同卷移入事务恢复目录，小元数据及头部原像留在日志。

回滚仅在原字节前提仍成立时执行；首次上线后的任何新存档进度会阻断自动全字节回滚，不能承诺无损撤销后续玩家进度。日志/旧扇区不是整盘灾难备份。原两世界总约 4.07 GB、当时盘空约 27.2 GB；额外空间依实际变化 payload/metadata/journal 计划计算。生产全量扫描及停机时间仍未实测。
