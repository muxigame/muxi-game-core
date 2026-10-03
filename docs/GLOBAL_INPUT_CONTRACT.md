# 全局键位与业务 owner 协作

默认键位：交换左右手 Tab；玩家列表 Ctrl+Tab；TACZ 枪械交互 F；Tom's Storage 终端 Alt+T；原生武器轮盘 Alt+B。普通 B 背包、终端 K/F8、聊天页 Tab 补全与输入框行为保留。

## 输入优先级

Core 在原生 KeyboardHandler 入口保存本次 F 的玩法占用与修饰键，再发送可选的业务请求。KeyMapping 的 matches/isDown/setDown/consumeClick 使用同一状态；结束时清理被屏蔽的点击队列。WrapMethod 的 finally 覆盖其他 mixin 取消 HEAD 事件和异常路径；其他窗口的回调不改变本实例状态。按住 F 的重复事件不重发拾取请求。

Core 从共享 `GameEquipment.context(player)` 的服务端活动上下文发布 Outbreak 标记及维度，仅在状态或维度变化时发送。客户端匹配当前维度、原小游戏协议可用时占用普通 F，发送原 `GameNetwork.Action("outbreak","interact","")`。Outbreak 继续校验会话、距离、视线、库存，执行拾取与交换；请求不携带物品、数量或库存。Core 不修改库存、不打开轮盘、不处理 Q。登出清理客户端上下文，服务端登出/停止清理发布记录。旧装备框架没有该 API 时保持普通枪械 F。

Outbreak 无需再注册 F；旧右键拾取和提示由其 owner 移除。其他业务可注册无副作用的快速判定并自行处理原事件：

```java
GameplayInputPriority.registerPlainFClaim("game-owner", () -> /* 当前玩法占用普通 F */);
// 或让公共层在占用快照之后调用一次请求入口：
GameplayInputPriority.registerPlainFAction("game-owner", () -> /* 当前上下文 */, () -> /* 原业务请求 */);
```

同一次 F 的状态变化或打开 Screen 不会重新放行枪械交互。蹲下/冲刺的 Shift、Ctrl 不禁用普通 F；Alt+F 与普通 F 分开；相关键位的显式自定义修饰键保持可用。

## 原生轮盘

Core 内置 `key.muxi_game_core.challenge_wheel`，迁移旧 B 或旧未绑定状态到 Alt+B，并屏蔽其普通 B 路径。Zombie owner 继续在原生事件入口严格检查 B、PRESS、仅 ALT，打开原生轮盘 Screen 并处理消费事件。其他原生轮盘可注册实际键位名：

```java
GameplayInputPriority.registerWeaponWheelBinding(WHEEL.getName());
```

Alt+B 屏蔽其他 B 绑定和遗留点击队列；先松 Alt、后松 B 也不会漏到背包。Tom's Storage 的旧 Alt+B 迁到 Alt+T。

## 旧客户端和 seed

Core 在原生 Options.load(boolean) 后逐键迁移旧默认值（包含 NeoForge 注册模组键位后的 load(true) 轮次）。`config/muxi_game_core/input-migrations.json` 为每个已注册键记录一次目标版本；不同于旧默认值的自定义保持原样，玩家之后重新改键也不反复纠正。构造器同步默认值与修饰键，原版与 NeoForge 带修饰键的两个叶构造器均以完整签名覆盖；TACZ/Zombie 走独立的 NeoForge 构造路径，Controls 的 Reset 使用新默认。迁移只替换目标行，保留无关键、注释、换行、未知项；同名重复行一并更新。

五个 seed 字段已集成到 `better-mc-remake/pack/packspec.json` 的 options.txt overlay。保留原有 Seed 文件策略和 `createIfMissing: false`；没有整文件强制覆盖。启动器按 OverlaySeeds 的值版本下发一次，之后继续允许玩家改键。Core 与小游戏框架须随统一客户端/服务端同步，由 008 集成；本任务不发布或部署。

回归脚本：Core `tests/run_global_keybindings_tests.py`；启动器 `client/tests/global-input-seed-smoke/InputSeedSmoke.csproj`（.NET 9，参数为隔离输出目录及 packspec 文件路径）。检查范围须注明：文件/逻辑 fixtures、原生完整包启动与物理输入各自独立。

当前会话无 node_repl，Workspace 列表为空，control_status 返回 Unknown tool。原生回调或代码模拟不算真实键鼠验收；本任务明确保留物理 Tab/F/Alt+B 与实际玩法组合测试缺口。
