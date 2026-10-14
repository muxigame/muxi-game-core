# Core 用户 YSM 资产桥：隔离协议候选

本候选位于真实 muxi-game-core 的 feature/ysmassets、tests/ysm-assets、tools/ysm-machine 和本文，并已接入原 LoginGate、TerminalPassportFeature、TerminalPassportServer 的认证事件路径。MuxiGameCore、CoreConfig、HomeSpawningFeature、共享 mixin、传送网络和 Terminal 未由本任务修改。YSM 格式、目录和资源减负仍由原 YSM owner 负责；官网 store/game wire 仍由官网 owner 负责。未部署、未变更生产凭据或模型目录、未 native 导入或 reload，也未启动 MC。

## 当前交接状态

身份接线提交：fe06d1b85e9f8b393d60c81f3168b6d82ebbae1d（19 文件，已推送 dev）；官网 wire 基线：742cedafa011cc11cfa830ae9cbd1e2093405a22。实际注册沿 MuxiGameCore 原有的 LoginGate / TerminalPassportFeature 路径执行，不需要另行注册身份原型；模型 RuntimePort 保持默认关闭。

| 部分 | 已完成及证据 | 尚未完成 |
| --- | --- | --- |
| 官网 wire | 实际 Router/SQLite 接入 Core-owned HMAC plugin 与 signed Core resolver，33项联合检查通过 | 官网生产 bootstrap、批准 provider/replay/HTTPS host 尚未安装；缺 hook/provider 返回503 |
| Auth 事件 | 原 join grant 成功及 terminal Auth callback 后绑定；原 SSO 60项、LoginGate localhost HTTP/lifecycle 121项通过 | issuer 响应与 MC listener 使用隔离 fixture，未验收真实账号会话 |
| 连接生命周期 | exact player/listener/UID；每次授权换 generation；重连、换号、晚到旧回调、tick/close 失效路径已接入 | 真实 MC 双 UID 原生验收待协调；未给 respawn 信任迁移增加任何捷径 |
| 撤权 | 原 explicit revoke/status expiry/logout/disconnect 会失效旧 claim；独立 JOIN 保留时重新发 generation；403伪造 UID 不撤销有效连接、409拒绝旧 claim | native 默认外观、旧 cache 与 tracking peers 的真正 ACK 尚未具备；pending fallback 不能成为成功回执 |
| 格式验证 | 检查 SHA、size、opaque ID 与可信 validation receipt 元数据；109项 store/protocol 检查通过 | 无完整2.6.5模型格式 validator；fixture opaque bytes/receipt 不能代表有效 YSM 模型，27 selector profile checks 也不能替代 validator |

本轮补强：Java复核 provider 返回的 key ID；Python Core resolver 流式限制4096字节，超限提前停止并关闭，错误/重定向响应不读取正文；非ASCII签名路径返回403。新增机器边界检查 Java15项、Python12项通过，并重新通过上述33项联合 wire。证据：build/ysm-assets-131/machine-boundaries-result.json、joint-wire-result.json。未改变 production admission、模型 canSelect 或 active。

用户已于2026-10-14选择私有外观A：仅owner列出和选用，旁观者可接收显示所需资源，不承诺不可提取。A/B不再待决策；完整格式validator、正常目录/选用/恢复授权、tracking同步、批准bootstrap及真实原生验收仍待完成。客户端文件保密不是激活条件；详见ysm-private-appearance-a-20261014.md。未与家园267合包、部署或改父仓指针。

## 当前事实

- 官网已提交合同见 ../../better-mc-remake/docs/ysm-user-assets.md 与 ysm-user-assets-bridge.md；本候选直接测试其实际 YsmAssetStore 导出。
- TrustedAccounts.socialUid 只接受当前 player 实例、可接收消息的 listener、同一已认证 connection；TerminalPassportServer 在 Auth 验证及 server-thread live 校验后 admitTerminal。这里复用实际代码，不由请求 body.uid、昵称或离线 UUID 授权。
- 生产2.6.5的 exact JAR 审计 SHA256 为 b285c73d4ec010d9a9be3c53c1bee890cf269645be5f1bcf1c27a2e8e82807cb；auth 是选用授权，不是已证明的私有字节分发。事实入口是 ../../muxi-ysm-compat/docs/ysm-production-upload-contract-20261013.md。没有用3.0迁移 API 冒充2.6.5。

## 独立实现

YsmAssetSnapshot 严格校验 trusted snapshot：精确 owner/reference 映射、公开聚合、去重内容、opaque m_/p_ ID、SHA/size/storageKey/validator 元数据、期望选择及 revision。拒绝伪造/过期聚合、跨 UID 期望选择、重复引用、路径越界、未验证字段和 active/selectEnabled=true 清单。它验证合同元数据，绝不声称解析或验证了实际 YSM 格式。

m_ 逻辑引用只允许所有者；p_ 公开去重条目才允许其他已认证 UID。某内容还有其他人的公开引用，不会把所有者的私有 m_ 元数据公开给别人。列表投影不带 SHA、storageKey 或其他所有者 UID。same SHA 的多人引用不交叉授予逻辑引用权限；删除一个引用不删除另一个用户的有效引用。

YsmAssetCoordinator 先原子安装新 ACL/epoch 再处理撤权。下载许可只在精确运行时代际确认后签发，并在 open/send 及每个 chunk/native delivery 前重查当前 UID、listener、player、revision、generation、epoch、内容。旧 cache、许可或幂等请求不充当授权。真实运行时尚无适配器，默认 RuntimePort 所有能力为 false，选择和字节许可拒绝；stage 回执始终 active=false，既不 ACK 导入，也不调用网站 active=True。

选择只处理当前真实 actor，并可应用 trusted snapshot 中这个 UID 的期望选择。穿戴失败、请求期间代次撤销或 UID 过期会进入默认回退。回退完整回执要求默认穿戴读回、本人选择缓存失效及 tracking peers 同步 ACK；任一未确认会保留 pending，暂停新代次放行，ACL 不回滚。断开的旧 player/listener 与同 listener 的新授权绑定受到区分，旧回调不能重置替换玩家的穿戴。close 先封权限和许可，再处理穿戴回退；未确认回退会明确报错。

YsmConnectionRegistry 对齐官网在制 game wire 的 serverId/connectionId/bindingGeneration/uid。bindVerified 必须来自真实 Auth admission callback；每次 bind 即使同 UID/listener 也轮换声明，旧 claim 和返回的连接句柄失效。client entry 从 actual packet sender 的 claimFor/connectionFor 派生，不能接收客户端 UID 作为所有权。retire 只撤精确代次，迟到旧 logout 不会撤新代次；close/prune 清失效绑定。随机 connectionId/generation 只是关联元数据，不是新凭据。

## 文件边界及尚未接线

初版 c07917ef 的数据协议保持未激活；后续身份接线已由现有 LoginGate / TerminalPassportFeature / TerminalPassportServer 真实注册调用。Default RuntimePort 仍不可激活。Core 的 shared registration/config 文件保持其他 owner 原状。现有 Auth 原源码现已把 registry bind/retire 和 coordinator reconcile/disconnect 接到成功核验、明确撤销、heartbeat 过期、登录退出、tick 与 feature close；socialUid 本身不暴露授权代次，不能仅靠 UID/UUID 猜测同 listener 的重新授权。停止顺序应先 coordinator.close 完成回退，再 registry.close 清绑定。这个接线点需要与官网/登录/框架 owner 协调，不能从 HTTP 声明自行生成可信 bindingGeneration。

官网 server/app/ysm_game_api.py 在由其 owner 增补机器授权和 resolve_connection 接口，默认缺 hook 时503，用户 Bearer/cookie 不回退成 machine authority。新增机器身份 HTTPS handler 和启动期 hook 组件见 ysm-machine-auth-contract-20261013.md；它们不会自己开端口、读取/生成机器凭据或启动主动拉取任务。独立模型文件继续留在隔离根。不能借用音乐/社交 key。

## 隔离验证

build/ysm-assets-131/protocol-result.json：109项通过。使用官网实际 YsmAssetStore、实际 Core 新源码、实际 TrustedAccounts，MC listener 和 native wear/cache/peer ACK 明确为 fixture。两个模拟账号为10001/10002。仅自造 opaque bytes 与明确 fixture validator receipt；没有真实 YSM 格式验收。

覆盖：私有不可列举/metadata/download/use、公开条目、同内容重复上传及两用户引用、删一留一/删完/恢复默认私有、公开撤私有穿戴回退、缓存/peer ACK 不全重试、旧许可/幂等 key、未授权 UID/伪造 wire UID、旧 listener、同 listener 重授权换代、迟到 logout/回退、Auth 到期、server-thread 拒绝、穿戴过程撤权竞态、期望选择只作用于当前 UID。官网实际 store 的所有资产 canSelect=false，没有 active=True 签收。

build/ysm-assets-131/native-api-compile-result.json：新增代码对实际 MC1.21.1/NeoForge21.1.250 库编译通过，不使用 fixture 类型。这只证明 API 编译；没有构建混入其他 owner dirty 的整包 Core JAR。

运行：

```powershell
python tests/ysm-assets/run_protocol.py --java-home <已有JDK21> --gson <已有Gson2.10.1.jar>
```

测试隔离数据只在本项目忽略的 build/ysm-assets-131；不复用 Web 生产卷或 YSM 扫描目录。

## 激活阻塞与真实验收

1. YSM owner 提供 exact2.6.5格式验证器及限制、真实 catalog ID 映射、禁止授权包处理、每次 native 下载/列举/选用拦截和原生回退读回。当前只有 use auth 的字节码事实，不能承诺私有分发。
2. 私有外观已确认A：owner保留合法穿戴，非owner可看见但不能选用。Core现有RenderPermit与选用Permit分离，并核当前确认穿戴、owner权限、原连接代次及实际tracking；已收到的显示资源不构成选用权。无需客户端文件不可提取证明，也不召回已合法收到的副本。原生正常目录、选用绕过拦截和状态同步仍需实测；未知输入隔离保持。
3. 原 Auth 事件注册、机器 authority/connection resolver 和 Core identity lifecycle 已在 fe06d1b 接入；后续仍需原 owner 协调批准 credential provider、原子 replay store、Core HTTPS host 与官网 bootstrap 的实际安装，并在真实隔离账号上验收。默认缺 provider 为503，不能借用其他用途密钥。共享配置、家园及传送 owner dirty 保持原状。
4. 获得隔离两真实 UID MC 窗口，证明网页及原 YSM native 直请求的访问/列举/选择拒绝、旧缓存/旧代次拒绝、穿戴中撤权回退和 tracking peer 画面、重复上传/删除引用、断线重连、重载失败/进行中与代际切换。未获得这些回执前，不写 live auth/custom 目录、不激活桥、不安排凌晨生产 reload。

热加载与凌晨策略沿用官网隔离合同，stage 与运行时 ACK 分离；到凌晨也不能越过上述阻塞。原 Auth 接线另通过60项 SSO、121项进服 HTTP/lifecycle 与33项实际 MAC/Web/Core 联合检查，两个 UID 10000/10001 仍是隔离 issuer/MC listener fixture。协议 fixture 并非生产、真实 UID 身份或两客户端网络/画面验收。

新增关闭态接线见 [intake／默认回退 Core 合同](ysm-intake-default-fallback-core-20261013.md)：实际官网pending store→原YSM intake→签名wire 42项、原reset事务→Core pending fallback 20项通过。原33项wire及真实MC/NeoForge API编译通过；没有完整格式validator、native激活或cache/peer ACK，生产bootstrap仍未安装。

用户A决策已落实：[私有外观A与原生验收合同](ysm-private-appearance-a-20261014.md)。新增60项A合同通过；物理渲染资源可保留，选用资格仍由服务端独立判断。实际native切换/删除矩阵未执行，生产激活未执行。
