# 冒险世界环境保护

仅服务端 `muxi_game_core:adventure` 生效，不改全服 gamerule。

- NeoForge Detonate 事件只清空爆炸方块列表，覆盖 TNT、苦力怕及使用标准爆炸事件的模组爆炸；伤害、击退、声音保留。
- Level.setBlock 拒绝 FIRE 标签或 BaseFireBlock 火方块，覆盖原版工具、投射物、雷电、岩浆与标准模组点火路径。
- 原版 FireBlock.tick 在该维度停止，避免已有火焰烧毁邻居或继续蔓延。已有建筑、物品和存档火方块不批量删除。
- 生存、家园、地狱及其他维度保留原有规则，包括既有苦力怕保护。

“火焰”本次指环境点火与原版火势蔓延。既有火方块视觉、实体着火/燃烧伤害、岩浆伤害未关闭。绕过标准爆炸事件直接删除方块，或自行实现且不使用 FIRE 标签/BaseFireBlock 的特殊模组效果，需要逐模组适配，不能由标准路径测试推断全部覆盖。

这是模组代码更新，现有服务端需在下一批按原发布流程替换 Core jar 并正常重启生效；没有精确维度热配置。本任务未操作生产服。

## 本机验证（2026-10-03）

从已提交的 Core `a51e523` 建立树内隔离构建快照，排除优化 owner 的未提交文件，仅叠加本模块。完整编译和 6,995 项原有 Java 自检通过，构建 SHA256 为 `250254b35b56883b50b217cd99182b2539fb6e032af7f41bf205058543a7383b`。

专项 QA 仅装载精确的生产保护类、WorldDimensions 和独立 Mixin 配置，不加载身份或网络业务。通过统一 `better-mc-remake/scripts/local_mc_debug.py` 在全新隐藏一服一客户端实例执行，120 条断言通过，两进程正常退出为 0。验证了标准模组爆炸事件方块/实体列表、实际 TNT 与苦力怕来源及无实体来源的燃烧爆炸、火和灵魂火写入、已有火焰 200 次 tick 不烧毁邻居，及家园/生存/地狱范围回归；全服火焰 gamerule 保持开启。

第一轮夹具未加载实体查询区域，伤害断言失败；保留失败回执，第二轮显式预加载区块并断言实体可查询后全部通过。不得把第一轮 runner 的正常退出误当专项通过。

证据位于 `better-mc-remake/build/local-mc-debug/adventure-20261003-131b/`（本地忽略目录）。这证明标准路径的模块行为，不代表全部生产模组自定义爆炸、物理输入、视觉或整包发布验收。

可复用 QA：在 Core 根运行 `python -B tests/adventure-environment/build_qa.py --server <已安装服务端> --java-home <Java21JDK>`，然后通过统一调试工具的 `--server-mod` 加载输出 QA jar，场景依次执行 `adventureqa_prepare`（等待至少 5 秒）、`adventureqa`。必须同时核对 `server/adventure-environment-result.json` 的 passed/120 断言和 runner 的正常退出回执。QA jar 禁止部署生产。

事件接口依据 NeoForge 1.21.1 的 [ExplosionEvent 源码](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/event/level/ExplosionEvent.java)。
