# YSM intake／默认回退 Core 合同（关闭状态）

基线：Core 755689fbb3255bb2af20a116af47526a83057067；官网742cedafa011cc11cfa830ae9cbd1e2093405a22。原部件见 ../../muxi-ysm-compat/docs/ysm-production-intake-executable-20261013.md。本轮只在实际Core自有目录接线，不改官网/YSM owner源码、家园候选、共享配置或总仓指针。

## 已接部件

tools/ysm-machine/intake_bridge.py：可信本地worker直接使用官网pending_validation()，源SHA/size/storageKey只从已登记pending记录派生，不接客户端UID/文件路径。使用原intake模块，不复制来源。独占锁、持久任务、同revision幂等；复制前后校验revision与pending，删模竞态不发布绑定。worker与官网存储及现有YSM扫描树分开。quarantine沿原256MiB/100任务限制；观察输入副本另限256MiB/100根。

prepare_observation只准备全新副本和job marker，没有进程/native执行入口。assess_observation绑定当前token、固定JAR和SHA/size；成功/7类失败均保留quarantined_pending_full_validation。旧token、越权标志、未知状态、输入改变、旧revision拒绝。结果不是ValidationReceipt，不调用promote_verified/mark_synced/reload。官网pending/blocked及canDownload/canSelect=false不变，目录隐藏未知输入，/blob返回404，/staged拒绝pending。

YsmProduction265Fallback：批准启动代码可显式注入原ServerDefaultReset.bind(pinnedJar)的helper，再调用fromApprovedHelper；没有自动bind/native加载/注册。reset前后复核exact player/listener/UID和server线程，原helper仍负责固定JAR/默认资源/状态事务。仅OBSERVED且resetAttempted映射defaultConfirmed；cache与peer ACK固定false，complete始终false，Core pending和关闭失败回执保持。readiness/wear始终false。
## 离线证据

- intake-wire-result.json：42项，实际官网Store／原YSM intake／签名wire；观察文件明确为fixture，未执行native/MC。
- joint-wire-result.json：原33项签名/Auth合同通过，隔离JVM正常exit0。
- fallback-bridge-result.json：20项，原DefaultResetTransaction／ServerDefaultReset.Observation到Core，reset操作为fixture；服务器默认观察不清pending。
- 同runner真实MC1.21.1/NeoForge21.1.250 API编译通过，API编译无fixture类型。未构建含其他owner dirty的整包。
- 原owner19/16/12及此前Auth57/121、协议109没有重复执行，不能合称完整格式/native验收。

证据在Core/build/ysm-assets-131。运行：tests/ysm-assets/run_joint_wire.py --with-intake-contract，随后run_fallback_bridge.py；两者使用Core自有Python环境、现有JDK21及Gson参数。测试读取原YSM源码目录与记录hash，不复制来源。YSM提交、依赖冻结和父仓登记仍由原owner/总仓协调。

## 精确最小剩余操作

用户已选择A，旁观显示资源允许传输，不承诺客户端文件保密。仍有格式/native目录与选用授权/tracking画面ACK的工程依赖；未知输入native执行与生产激活保持关闭，当前没有实际发包。选用/目录授权失效不等同于抹除渲染文件，资源性能回收独立归原YSM owner。

| 操作 | 目标与边界 | 凭据及身份 |
| --- | --- | --- |
| 合法样本原生观察，需owner协调窗口 | 原owner提供有权使用的单个原始.ysm及SHA/size；固定2.6.5 JAR SHA b285c73d4ec010d9a9be3c53c1bee890cf269645be5f1bcf1c27a2e8e82807cb和批准native；全新root/world=ysm-intake-world，零玩家、仅观察插件。起点wall120s/CPU60s/job4GiB/process1/log4MiB待校准，不改生产；ZIP不得改名冒充。 | 不需账号/SSO/机器密钥。只生成回执job token，非平台/机器凭据。合法样本和窗口尚需原owner提供。 |
| 未知/恶意输入隔离，先安装验收 | 已验收VM/低权限环境，输入只读、有限临时写盘、拒绝出网、不可读生产凭据、外部超时/资源约束。Windows Job只有资源包络，network/filesystem/disk/GPU隔离仍false，本worker无执行入口。 | 可复用已验收隔离身份；不能用能读取生产凭据的宿主身份执行未知输入。未创建OS账号或新凭据。 |
| 生产bootstrap，当前不执行 | 总仓/官网/Core批准已有key的serverId/keyId、sync/player-read/connection-resolve、ysm-web/ysm-core:<serverId> audience、原子replay、HTTPS host和hook安装；缺provider/hook仍503。 | 不新建/借用密钥，只有已批准对应scope的机器身份可复用；SSO/social/music key不能自动扩scope。玩家身份复用原批准JOIN/SSO事件。 |
| 原生撤权验收 | 原owner验证content/native-ID/generation映射、owner正常菜单/选用权限、旧授权失效、默认穿戴和tracking画面更新，按用户已定A进行双UID验收；不要求召回渲染副本。 | 仅复用获准隔离测试账号，不借用其他会话，不新建凭据。 |

跨主机pending intake/离线上传者全量同步尚无批准字节传输合同。本地pending_validation不是远程接口，/blob仍只给已验证内容与live claim；没有新增raw intake endpoint或扩大sync权限。完整格式validator仍缺失，成功观察不会签发passed=true。未部署、启动MC或修改生产凭据/模型树。
