# 131 跨维度光影优化：第一阶段

仅 dev；未部署、未推送、未改生产或全局显卡缓存。旧地图任务仍暂停。

MC 1.21.1 / NeoForge 21.1.250；正式 1.4.27 的 8998 个发布文件逐项校验。
Iris 1.8.14-beta.1、Euphoria Patcher 1.10.0-r5.9、Better MC - Low 光影开启。
131 Session 2；独立隐藏 GLFW 客户端、离线集成服、真实 changeDimension 和移动包；
1280×720、FPS 60、render 12 / simulation 10、10 GiB heap、4 个有效 JVM CPU。
落点 chunk 和固定石砖平台预先生成；不跳过接收屏幕、区块同步或编译完成判定。

## 行为与边界

原版 Iris 在世界引擎更新时销毁 GPU 管线并重建；Euphoria 随后再次 Iris.reload。
适配在三个 Core 世界之间用正确目标宏创建或复用解析后的 ShaderPack（最多 3 个），
仍正常销毁并重建 Iris GPU 管线，仅在成功完成当前 setLevel 且确认是 Euphoria 维度回调时消费额外 reload。
失败保留原版重载；手动 reload 清理缓存；世界退出在 updateLevelInEngines(null) 返回时清理。
更高版本带原生 DimensionShaderRefresh 时自动退出适配；可用 muxi.disableDimensionShaderSwap 关闭。
下界、末地及其他世界保留原有完整重载。无 GPU 编译缓存或磁盘 program binary 缓存。

## 严格对照

两组正式对照使用相同框架 SHA256 41a8d4ab1199ccf929501c5fbfac76555f3dc041d47eab85565836f8104aa239、
相同 Terminal、探针、发布包、平台和客户端设置。每组首轮 1 次、热轮 7 次。

| 实际可见且允许输入 | 原版 Core | 第一阶段 |
| --- | ---: | ---: |
| 进程内首个目标维度（n=1） | 40.875 s | 29.444 s |
| 热轮中位数（n=7） | 40.460 s | 22.981 s |
| 热轮 P95（nearest-rank，n=7） | 42.182 s | 38.297 s |

热中位数减少 43.202%。原版每次两次 Iris pipeline 构建、一次完整 reload；候选每次一次构建。
reload 含嵌套 pipeline，不能相加。第 7 样本后的显式 reload 已用实际 UTC 时间窗排除。
高并发机器负载不相同，候选早期 CPU 占用更高；P95 为小样本最大值，不能夸大可靠性。
首轮仅 n=1，且没有清理驱动/共享缓存，不代表首次安装冷启动。早期 105 s 调查不进入正式对照。

## 实机验证与已修问题

candidate-extended：12 次实际传送全部完成，正常退出；主世界/冒险/生存正确维度宏、
光影开启、真实 IrisRenderingPipeline、无 fallback、实际按键移动且服务端收到位移。
8 次计时后显式 Iris.reload 19.702 s，缓存代际清理；随后第三 Core 世界和回程正常。

nether-end-validation：6 次维度往返光影和移动通过；退出断言失败，残留 1 个解析缓存。
该失败完整保留，不能标为成功。原因是 Minecraft.disconnect 不调用 clearClientLevel。
随后修为共同的 updateLevelInEngines(null) 返回 hook；logout-fixed-validation 独立定向验证
2 次传送、正常退出、解析缓存 0 / 根路径 false / 待处理世界 false、退出码 0 全部通过。
该修复只涉及退出时机，未重新运行已过的 6 条维度路线。

截图实际 framebuffer 已目视检查：天空云层、地面材质、YSM 人物及阴影、Create 轴正常。
私有探针旧 272 项、噪声预算检查、旧分析器 4 项通过。最终共享源完整编译 148 个 Java。
core-final-receipt.json 记录完整构建哈希；主计时版本与最终版本差异仅诊断 helper 和退出 hook。

首次进服认证、远程资源同步未在离线集成服测量；下一阶段单独拆解，不能由传送推断。
最终 GLSL 相同的已编译阶段缓存仍为隔离实验，未包含在本次提交。

实现参考对照：Euphoria 官方 multiloader 分支公开的 lean refresh 生命周期；
https://github.com/EuphoriaPatches/EuphoriaPatcher/tree/multiloader
只核对行为和发布字节码，没有把 MPL 代码或第三方 jar 复制入生产源。
