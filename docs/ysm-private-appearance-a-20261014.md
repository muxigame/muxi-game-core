# 私有模型 A：所有者选用、旁观者渲染

2026-10-14 用户明确选择 A。私有模型仅所有者列出和选用，其他玩家可以看到合法穿戴并接收显示所需资源；不承诺客户端资源不可提取、复制或保密。该决定已落实到Core候选，并解除A/B待决策。官网对应提交为b56eee7b2b025ec4ca93a294826fb1a65d4c5aec，HTTP字段沿原742cedafa合同。

## Core接线

YsmAssetCoordinator.privateAppearancePolicy()固定OWNER_SELECTION_OBSERVER_RENDER。owner的list/metadata/downloadPermit/select继续核当前UID对逻辑m_引用的权限；观察者得到资源、看到穿戴或保留缓存，均不会获得这些权限。public p_仍由当前有效公开引用授权，独立同内容引用不被误删。

新增RenderPermit是服务器内部render_only类型，与选用Permit分离，不作为客户端token。它只从实际存活的observer/wearer连接、当前已确认成功的穿戴、有效owner权限、资源SHA和exact epoch/revision/generation产生，并保存原连接绑定及穿戴对象。发放前及每次chunk/native delivery重核原绑定、当前tracking关系和目标权限；同listener换Auth代次、换穿戴、撤权、删模、旧revision、停止跟踪或关闭都拒绝旧许可。tracking取自RuntimePort.isTrackingObserver，默认false，不采纳客户端render=true、UID或hash。发放过程中tracking回调导致revision变化也拒绝。

部分/失败wear尚未确认时不发渲染许可。某个旧公开wearer正在等待默认回退时，其他仍合法的owner穿戴可继续为tracking observer渲染；不能把无关pending误作owner私有资源禁令。默认RuntimePort仍关闭，因此未知/未验证输入不会被A决策激活。

ownCacheInvalidated指撤掉旧的选用/目录授权状态，不是安全抹除渲染文件；trackingPeersAcknowledged指确认画面/穿戴状态更新，不是证明观察者删光资源。客户端内存/GPU资源回收仍属原YSM性能任务，不是A的文件保密验收条件。

官网/catalog和/blob保持actor引用权限，观察者仍无法直接下载owner私有引用。Core/YSM显示路径从受信资源缓存或合法wearer取源，再用RenderPermit和实际native tracking投递；不得替换成observer的owner下载权限，也不因此新增机器scope或凭据。
## 离线结果

build/ysm-assets-131/appearance-a-result.json：60项，通过真实官网Store导出的快照，使用明确的MC/native/tracking fixture。覆盖非owner看穿戴但不能列表/metadata/下载/选用、伪造m_/p_/hash、跟踪结束、tracking回调中的revision竞态、同连接换Auth、撤权、public/private、删除/恢复、独立同内容引用和资源留存不产生选用权。相关旧协议109项、原回退20项通过。当前协调器另以实际MC1.21.1/NeoForge21.1.250类型编译，证据appearance-a-native-api-result.json；无fixture API类型。

这些结果不声称原生切换已实测。nativeTransitionMechanismExecuted=false，runtime/productionActivated=false；未启动MC、发布、恢复联合UI、创建凭据或修改官网/YSM/家园/shared配置。

## 按2.6.5实际机制验收

原机制来源：../../muxi-ysm-compat/docs/ysm-production-upload-contract-20261013.md、ysm-production-runtime-hooks-20261013.md及contracts/production265-hooks.json，固定JAR SHA b285c73d4ec010d9a9be3c53c1bee890cf269645be5f1bcf1c27a2e8e82807cb。

| 场景 | 需要实测的原生行为 |
| --- | --- |
| owner私有穿戴，非owner跟踪 | required-auth/catalog与player own_models使正常菜单只列owner可选项；原正常selection入口执行catalog/auth/texture/CanSwitchModel检查。显示资源和wearer状态可发给跟踪者；缓存存在也不准伪造选择。 |
| 非owner伪造普通/管理员/恢复选择 | normal_selection、raw setter、ignore_auth管理员命令、NBT restore等路径均回到当前Core UID/引用/generation校验，不能只依赖客户端菜单或单个Q入口。A不授予选择权。 |
| 公开改私有 | 更新required-auth及own_models，并正常同步目录/所有权；原auth remove/clear及manager当前选用校验对失去资格的非owner调用reset。owner自己的m_仍合法，不强制卸下；其旁观显示继续。不得清空其他有效同内容引用。 |
| 私有改公开 | 当前公开引用p_进入正常可选目录并同步授权；旧revision许可不可复用，后续正常选择走新权限。 |
| 删除/最后引用消失 | Core先撤资格，再经原manager catalog/ownership校验和ServerDefaultReset.resetAndReadback确认无权wearer回默认，并核实际tracking状态投递与观察者画面。原catalog为空会早退，必须覆盖最后引用删除，不能拿空目录当回退证明。 |
| 独立引用/恢复 | 删除m_a不清m_b；恢复默认为private A，重发目录/所有权，不自动恢复撤销的wear。 |
| 重连、换号、跟踪离开、并发撤权 | 原listener/Auth代次/selected状态分别失效；每chunk重核，旧回调不触新actor；实际同步后伪造选择仍拒绝。 |

原T reset只是修改服务器模型/纹理并dirty；T.M(ServerPlayer)是状态/资源触发的一部分，不是“所有客户端立即确认”。实际dirty/interval/tracking投递与客户端画面要单独记录。原manager reload是全局import/native sync，成功观察不是完整格式validator。

A允许正常渲染资源同步，不再要求opaque JNI提供“资源永不离开owner”的保密白名单，也不要求召回已合法收到的副本。仍须证明验证后的content/native-ID/generation映射、owner选用权限、正常目录过滤、raw/restore绕过拦截、失权wearer默认状态和tracking画面同步。未知输入隔离与完整格式校验保持。原生矩阵仍由原owner在协调的隔离双UID窗口验收，本轮没有权限/工具阻塞，也没有代做原生发布。

删除逻辑m_引用时，即使同内容的其他公开/自有引用使native ID仍存在，也须按Core当前选用的逻辑引用判失效；不能仅凭native catalog里仍有该ID保留旧穿戴。恢复同样不隐式恢复旧逻辑选用。

RuntimePort.isCurrentWear必须只读原生当前模型/纹理与本generation映射，不能把“曾经wear成功”当作reload后的当前穿戴。原生外部reset或模型改变时，渲染发放及旧许可分块均拒绝；默认回调false。本轮新增该失败路径后，A合同为60项。

原生公开转私有还须按YSM新合同移走public/custom扫描副本，只留唯一auth版本，成功reload后核实际ID与required-auth/own_models；reload失败不写成功ACK。原native同步可能预发完整元数据/资源，应实测范围，不承诺只发最少字节。Core的display-target许可与正常native资源预取分开，A不要求逐UID资源保密白名单；正常可选目录和每条服务器选用路径仍须独立证明。对应YSM合同：../../muxi-ysm-compat/docs/ysm-private-a-policy-20261014.md（原owner10项，不重复执行）。
