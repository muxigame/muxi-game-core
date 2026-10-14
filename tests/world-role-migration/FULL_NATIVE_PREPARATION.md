# 完整本地模组组合：原生验收准备（尚未启动 MC）

当前只完成输入冻结、源码/API核对、编译和哈希复核。完整组合尚未运行；此前 16 模组 275 项及独立 Sable codec 24 项不等同本组合或生产验收。

以维护手册登记的已完成 1.4.38 发布为基线：350 个产品 JAR 全部按精确 SHA256 和尺寸从实际项目已有运行产物/发布产物中匹配，缺项为零。来源是发布前350库存加正式音乐/终端两项替换，公网 manifest SHA与发布记录15779976…完全一致。352项联合UI候选含未发布跑酷两项，不能作基线，已排除。

旧本地340目录不再用于执行输入。每阶段349个未改产品JAR + 指定旧/新Core = 350；新阶段只把Core 46acc0ad…替换为维度候选5e47f57e…；框架恢复生产登记c8818f…，NPC和所有其他产品包不改。每阶段另加一个QA模组和生成调试代理，共352运行模组（QA两项分列，不冒充生产350）。清单验证登记JAR组合，不代表重新核查当前现场配置/加载器/后续临时增删；已冻结服务器库/JDK以供核对。

关键哈希：

| 输入 | SHA256 |
| --- | --- |
| 冻结清单 manifest.json | 604e3790d343023ffd3625c200735f7f33642cb8f5d69b91ccb4c5c16ce700e5 |
| 旧 Core 基线 | 46acc0ad4bd4b3cd0ff3ed812f649cfbbe6fc825820b3c9bca9267b8e0547d59 |
| 新 Core 候选 | 5e47f57e36ae145914d7732f61533ed8002c68252f76c22ab6dc530a2dbe1a88 |
| 完整生命周期 QA | e57e67d1acbb8d74c566f0cbd0220df57726f873a3b2ed7ec7c63d16b9df2a4b |
| 新维度/生成禁令矩阵 QA | 7acb488f470f007fdfe48ff21f75795d8c3da9fe9727167dc736f4999fd74370 |

输入差异必须保留：这350个产品JAR与登记完成的1.4.38清单相等，尚未重新探测现场后续变化或配置等价性；仅八个公开模组配置有冻结 old/new 输入（15 个维度字段），其余模组在自建实例生成默认配置。没有复制生产世界、玩家数据、账号凭据或私有配置。合成 seed 固定 8324402536652151177，不复制真实地形。

## 可执行步骤和启动条件

所有命令均在实际 Core 仓库运行。当前允许执行 prepare / verify-inputs；run-stage 必须等队列负责人明确轮到本任务并授予 slotGranted=true。slot-receipt.template.json 现在为 false，已验证会在任何 MC 启动前拒绝。不得因模板存在就改为 true；当前 Create 两项之后还有音乐/YSM轮盘。

```powershell
$pyTask = 'C:\Users\ranzh\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$qaTask = 'tests/world-role-migration/prepare_full_native.py'
$prepTask = 'build/world-role-migration-20261014/full-native-preparation'
& $pyTask -X utf8 -B $qaTask verify-inputs
# 以下仅在队列负责人授予本任务独占时段后：
& $pyTask -X utf8 -B $qaTask run-stage --stage seed-old --slot-receipt "$prepTask/slot-receipt.json"
& $pyTask -X utf8 -B $qaTask prepare-cases
& $pyTask -X utf8 -B $qaTask run-stage --stage matrix-new --world "$prepTask/inputs/migrated/world" --slot-receipt "$prepTask/slot-receipt.json"
& $pyTask -X utf8 -B $qaTask run-stage --stage verify-new --world "$prepTask/inputs/migrated/world" --slot-receipt "$prepTask/slot-receipt.json"
# restart-new 的输入必须替换为上一 verify-new 成功且正常停止的自身实例 server/qa-world 路径：
& $pyTask -X utf8 -B $qaTask run-stage --stage restart-new --world '<verify-new 自身实例>/server/qa-world' --slot-receipt "$prepTask/slot-receipt.json"
& $pyTask -X utf8 -B $qaTask run-stage --stage roundtrip-old --world "$prepTask/inputs/roundtrip/world" --slot-receipt "$prepTask/slot-receipt.json"
```

每条失败立即停止后续阶段。prepare-cases 只接受本任务成功且正常停止的 seed-old 实例，构造两个独立自建输入：原生新布局冷加载、apply后字节级rollback再旧布局冷加载。不读取生产存档；不覆盖/合并已有输入。任何未知 SavedData、Sable结构、配置引用或冲突会阻断，不能手工删 blockers。输入变动必须重新冻结，不能减模组冒充完整组合。

启动器：4 GiB/2 CPU，零客户端、loopback随机端口、低优先级、nogui，关闭隔离 Core identity/login/terminalSso/opSync；仅正常停服。必须验证各阶段 QA success、正常退出0、实际实例 mod hashes、无保留 PID。普通 Done/hold/连通成功不构成行为验收。现有执行器结果与生命周期判断可运行；实际完整模组与原生 DLL/物理后端是否兼容尚未实测，失败必须作为验收阻塞报告。

## 冻结关键行为

| 项目 | 隔离输入/断言 | 仍未覆盖 |
| --- | --- | --- |
| 注册与生成器 | 新 HOME、原生主世界、旧 B 不注册，冒险/下界/末地存在，原 flat-home/noise-survival及seed保留 | 完整组合尚未启动 |
| 家园禁令 | 275矩阵的真正生成/插入/模组调用链放到完整组合；家园 protectedHome=true，生存 false | 其他模组增加的来源需结合失败/日志审计 |
| 传送/出生 | 独立家园出生点与 EndPortal 实际返回目标；8配置字段保持家园回程 | 真实下界往返、Aether/Twilight消费者链、有效床/锚/缺床及首登需扩展/网络客户端验收 |
| 存档重载 | 保留实体UUID和27.5血量，第一次保存后再次冷启动 | 没有真实玩家/客户端、未测生产实际对象 |
| Sable往返 | 实际分配小构造物、放方块、COMMAND_FORCED票据；新布局及回滚旧布局冷加载，UUID/name/plot寻址/方块/user_data父世界/ticket与实体断言 | 原生物理稳定性、精确姿态/速度、复杂加载依赖与实际大型构造物需扩展；当前仅编译 |

预估：离线准备约2–5分钟；轮到后的五次完整组合冷启动约40–90分钟，每次启动上限900秒、保持180秒、正常停服120秒。未测完整组合时间或内存峰值；4GiB不足则停止报告容量，不加内存、不减少模组、不改生产。真实全量扫描/迁移停机时间仍不能给实测值。

## 隔离可先完成与必须停服的边界

无需停生产即可做：清单/源码/API/注册资源的静态复核，合成world与配置的计划/迁移/失败恢复，独立codec验证；轮到资源时可执行本流程五阶段、无真实玩家的完整组合生成禁令/实体/小构造物冷热加载及旧新往返。当前已完成507集成+124迁移+108元数据+94缓存检查，但完整组合原生项仍未执行。

必须实际正常停服并停止所有world/config写入者才可做：取得生产 session.lock；对真实 region/entities/poi/Sable/players/SavedData/claims 作一致性全量快照和交叉引用计划；重新核对实际配置哈希及全部未审查引用，计算真实额外空间/时间；得到零阻塞计划后才可另经维护授权做真实目录/配置变更及原字节恢复验证。生产当前不能移动、apply、试回滚或复制为新备份。

可在线的必要只读库存/固定模组文件版本与哈希检查不证明世界一致性。本轮没有访问生产。实际 immersive_aircraft.json 等缺少本地匹配文件、默认配置/KubeJS旧引用、未知结构和真实目标/玩家进度冲突都必须在现场计划里清零；未知数据不猜写。开放真实玩家写入前还须核对生成器、出生、实体/构造物、领地、门户和明确选定的客户端缓存。

readyForProductionMigration=false；不因当前独立codec、16模组冷启动或本次准备通过而改变。
