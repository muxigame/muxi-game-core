# Core 用户 YSM 资产桥：隔离协议候选

本候选只新增真实 muxi-game-core 的 feature/ysmassets、tests/ysm-assets 和本文；没有改 MuxiGameCore、CoreConfig、登录注册类、HomeSpawningFeature、mixin 配置、传送网络或 Terminal。YSM 格式、目录和资源减负仍由原 YSM owner 负责；官网 store/game wire 仍由官网 owner 负责。没有部署、凭据、生产目录写入、native 导入或 reload，也没有启动 MC。

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

这些类未注册，Default RuntimePort 不可激活。Core 的 shared registration/config 文件保持其他 owner 原状。后续须串行把 registry bind/retire 和 coordinator reconcile/disconnect 接入真实 Auth 授权/撤销、登录退出与 server stop；socialUid 本身不暴露授权代次，不能仅靠 UID/UUID 猜测同 listener 的重新授权。停止顺序应先 coordinator.close 完成回退，再 registry.close 清绑定。这个接线点需要与官网/登录/框架 owner 协调，不能从 HTTP 声明自行生成可信 bindingGeneration。

官网 server/app/ysm_game_api.py 在由其 owner 增补机器授权和 resolve_connection 接口，默认缺 hook 时503，用户 Bearer/cookie 不回退成 machine authority。Core 当前没有 HTTP 发送器、机器凭据或主动拉取任务；独立模型文件继续留在隔离根。不能借用音乐/社交 key。

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
2. 私有外观语义待确认：若旁观者收到完整模型资产以渲染，就不能承诺这些字节完全不离开所有者。严格私有字节门禁可拒绝未授权 remote-render 资产许可，但当前没有实现旁观默认外观适配器，也没有擅改产品外观；桥保持关闭。不能把 UI 隐藏当安全证明。
3. 串行连接真实 issuer bind/revoke、机器 authority/connection resolver 及 Core lifecycle；配置和共同注册必须保留家园/传送 owner dirty。没有选择新的凭据来源。
4. 获得隔离两真实 UID MC 窗口，证明网页及原 YSM native 直请求的访问/列举/选择拒绝、旧缓存/旧代次拒绝、穿戴中撤权回退和 tracking peer 画面、重复上传/删除引用、断线重连、重载失败/进行中与代际切换。未获得这些回执前，不写 live auth/custom 目录、不激活桥、不安排凌晨生产 reload。

热加载与凌晨策略沿用官网隔离合同，stage 与运行时 ACK 分离；到凌晨也不能越过上述阻塞。协议 fixture 并非生产、真实 UID 身份或两客户端网络/画面验收。
