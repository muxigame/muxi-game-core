# 功能边界

`MuxiGameCore` 只管理配置和功能注册/关闭，不承载具体业务。
`ServerFeature` 定义模块 ID、事件注册与资源释放；每项新功能有独立包和配置段。
`IdentityFeature` 处理 UID 检查、后台资料查询和主线程昵称应用。
其他模块包括登录凭据、冠军怪规则、聊天补全和每日任务，各自独立。

身份网络请求在后台执行，昵称变化在服务器 tick 中应用；HTTP 不阻塞游戏主线程。
FakePlayer/NPC 在入口和定期刷新中均跳过。停服时释放模块网络资源。
关闭身份功能时不会创建 HTTP 客户端，也不要求安装 Simple Nicknames；核心可离线加载。

模块配置：`config/muxi-game-core.json`，格式为 `schema: 1` 和 `features.<功能名>`。
身份专用字段继续是 `endpoint/serverKey/refreshSeconds`，只是放在 `features.identity` 中。
本次不改变统一账户的接口、密钥、UID 或昵称存储方式。

新增功能先实现 `ServerFeature`，再在入口显式注册；不要将活动、任务、服务器规则逻辑塞进昵称类。
每日任务由 `feature/tasks/` 实现；表现层在 `client/tasks/`，不进入身份模块。

客户端与专用服务端有各自入口。客户端只注册显示层与本地单人世界的任务服务，不开启登录核验。
任务网络协议为可选 play payload（daily-tasks-2）；客户端只发送查看请求或领取/更换的「日期 + 任务 ID」，服务端不接受客户端的进度/物品/数量。
统计每 20 tick 检查一次、变更时同步，每 600 tick 校时，不扫描实体或区块。
1.7 的 `defeated` 是独立服务端击杀事件，不与原版击杀统计相加，以免女仆模组已转移统计时重复计数。
`TaskOwnership` 只沿 Projectile/OwnableEntity 的真实主人链归属；无主铁傀儡不猜附近玩家，也不写离线玩家档案。
收获以成熟方块收割次数计，花朵以实际采集掉落计。`BlockDropsEvent` 延迟到 tick 末核对取消、掉落和方块实际变化；RightClickHarvest 使用成功后的专用事件。
`goalMax` / 奖励 `countMax,countStep` 只在分配与更换时抽取，具体数值进入任务快照；领取、重连、死亡不会重抽。

运行时兼容层在安装 CustomNPCs 时把其全局 `MarkData` 缓存替换为 `ConcurrentHashMap`。C2ME 会在多个世界生成线程并行序列化自然生成实体，原版 CustomNPCs 的静态 `HashMap.computeIfAbsent` 会并发修改并令区块任务失败；补丁不改变 NPC NBT 或存档结构。
奖励用完整 `ItemStack` 编解码，客户端用 `GuiGraphics.renderItem/renderItemDecorations/renderTooltip` 原生渲染。
任务进度位于 `NeoForgeData.PlayerPersisted.muxi_daily_tasks`，随玩家 UUID 保存；死亡 Clone 事件复制，不依赖昵称。
领奖先模拟全部奖励装入 36 格主背包，足够才同线程提交；领取位、物品与经验等级一起进入同一玩家 NBT 存档。
`DailyTaskState` 保存前一任务日所有出现过的 ID、当天已出现的 ID 和免费更换消耗次数；更换和领奖立即调用单玩家保存。
`integration/` 中的 TaCZ、Champions 适配只在对应模组存在时加载。射击要求真实健康损失与 TaCZ 事件相互印证，并按射手/弹丸/目标去重。
死亡事件持有至 tick 末端再核实取消状态、死亡状态和任务所属日；只给死亡发生时已分配的任务计数，不能更换后追溯加进度。
单玩家保存通过 `PlayerListSaveInvoker` 调用原版存档方法，不为一次领奖保存全服玩家。
完整边界、已知限制与配置见 `daily-tasks.md`。
