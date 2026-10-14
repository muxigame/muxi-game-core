# 340 与已登记 1.4.38 / 350 的离线组合核对

结论：旧340输入撤出。350个登记产品JAR现全部精确匹配，无缺包；冻结清单为 full-native-preparation/manifest.json，SHA256 604e3790d343023ffd3625c200735f7f33642cb8f5d69b91ccb4c5c16ce700e5。QA模组和调试代理单列；新布局仅Core候选是有意产品差异。没有访问凭证全文、生产主机、启动MC或改生产。

基线证据链：OPERATIONS.md/运维手册.md 1.4.38登记 -> docs/release-1.4.38-artifacts.json -> 已登记 release1438-joint/production-baseline.json（350）+正式 server-overlay 音乐/终端替换 -> COMPLETED-1.4.38.json（modsVerified=350）。同目录 manifest.json SHA157799764fbd5741684f879fb69d0150d73002d76eae89dd7de7ffccb0e4ce71与发布记录相同。联合UI候选的352清单混有未发布跑酷/无尽跑酷并替换终端，已排除，不拿它伪装生产。

不是单纯缺10包：旧输入只与335个登记包完全相同；14个登记文件名缺失、4个旧文件名不在登记，另1个同名MCEF哈希不同。按版本归组：11个新增产品模组减1个退出C2ME，净增10；另外3组版本/文件替换及MCEF同名改包。下表按10组功能列出净变化（音乐与终端两包同组），不隐藏原始文件数量。

| 组 | 登记差异 / 旧目录原因 | 对维度、保存和Sable验证的影响 |
| --- | --- | --- |
| 1 | LesRaisins Tactical 0.4.3缺失；旧目录尚无该发布输入 | TACZ战术实体/物品路径参与完整生成组合；常量池6个ServerLevel类，无直接Sable引用不等同无间接影响 |
| 2 | Flight half.5-recovery.2缺失，项目普通release.json仍指half.2 | 房间/返回/维度桥接必须纳入；14个ServerLevel类、2个teleport类；反射Sable桥接不能仅靠直接引用搜索排除 |
| 3 | Horse race.5-shop-ui-qa.4缺失，普通release.json仍旧0.2.0 | 赛马实体/返回与跨世界位置保存；10个ServerLevel类、2个teleport类 |
| 4 | Minigames equipment.1缺失；先前补入的是同版本名75aead…本地包，登记为c8818f… | 共用房间、安全返回、成员协议与Core ABI关键；4个ServerLevel类、1个teleport类，44个本任务源码按登记ABI重新编译通过 |
| 5 | Music repair-candidate.2与Terminal window-ui.3两项缺失，是1.4.38正式增量 | 登录/终端入口链组合；terminal有传送类；音乐没直接世界引用不代表可省略 |
| 6 | Outbreak school.4-candidate.11缺失，当前构建release.json已是未发布.22 | 地图/怪物/传送、房间数据和生成禁令；21个ServerLevel类、4个teleport类及维度字面量 |
| 7 | Performance qa.4缺失 | 2个维度字面量类，影响新homeworld识别/客户端或缓存路径，保留已交给owner的homeworld小补丁待整合 |
| 8 | Tower-defense compact.2缺失 | 世界实体与返回流程；3个ServerLevel类、2个teleport类 |
| 9 | Zombie challenge social-qa.4缺失 | 关卡世界/实体/社交返回；6个ServerLevel类、1个teleport类 |
| 10 | NMBL 1.0.4缺失，是NetMusic better-login而非世界库 | 音乐登录支持组合；未见直接世界/SavedData/Sable引用，仍属于登记运行依赖，不静默省略 |

其他必要校正：

- C2ME alpha.0.120只在旧本地目录，登记服务器无此包；新验收输入排除，实际旧目录未删。涉及并行世界生成/时序，不能把旧行为等同生产。
- Core 1.7.4 -> 已登记1.12.3-task14-qa.1 / 46acc0ad…。新验收阶段有意替换为维度候选 / 5e47f57e…。直接影响注册、生成禁令、出生/门户、SavedData与构造物所属世界。
- CustomNPCs-Unofficial -> bmc-stock.4 / fe9f1ff4…。两者同上游日期但字节不同；NPC插入/载入/传送直接影响生成禁令和已有实体保留。
- Champion companions 1.0.3 -> 1.0.5 / 8a605861…。涉及实体生成/跟随，必须匹配发布版本。
- 同名 MCEF 2.1.6：旧fee3f495… -> 登记0c769621…。浏览器/JNI/启动组合必须匹配；未见直接世界/Sable引用，但不能按文件名认等价。

准确路径、全部350完整SHA、旧目录差异与实际匹配来源在 registered-production-1.4.38-profile.json；类常量池证据在 difference-impact-bytecode.json。引用计数仅是离线影响线索，不能替代运行验收。补齐来源先取实际项目发布产物/模块构建；剩余五项在实际项目已存在的隔离实例mods目录找到并校验，仅读二进制，没有触碰其他负责人的MC进程/存档/GUI。

重冻结和校验：349个未改产品输入+1个Core，所有350哈希匹配；阶段QA一项、生成代理一项分列；没有Create附属、Outbreak20/21、新跑酷、music0.4.2或YSM upstream。队列未授予时段仍拒绝启动。当前只证明已登记1.4.38的JAR组合等价，不证明当前现场配置/后续变动/完整原生行为或真实数据可迁移；正常停服全量计划未零阻塞前仍readyForProductionMigration=false。
