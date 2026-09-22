# 功能边界

`MuxiGameCore` 只管理配置和功能注册/关闭，不承载具体业务。
`ServerFeature` 定义模块 ID、事件注册与资源释放；每项新功能有独立包和配置段。
`IdentityFeature` 是当前唯一模块，处理 UID 检查、后台资料查询和主线程昵称应用。

身份网络请求在后台执行，昵称变化在服务器 tick 中应用；HTTP 不阻塞游戏主线程。
FakePlayer/NPC 在入口和定期刷新中均跳过。停服时释放模块网络资源。
关闭身份功能时不会创建 HTTP 客户端，也不要求安装 Simple Nicknames；核心可离线加载。

模块配置：`config/muxi-game-core.json`，格式为 `schema: 1` 和 `features.<功能名>`。
身份专用字段继续是 `endpoint/serverKey/refreshSeconds`，只是放在 `features.identity` 中。
本次不改变统一账户的接口、密钥、UID 或昵称存储方式。

新增功能先实现 `ServerFeature`，再在入口显式注册；不要将活动、任务、服务器规则逻辑塞进昵称类。
目前并没有这些后续功能的实现。

模组本身仅在专用服务端加载。与客户端的昵称显示同步仍由 Simple Nicknames 负责。
