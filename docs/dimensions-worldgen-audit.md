# 生存世界生成修复与 A/B 验证（2026-09-28）

1.9.1 已修复本次发现的维度兼容缺口。两个种子、相同生成顺序、单 C2ME 工作线程的 A/B 测试全部通过，已测群系、完整区块方块状态及结构起点 NBT 均无差异。这是明确样本范围的验证，不是对无限地图、任意探索顺序或所有模组动态玩法的穷尽证明。

正式服务端、信令服务、分发包与正式存档均未启动或修改。只在一次性本地实验目录运行测试。

## 1.9.1 修复内容

保留家园 `minecraft:overworld` 和生存 `muxi_game_core:overworld` 两个真实 ID，不交换世界目录，不全局伪装维度身份。

| 模组 | 已修复行为 |
| --- | --- |
| Blueprint / Environmental | 生存继承主世界群系切片、切片尺寸、切片种子盐值和有序地表修改器，恢复缺少的 8 个群系、木屋与锦鲤候选。 |
| Blueprint LevelNoiseReceiver | 统一生存与家园的特征噪声维度盐值，解决矮云杉密度与高度差异。 |
| Mowzie’s Mobs | 仅在原生自然生成谓词内，让生存继承家园的维度许可。高度、地面、光照、结构与稀有度条件保留。 |
| Serene Seasons | 生存继承家园的季节维度白名单判定，不修改正式配置。 |
| Goblin Traders | 生存增加原生独立计时器，使用自己的 SavedData；家园与下界原计时器保留。 |
| Paster Dream | 竞技场唯一生成标记存入当前生成世界，避免家园先生成后耗掉生存资格；原点裂隙也在生存执行原模组生成路径。 |

补丁仅在对应模组安装时加载，针对本地已安装版本验证。

## 修复后 A/B 条件与结果

构建：`build/libs/muxi-game-core-1.9.1.jar`，SHA-256：`c46a8b061cd59bc45d3fe1abdb53694e3bbbbde700e396c6705b73372b650c28`。

每轮使用当前完整服务端模组、全新独立存档、普通主世界生成器。复用公开世界生成/生物配置、Paxi 数据包与 KubeJS 的 `server_scripts`、`startup_scripts`、`data`；不复制账户配置。公共配置采用测试脚本中的明确白名单，并非整个正式配置目录的逐文件复制。

使用 [C2ME 对照配置](../compatibility/survival-worldgen/README.md)：`globalExecutorParallelism = 1`。测试暂停随机刻和自然刷怪，在同一服务端进程内按相同坐标顺序分别生成 A/B，不对结果进行方块过滤、替换或归一化。

| 检查 | 种子 12345 | 种子 67890 |
| --- | ---: | ---: |
| 两世界可用群系集合 | 135 / 135，相同 | 135 / 135，相同 |
| 群系刷怪表实体类型候选集合 | 281 / 281，相同 | 281 / 281，相同 |
| 建筑候选集合 | 557 / 557，相同 | 557 / 557，相同 |
| 群系坐标采样 | 12,675，0 差异 | 12,675，0 差异 |
| 完整区块 | 12，0 方块差异 | 12，0 方块差异 |
| 比较的方块状态 | 1,179,648 | 1,179,648 |
| 建筑候选区块结构起点 NBT | 96，相同 | 96，相同 |
| 其中含有效结构起点的区块 | 15 | 13 |
| Mowzie 原生自然生成谓词的维度条件 | 7 / 7 允许，两世界相同 | 7 / 7 允许，两世界相同 |
| 季节白名单 | 两世界均启用 | 两世界均启用 |
| 生存独立 Goblin Traders 计时器 | 已创建，归属生存 | 已创建，归属生存 |

12 个完整区块包括四个不同象限区块及 Environmental 八种群系各一个。每个区块逐一比较 384 格高度内全部 98,304 个方块状态并记录 SHA-256。结构检测比较实际序列化的结构起点及分块数据，不只是候选数量；96 个候选区块并不都生成了建筑。

Mowzie 测试暂时中和其他条件以单独验证维度门槛，调用原生 `NATURAL` 谓词后在 finally 恢复配置；不是刷怪蛋测试，也不是全部自然生态的长时间验收。候选集合一致不等于刷怪权重、动态事件、Boss、战利品、进度或农作物的全部行为都已测完。

原始报告：[种子 12345](../build/tasks-smoke-20260928-170757-078485/tasks-smoke-result.json)、[种子 67890](../build/tasks-smoke-20260928-170807-597746/tasks-smoke-result.json)。两轮服务端都正常保存、停止并退出，退出码 0，没有强杀后台线程。

## 一致性的边界

排查中实测 C2ME 并行生成会使相邻区块的原版矿脉覆盖顺序不同，产生少量安山岩/闪长岩差异。相同种子不能消除此问题。因此上述逐方块结果需要配套单线程配置和相同生成顺序；单线程会降低新区块生成吞吐量，也不能保证任意不同探索顺序都得到完全一致结果。配置文件已提供，未覆盖正式服务器配置。

后续无 C2ME 对照也观察到 2 个矿物方块差异，说明该问题并非仅有 C2ME 才会出现；原版后台生成也需要控制调度。参见 [维度线程实验记录](../experiments/dimension-threads/README.md)，这不改变上文已通过测试的运行条件。

已有区块不会重新生成；历史存档中缺失的群系不会通过安装补丁自动补回。玩家建造、传送门平台、随机刻、天气、自然刷怪时间、实体 UUID 等独立世界状态会自然分化。测试没有将这些动态状态或所有方块实体 NBT 纳入逐方块状态哈希。

原 1.9.0 审计的建筑候选为 564/563，那轮没有加载 Paxi/KubeJS，不能把其 564 与这轮 557 直接解释为建筑丢失；本次在同一完整配置下比较，两世界候选集合相同。

## 复现

在 `muxi-game-core` 目录运行：

```powershell
python build.py --java-home ../perf-lab/java21/jdk-21.0.2 --test
python tests/run_tasks_smoke.py --worldgen-audit --serial-worldgen --seed 12345 --java-home ../perf-lab/java21/jdk-21.0.2
python tests/run_tasks_smoke.py --worldgen-audit --serial-worldgen --seed 67890 --java-home ../perf-lab/java21/jdk-21.0.2
```

去掉 `--serial-worldgen` 可运行并行诊断，但不能套用本页的零差异结论。测试使用一次性 `build/tasks-smoke-*` 目录，网络监听由仅测试用的 mixin 禁用，不运行正式启动脚本、启动器或信令客户端。

---

## 历史记录：修复前的 1.9.0 审计

以下内容保留原始排查结论；其中“未修复／尚未验收”描述的是当时状态。当前结果以上文 1.9.1 的检查及边界为准。

结论：当前 1.9.0 通过传送验收，但尚未达到与原主世界完整玩法等价的验收标准。已发现真实的群系、建筑和自然生成限制，不能承诺所有模组建筑／生物正常。本次只增加诊断测试，未更改模组配置或正式存档，未修复以下缺口。

## 运行时对比

使用当前安装的完整服务端模组、新建普通主世界、新生存维度、固定种子 12345。复用公开的 Mowzie、Alex's Mobs、Goblin Traders、TerraBlender、季节配置；其他配置采用新建默认值，未加载正式服 Paxi 数据包和 KubeJS 脚本。因此这是基础整包对比，不是正式服完整配置的复刻验收。

| 项目 | 家园 | 生存世界 |
| --- | ---: | ---: |
| 生成器可用群系 | 135 | 127 |
| 群系生物生成表中的不同实体类型 | 281 | 280 |
| 结构集与可用群系相交的建筑候选 | 564 | 563 |

候选数量包含原版和模组，不等于已实际生成的建筑／生物数量。生物还会经过维度、亮度、高度、频率、上限等运行时检查；Boss、刷怪笼、特殊刷怪器也未必通过普通群系生成表。

采样 25 个区块的结构起点阶段，未命中有效起点；这不能证明建筑不生成，也不能作为建筑成功生成的证据。另有 2 个生存世界区块完整生成成功，没有验证每一种自然刷怪或所有建筑内部的 Boss、战利品与进度。

原始结果：`../build/tasks-smoke-20260928-163808-066473/tasks-smoke-result.json`。

## 已确认缺口

### Environmental

模组内 `data/environmental/blueprint/modded_biome_slices/{blossom,marsh,pine_barrens}.json` 的 `levels` 只包含 `minecraft:overworld`。运行时生存世界缺少以下 8 个群系：

- `environmental:blossom_valleys`
- `environmental:blossom_woods`
- `environmental:marsh`
- `environmental:old_growth_pine_barrens`
- `environmental:pine_barrens`
- `environmental:pine_slopes`
- `environmental:snowy_old_growth_pine_barrens`
- `environmental:snowy_pine_barrens`

同时缺少 `environmental:log_cabin` 建筑候选及 `environmental:koi` 群系刷怪候选。需要将生存维度纳入 Blueprint 群系切片，并核查 Environmental 地表规则的维度限定，不能仅通过添加群系标签解决。

### Mowzie’s Mobs

正式配置 `bmc5server/config/mowziesmobs-common.toml` 中，下列 7 个生物的 `spawn_config.dimensions` 仅允许 `minecraft:overworld`：`grottol`、`lantern`、`umvuthana`、`naga`、`foliaath`、`bluff`、`Elokosa`。

已用本地 JAR 字节码确认 `MowzieEntity.spawnPredicate` 在自然生成路径读取此列表，不含当前服务端维度时直接拒绝。刷怪笼路径有例外，不能用刷怪蛋或刷怪笼成功来证明自然生成兼容。需保留原维度并加入 `muxi_game_core:overworld`，随后验证各自所需的群系、方块、高度和光照。

### Serene Seasons

`bmc5server/config/sereneseasons/seasons.toml` 第 11 行仅将原主世界加入季节白名单；新生存世界的季节行为与家园不一致。需确定采用相同季节规则后追加白名单并验证农作物／气候行为。

## 尚未验收

自定义计时刷怪器（如 Goblin Traders）、其他模组内部的固定维度判定、全部结构实际生成／Boss／战利品、整包 Paxi 与 KubeJS 配置覆盖，以及长时间自然生成。不得将上述候选对比称为逐模组全部通过。

下一轮应先修复已确认差异，再在公开玩法配置完整复刻的隔离环境中对比，并按建筑／生物类别检查自然生成。正式服务未启动，测试未监听端口；退出后清理测试自身的模组遗留线程。

复现命令：

```powershell
python tests/run_tasks_smoke.py --worldgen-audit --java-home ../perf-lab/java21/jdk-21.0.2
```
