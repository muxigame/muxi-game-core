# 光影进服与维度采样运维交接

公共调试工具由 Outbreak owner 统一整理。本页和 `tools/qa/shader-sampling-reference/`
提供已经运行成功的业务 runner 源码、探针和使用契约，供其接入公共入口；不新建第二套通用运行器。
统一入口现已交付：`better-mc-remake` dev `9b8d564accd750b4c5b1e94a7556f6a20444da85`，
使用同级仓库的 [scripts/local_mc_debug.py](../../better-mc-remake/scripts/local_mc_debug.py)，
先读 [docs/local-mc-debug.md](../../better-mc-remake/docs/local-mc-debug.md)。
后续新测试统一经此入口管理新建实例、独立 loopback 端口、资源预算与正常退出。
光影业务探针和分段断言仍需接入；通用双客户端冒烟不替代首登、切维度和光影重载验收。
原始运维手册已登记 6.5。当前参考只保存既有成功运行证据和业务采样源码，
其中私有路径及集成服务器流程不应被当作第二套通用入口。
收到统一入口通知时已经运行的 `064541-0306f393` 单客户端轮次按原流程完成，
不迁移工作目录，也不将它伪称为新公共工具执行的结果。

## 已成功运行的实例

工作目录：`C:\Users\ranzh\Documents\Codex\2026-10-02\task-5`。
实例：`transfer-lab/native-on-fix-pctrl-20261003-055919-0a8f7758`。
结果：3 次测量进服、8 条路线，正常退出 0；候选包与源码哈希见参考目录的 `source-manifest.json`。

已执行的入口：

```powershell
C:\ProgramData\anaconda3\python.exe binary-audit/prepare_shader_fix_round.py
C:\ProgramData\anaconda3\python.exe launch_prepared_transfer.py shader-fix-on
C:\ProgramData\anaconda3\python.exe binary-audit/read_shader_fix_progress.py
C:\ProgramData\anaconda3\python.exe binary-audit/verify_shader_fix_native.py
```

prepare 只准备目录、候选 mod 和 QA 编译，不启动游戏。launcher 是独立启动动作。
已启动或产生 pid.json 的 lab 不可再次准备覆盖；创建新目录。只允许一个本 owner 的 MC。
`--resume` 只用于还未启动的准备目录。暂停或其他 owner 实例不得启动、停止或接管。

## 公共入口需要承接的输入

- 用户授权的原仓当前 dev，不切分支或 worktree，保留其他 owner dirty。
- Java 21、Python 3.12；现有正版安装提供的 client/server 库与 assets；不下载或分发这些二进制。
- 只读模组源、私有复制的世界模板、离线 QA 名称、token 0、网络代理隔离。
- candidate receipt：artifact/repository/commit/sha256/buildSource，及对应框架 jar。
- 原参考 prepare 读取 `latest-veil-stability.json` 的旧已验证世界和 QA 资源。
  `validated-qa-source` 已保留成功实例的 Java 源，公共工具应以显式 fixture 参数替代历史 pointer 隐式依赖。
- Windows runner 当前固定本机 JBC_FCRL/session 2；公共实现应验证目标会话并用隐藏窗口启动。
  禁止物理键鼠自动化、读取账号/密钥内容或使用生产 008。
- 此样本为框架 0.2.1，移出不接受该版本的两个旧游戏 jar。
  公共入口必须检查版本组合并记录排除项，不能静默更改测试整包。

## 采样与判定

`JoinNativeQA` 从真实客户端 handleLogin 到画面可见且输入不受阻计算进服耗时。
openWorld 到 handleLogin 的本地服务器准备、启动游戏及模组加载不属于此指标。
旧参考 runner 的 240 秒保护覆盖整个 openWorld 请求，会在准备较慢时提前中止客户端验收。
公共入口须拆分准备超时和 handleLogin 开始后的客户端超时；失败时仍保存 native spans、
过滤器实际守卫状态、内存快照和 qualifiedVisible，不可丢弃已出现的客户端长尾。
3 次进服区分真首登、第一次重连和第二次重连；不能把重连优化写成首登重复管线修复。

8 条传送路线为同维度短距 2、同维度 16384 方块远距 2、三个维度往返 4。
目标 landing 已预生成不代表全部邻近区块已预生成。每条都要检查目标维度、正常 Iris 管线、
无 fallback、服务端观察到移动、退出后自有缓存清空及进程正常退出。

`PipelineConstructionProbe` 计成功构造次数，不能用每帧 `preparePipeline` 调用次数代替。
`RouteTimeline` 覆盖请求、服务端任务、目标区块、mesh/upload、可见和服务端移动标记。
native stage 累计值与 sparse spans 不同；分段按测量窗口裁切，嵌套和不同线程不能直接相加。
缺省、显式 ON/OFF、二进制缓存、日志优化、光影包、渲染距离、堆限制和并发条件必须记录。

## 输出与回收限制

- `latency-join-result.json`、`transfer-qa-result.json`、`native-join-*.json`、
  `timeline-*.json`、`run-summary.json`、本次画面截图。
- receipt 保存实际 Core/QA jar 哈希、源码哈希、模式和依赖差异；不将真实启动凭据写入共享日志。
- 正常关闭使用 QA 清理或仅本 lab PID 的窗口关闭；不要结束其他 owner 的 java.exe。
- parsed-pack/binary metadata 为零只证明自有状态释放；必须另看 GPU 对象及本 PID 的显存、
  JVM heap、private bytes/working set 趋势。WDDM 全机显存包含其他进程，不能直接归因本次修复。
- 在稳定菜单、进服稳定、退出后稳定菜单与真实 reload 前后比较；计时窗口内不强制 GC/glFinish。

## 协作交接

请公共工具 owner 复用上述源及判定口径，统一参数、fixture 和会话管理，不复制另一套公共基础设施。
原始证据留在任务目录，不把游戏资产、模组 jar、世界、账号或密钥提交到项目。
公共工具负责人接入后，应在统一运维入口登记这些测量字段和判定条件。

## 迁移后继续验证

当前源码根为 `C:\Users\ranzh\workspace\dev\muxigame`，Core 位于同名子目录。
旧 task 的原生结果和备份只读保留；不在旧源码位置继续写入或启动私有旧 runner。
当前统一工具需要角色专用模组输入和明确的光影包选择，才能忠实加载整包客户端与专用服务端。
由公共工具 owner 协调该接口；独立光影 QA mod 仅负责业务状态机和采样，进程仍交给统一工具。

新根基于 `1b6467b` 的隔离构建已通过 6995 项 Core Java 检查，32 个光影/日志类与迁移前候选字节相同。
这不替代尚未完成的三次进服、八条路线及真实重载/回收验收。


## Unified runner integration and native acceptance

The common runner provides per-role mod inputs, explicit shader selection,
and narrowly structured dependency overrides in dev commit `9b8d564`.
Its ownership/PID/normal-stop protocol remains the only launcher. Six existing
ownership checks and thirteen role-profile CLI checks pass; these preparation
checks are separate from native business acceptance.

The candidate built on `a51e523` passed 6995 Core checks. Its SHA256 is
`50f7c2642a6ddfb6a40cd154867900fea6dcf736cb757cb9f2b1a92bc7042507`;
the QA artifact SHA256 is
`6b8505c6a9b440068a5a3e47d55f872d493a04abf862cd2b67b3c1c08158de02`.
The dedicated logging subscriber also passed actual-dependency compilation
and real Log4j Lease lifecycle checks with a deliberately incompatible
LogBegone fixture for the pin fallback case.

Private run `shader-native-20261003-085755-ed4314` completed all 11 cases,
three logout/server-absence acknowledgements, real Iris/resource reloads,
and explicit false/invalid/default-restoration logging checks. Business
`passed=true`; the common runner separately reports `normalExit=true` and
server/host exit codes both zero. Actual rendering used NVIDIA RTX 4090.
Default dedicated logging rejection was observed before PlayerLoggedInEvent;
this event boundary does not prove coverage of all earlier network work.

| Native case | Handle-login/request to qualified frame (seconds) | New pipelines |
| --- | ---: | ---: |
| First client connection | 147.734 | 1 |
| Reconnect 1 | 9.298 | 1 |
| Reconnect 2 | 6.445 | 1 |
| Same dimension Y224 | 0.069 | 0 |
| Same dimension Y241 | 0.075 | 0 |
| Same dimension X16392.5 | 0.743 | 0 |
| Same dimension X8.5 | 0.220 | 0 |
| Adventure | 2.863 | 1 |
| Vanilla overworld | 0.670 | 1 |
| Core overworld | 1.591 | 1 |
| Vanilla overworld | 0.618 | 1 |

Game/mod startup is excluded. ConnectScreen-to-visible times for the three
connections are separately 152.784/10.542/7.510 seconds. Each case also
requires actual client movement and independent server displacement proof;
this is native KeyMapping input, not physical keyboard or screenshot proof.

Shader behavior acceptance does **not** establish that first-login latency is
fixed. The first login's retained Trace union covers only 5.389 seconds;
login-period resource reload logs span about 143.530 seconds. A compiled
landing section appeared at +34.170 seconds, well before the qualified frame.
The probes did not record screen/overlay transitions, so neither all remaining
time nor that entire reload is attributable to shaders. The first connection
also observed no logging lease before connecting and an active lease after;
early client installation requires separate confirmation. The 129-sample OS
sidecar began during this login and cannot describe its complete CPU/GPU window.

Comparison conditions: one real loopback client and dedicated server; an
already initialized private account in vanilla overworld; client render 12,
simulation 10, FPS 60, graphics 1, Better MC - Low; dedicated view/simulation 3.
The profile supplies 341 shared and 83 client-only artifacts plus the common
agent. Both required inherited loader overrides are explicit:
`citresewn=-connector` and `sable=-scalablelux`. Historical incompatible private
QA/game jars are excluded in the profile receipt. The generated existing-player
world avoids new-account provisioning and does not cover the separately
confirmed synchronous initial-survival landing blocker. These conditions and
shared driver/JIT warmth preclude a controlled speedup claim against older
integrated-server timings.

Receipts and raw logs remain ignored under Core `build/shader-qa` and the common
runner's marked instance directory. `attempt4-timing-review.json` identifies
its source hash, interval unions, dropped events and exact sample boundaries.
Earlier attempts with missing dependency overrides, or a manually corrected
wrong-dimension fixture, remain failures/exclusions and are not acceptance.
