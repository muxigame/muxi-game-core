# 原生输入与真实网络联验探针（仅 QA）

该探针补充默认值迁移测试：在真实 `KeyboardHandler.keyPress` 调用链记录 Core 仲裁、TACZ `doInteractLogic` 入口、原 `GameNetwork.Action("outbreak","interact","")` 的客户端发送及服务端接收、原生副手交换包。它没有 OS 键鼠注入，不得作为实体键盘、玩家列表 HUD 或轮盘可见交互验收。

所有读写都要求统一 `local_mc_debug.py` 创建的实例所有权标记、匹配 `runId` 和显式独立实例根。不要把 QA jar 放进发行包。

1. 用 `tests/build_global_input_probe.py --help` 指定已安装的 JDK 21、服务端/客户端库、Core/小游戏框架/TACZ jar，编译到新的私人 QA 输出目录；不下载依赖。
2. 按 `better-mc-remake/docs/local-mc-debug.md` 用统一工具启动新的 `--mode hold --clients 1` 实例。`--mod` 包括探针以及 Core、框架、Outbreak、枪战、TACZ/LRTactical、Waystones/Balm、真实背包及依赖；`--data-dir` 仅用显式选定的 TACZ QA 数据，`--world` 只复制已知 QA 世界。不要复制玩家世界或登录凭据。
3. 运行 `tests/run_global_input_link_probe.py --instance-root <独立实例根>`。默认调试项目是相邻 `better-mc-remake`，可通过 `--debug-project-root` 指定。它确认唯一真实客户端及 hold 所有权，再运行回调、少量物品夹具、真实 Outbreak 建房/开始/离场和原生键盘事件回调。只检查 F 仲裁及上下文联动，不重跑战役胜利和装备业务验收。
4. `input-link-result.json` 和 `input-link-evidence.json` 是输入断言；统一工具的 `run-result.json` 单独证明实际退出码。正常关闭后再检查两个退出码均为 0。统一工具的 hold 成功本身不代表输入通过。

原生事件的修饰键位来自回调参数，没有改写 GLFW/操作系统的物理按键状态；Ctrl+Tab 只检查同事件仲裁与不交换副手。Alt+B 检查实际轮盘/背包绑定仲裁，隐藏窗口不证明轮盘打开或玩家可见 HUD。实体输入需由有可用 OS 输入通道的 owner 在短时焦点协调后完成。
