# YSM 机器用途鉴权及真实 Auth 接线 v1

本批仅修改 Core 自有代码和已清洁的 LoginGate、TerminalPassportFeature、TerminalPassportServer；没有改 MuxiGameCore/共同配置注册、HomeSpawningFeature、其 mixin 条目、传送 dirty、官网 owner 文件或 YSM 减负源代码。父会话授权继续接线；官网 wire 基线742cedafa011cc11cfa830ae9cbd1e2093405a22。

## 实际注册及身份生命周期

原 LoginGate 成功消费同一 raw connection 的 join grant、TrustedAccounts.admit 后调用 YsmAuthBindings.joinVerified；入口另要求 TrustedAccounts.uid，social-only 不能被误标成 JOIN。原 TerminalPassportServer 校验 Auth UID/requestId/gameSession 与当前 listener、admitTerminal 后调用 terminalVerified。公开 challenge 不建立 YSM 绑定。

原终端显式 revoke、status 到期及 session close 调 terminalRevoked，原 Session 保存不可变 original listener，旧回调不能退役替换连接。原 loggedOut/disconnect 和 LoginGate.loggedOut 退役全部该 listener 的声明；同 UID/listener 再授权也换 bindingGeneration。独立进服信任在终端 SSO 撤销后仍可成立，但 YSM 声明先退役再按当前 JOIN 信任重新派生；旧声明失效。已注册 Auth feature 提供 tick 清理和 source close；只有最后一个身份 feature 停止才关闭该服务器范围，避免单独关终端 SSO 擦除仍有效的 JOIN 范围。停止顺序先 coordinator 回退，再 registry 清绑定。YSM 回退未确认会记录失败，不打断 Auth HTTP 清理。

这些身份钩子是原 registered feature 的真实调用，不再依赖手工注册原型。模型 RuntimePort 保持关闭，没有格式签收、选用放行或导入动作。默认服务器关联名 unregistered-随机值不是机器凭据，也不自动进入官网允许名单；部署需要 approved startup attach 的稳定 serverId。

## 独立机器用途 MAC

实现：src/main/java/net/muxigame/core/feature/ysmassets/YsmMachineAuthority.java；网站兼容组件：tools/ysm-machine/web_hooks.py。本 Core 组件供官网 owner 启动期使用，不覆盖其 server/app/ysm_*。没有环境变量自动找 key、没有读取或借用音乐/social/终端 SSO key、没有生成生产 key。只有调用方显式提供已经批准的 Credential/provider 才可验证；缺 provider 或 replay store 为503。用途登记、密钥来源和容器插件打包仍需官网/父协调；不能自动增加现有凭据 scope。

头固定：X-Muxi-Ysm-Server、Key、Purpose、Audience、Time、Nonce、Signature。值为单值、规范 ASCII；Time 是毫秒 UTC、Nonce32位小写hex、Signature64位hex。重复头、玩家 Authorization/Cookie、正文篡改、错 serverId/keyId/用途/受众、过期与 nonce 重放均拒绝。密钥显示 redacted。

HMAC-SHA256 输入 UTF-8，各项之间一个 LF，末尾无 LF：

```text
muxi-ysm-machine-v1
<credential serverId>
<registered keyId>
<purpose>
<audience>
<timestamp milliseconds>
<nonce>
POST
<exact raw path without query>
<SHA256 of exact request bytes>
```

Web audience=ysm-web。snapshot/staged 仅 sync；catalog/blob 仅 player-read。当前 wire JSON不超过16KiB、未知字段拒绝，Crypto hook 有同一大小上限并复用 bounded body 给原 router。时钟窗口正负30秒；nonce必须原子消费，缓存身份包含audience/server/key/nonce。MemoryReplayStore 明确为有界、易失的隔离测试实现；生产须显式提供满足重启/并发需求的原子 replay store/policy，缺省不启用。源码和测试不把旧 Bearer/cookie 当作 machine authority。

## 当前 Core listener HTTPS 查询

YsmCoreConnectionHandler 不自己开监听。approved host 安装 POST /internal/ysm/connection/resolve；严格模式要求 HttpsExchange。仅隔离测试可显式用两端 loopback HTTP，仍经过真实 MAC/用途/受众/nonce校验。

查询请求正文仍为官网同一 claim四字段，受众 ysm-core:<approved core serverId>，调用方登记用途 connection-resolve。这个凭据用途独立于 Web sync/player-read；caller 不能拿玩家 Bearer 或一个只登记sync的 key做查询。handler 将实际查找 enqueue 到所属 MC server thread，核 YsmAuthBindings 当前 player/listener/UID/authorization generation，不从请求复制可信UID。有效输出 {serverId,connectionId,bindingGeneration,uid,live:true}；伪造UID403（不退役真实绑定），旧/断开/替换代次409，超时503。响应no-store，大小和正文受限。

CoreConnectionResolver 启动组件使用已批准 credential 与该 HTTPS endpoint，禁止TLS verify=false、userinfo/query/fragment、重定向及环境代理，IO前后用官网原 checked_connection hook复查。install_identity_hooks(app, authorizer, resolver) 将实际组件接入官网已有 app.state.ysm_machine_authorizer / ysm_connection_resolver；缺配置会清为None保持503。应由官网 owner 在 approved bootstrap 调用；本批没有编辑或部署其应用。

## 已实测证据

- 原 SSO source lifecycle：57项通过；成功callback/未claim challenge/过期/显式revoke/JOIN独立信任与换代直接断言 YsmAuthBindings。
- 原 LoginGate real localhost HTTP/lifecycle：121项通过；实际进服事件建立UID绑定，原退出事件退役，未改变拒绝和单次ticket行为。
- build/ysm-assets-131/joint-wire-result.json：33项通过。实际 FastAPI0.116.1 / Starlette0.47.3、官网742ced的 Router/SQLite、真实用途 MAC、签名 HTTP Core handler 和真实 Core/Auth 源码。两个隔离UID10000/10001；issuer响应和MC listeners 明确为fixture，不冒用任何真实账号会话。
- 联合覆盖 owner/other private catalog/blob、hash/ID拒绝、valid-MAC伪造UID、独立用途、cookie/Bearer拒绝、body tamper、expiry、replay、audience、Auth revoke、同listener reauth、独立JOIN保留、replacement listener、logout与无provider503。小JVM normal exit0，无MC/JCEF/键鼠动作。
- auth-native-api-result.json：原 Auth 钩子及新增代码对实际 MC1.21.1/NeoForge21.1.250/JDK API编译，无fixture类型。不构建/覆盖混有其他owner dirty的Core整包。

复现联合测试（依赖使用官网固定FastAPI版本，环境放Core/build内）：

```powershell
python tests/ysm-assets/run_joint_wire.py --java-home <existing JDK21> --gson <existing Gson2.10.1.jar>
```

## 仍然不可激活的条件

YSM负责人实际2.6.5交接 ../../muxi-ysm-compat/docs/ysm-production-adapter-handoff-20261013.md：27个selectors匹配只证明版本入口。没有完整单文件格式验证、没有逐content/generation的opaque native分发门禁，服务端reset也不代表own cache与tracking peer ACK。保持 exactGenerationReady=false，既不promote_verified真正上传，也不构造全部true的fallback回执；不以3.0迁移代替证明。

用户2026-10-14已选择A，旁观显示是render-only用途，不授予/catalog、/blob或select权限，不新增scope或用户token。Core新增许可合同见ysm-private-appearance-a-20261014.md；资源不可提取不作为验收条件。真实credential/replay/bootstrap仍未配置，未动生产模型目录或reload；原生双UID窗口仍由父会话协调。

另一个需官网 owner确认的同步边界：当前 /blob 是player-read且需live claim，离线上传者的私有文件不能凭/snapshot清单直接通过它复制到异机夜间镜像。若需求包含上传者离线时的全量同步，应另定义只给approved sync机器的exact content/revision字节传输，不能伪造离线player或放宽现player-read接口。本批没有悄悄添加该权限或接口。

## 机器协议边界补强

Core authority 现在还复核 credential provider 返回的 key ID 等于签名中的注册 key ID，拒绝 provider 别名映射误放行。CoreConnectionResolver 使用流式 HTTP 响应，最大接受4096字节；超过上限会提前终止并关闭。错误状态及重定向响应在读取正文前关闭。非ASCII raw path 作为403拒绝，不进入应用500。

tests/ysm-assets/run_machine_boundaries.py：Java15项、Python12项通过，覆盖注册 key/别名拒绝、拒绝不消耗有效 nonce、时钟边界、32并发重放仅成功一次、4096/4097响应边界、提早关闭、错误正文不读取、UID差异及缺 provider503。只使用固定 fixture 凭据和 MockTransport；没有 MC、生产凭据或 native 激活。修复后实际官网742cedafa联合 wire 33项重新通过；原57/121的 Auth 回归结论来自此前已完成证据，本轮未重复执行。
