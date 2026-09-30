# 家园、生存世界与冒险世界

主世界体系包含家园、可周期重置的生存世界，以及长期保留的冒险世界。原有下界、末地、暮色等模组维度及挑战维度继续保留；不新增永夜世界。

| 名称 | 注册 ID | 存档位置（相对世界根目录） |
| --- | --- | --- |
| 家园 | `minecraft:overworld` | 原来的 `region/`、`entities/`、`poi/` |
| 生存世界 | `muxi_game_core:overworld` | `dimensions/muxi_game_core/overworld/` |
| 冒险世界 | `muxi_game_core:adventure` | `dimensions/muxi_game_core/adventure/` |

家园只改显示名称，不迁移原世界或修改注册 ID。Xaero 大地图维度菜单显示中文名称，地图文件、建筑、领地及原有玩家记录仍使用原 ID。生存世界与冒险世界都使用主世界生成器、群系预设及同一存档种子，各自独立保存区块；已知的主世界镜像兼容（Blueprint 群系/地表/噪声、Mowzie 刷怪、季节、Goblin Traders、Paster Dream 结构存储）同时覆盖两者。三个主世界型维度均保留正常昼夜和睡眠。

## 重置策略

`WorldDimensions` 将重置策略作为正式数据保存，而不是依赖目录命名：

- 家园：`resettable=false`，长期保留。
- 生存世界：`resettable=true`，允许未来按赛季或运营需要重置。
- 冒险世界：`resettable=false`，长期保留，用于持续制作剧情、建筑、地牢或活动内容。

当前核心本身不会自动删除任何世界。未来实现世界重置工具时，只允许选择 `resettable=true` 的目标；`muxi_game_core:adventure` 不应进入自动重置列表。

## 建造双向门

1. 建造外侧 **4 格宽、5 格高**的完整矩形门框，四角必需，内部留出 **2×3** 的空洞。东西向、南北向都支持。
2. 框架允许圆石、石头、石英块、泥土、草方块，可混用；暂不包含石英矿石、台阶、楼梯或其他变种。草方块自然退化成泥土仍有效。
3. 手持打火石右键门框即可激活，站在门内约 2 秒传送。

家园中的普通双向门仍指向生存世界，生存世界中的门指向家园。冒险世界暂不新增普通玩家入口，避免在内容尚未设计时提前确定三选一的门交互；管理员可用 `/muxiworld adventure` 进入，冒险世界内仍可建返回家园的门。首次使用现有家园/生存门时仍会在目标世界相近坐标生成并保存配对回程门。

自动建门仅使用空余空间，检查世界边界及方块放置保护事件；失败时保留玩家位置并提示。目标门损坏时，需要在原位置修复并点亮，不会悄悄改连其他门。配对记录保存于家园存档的 `data/muxi_world_portals.dat`，全体玩家共享。

到达后需先走出门，等待约 2 秒再重新进入，防止在门中来回弹跳。背包、装备、经验共享。本期专用门只传送玩家；睡眠、骑乘、载客、打开容器、鼠标持物、参与挑战时不允许穿门。

## 冒险入口

暮色、下界和末地入口保留在家园，沿用原版／模组的激活条件和返回机制。生存世界和冒险世界都按主世界型探索维度处理：禁止下界门点火、末地之眼激活末地框架、暮色钻石水池成门，并拦截前往家园以外维度的传送。旧的或管理员放置的入口同样不能借此绕过限制。

末地出口仍按原版规则返回重生点／出生点，不保证回到进入的末地门；若玩家将重生点设在生存世界，原版末地出口可返回该重生点。暮色、下界沿用各自原有寻门规则。本期没有创建第二套下界、末地或暮色，也没有改写它们的返回算法。

## 管理员与范围

`/muxiworld`、`/muxiworld home`、`/muxiworld overworld`、`/muxiworld adventure` 仅限权限等级 2 的管理员，用于调试、救援和后续内容制作。普通玩家仍通过现有实体门在家园与生存世界之间出行。管理员传送保存各世界位置并查找安全落点，不能绕过主世界型探索维度的外部维度入口限制。

本期不新增家园禁怪、禁破坏、安全区或背包隔离，不调整怪物强度与刷怪倍率。生存世界和冒险世界共享同一套主世界型模组生成兼容。

## 生成兼容状态

1.9.1 修复 Environmental 群系／地表／特征噪声、Mowzie 自然生成维度条件、季节白名单、Goblin Traders 独立计时器与 Paster Dream 生成存储。两个种子在单 C2ME 工作线程和相同生成顺序下通过零差异 A/B。传送测试与这些样本不代表所有模组动态玩法均已穷尽验收。具体数据、复现命令和边界见 [生成兼容审计](dimensions-worldgen-audit.md)。

## 本地验证

在仓库目录运行，使用 JDK 21：

```powershell
python build.py --java-home ../perf-lab/java21/jdk-21.0.2 --test
python -m unittest discover -s tests -v
python tests/run_tasks_smoke.py --portals --java-home ../perf-lab/java21/jdk-21.0.2
python tests/run_tasks_smoke.py --dimensions --full-pack --java-home ../perf-lab/java21/jdk-21.0.2
python tests/run_dimensions_e2e.py --java-home ../perf-lab/java21/jdk-21.0.2
```

传送门测试运行真实 NeoForge 与暮色模组，检查五种门框、实际跨维度和回程、草方块退化、破损门、领地事件回滚、家园三类冒险入口的激活及往返、生存世界禁用入口。完整模组服务端测试使用新配置、新存档，不监听端口。原生客户端测试仅连接 `127.0.0.1` 的随机端口，实际点火、进入门、往返、另建回程门、重连，并检查背包、经验、维度同步和 Xaero 名称；保存截图。

报告位于 `build/tasks-smoke-*`、`build/dimensions-e2e-*` 和 `build/tasks-client-smoke-*`。测试仅复用现有模组和公共依赖，不复制账户密钥，不调用正式启动脚本、启动器、路由代理或信令服务。完整模组的个别后台线程可能在服务端停止事件后存活，测试会关闭其自身临时进程并在报告中标记。

正式服务端及客户端分发包未安装此版本。新维度需完整重启才能载入，普通 `/reload` 不会创建新世界。部署时应备份存档并在两端安装相同构建。

### 1.9.0 传送基线（2026-09-28）

构建 `1.9.0`，SHA-256 `cb9093cbc0ebf90add955d0646dde5259acc5640da7b3e180f17b69e0c8018c4`。

- Java 自检 6910 项、Python 安装回归 8 项通过。
- 实际传送门服务端测试 82 项通过：`build/tasks-smoke-20260928-153247-934270/tasks-smoke-result.json`。
- 完整模组维度检查 33 项通过：`build/tasks-smoke-20260928-152756-450140/tasks-smoke-result.json`。服务端已完成保存与停止，随后清理仍存活的模组后台线程。
- 原生客户端实体门及重连检查 29 项通过：`build/tasks-client-smoke-20260928-153215/client-smoke-result.json`。
- 本地端到端测试服务端正常退出，退出码 0：`build/dimensions-e2e-20260928-153201/e2e-result.json`。

### 1.9.1 修复后验证（2026-09-28）

构建 SHA-256：`c46a8b061cd59bc45d3fe1abdb53694e3bbbbde700e396c6705b73372b650c28`。

- Java 自检 6910 项、Python 安装回归 8 项通过。
- 实际传送门服务端检查 82 项通过：`build/tasks-smoke-20260928-171207-205435/tasks-smoke-result.json`。
- 两个种子共 25,350 个群系采样点、24 个完整区块的 2,359,296 个方块状态、192 个建筑候选区块的结构数据，差异均为 0；详见生成兼容审计。
- 原生客户端实体门和重连 29 项通过：`build/tasks-client-smoke-20260928-170858/client-smoke-result.json`。
- 本地端到端服务端正常退出，退出码 0：`build/dimensions-e2e-20260928-170840/e2e-result.json`。
