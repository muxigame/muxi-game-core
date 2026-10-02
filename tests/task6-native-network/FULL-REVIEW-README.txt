状态：真实 131 功能验收仍未完成，不是可发布包。已验证 JBC-1@192.168.110.131 身份为 JBC_FCRL\JBC-1，并完成 task6 自有隔离 QA 文件准备；待 task14 协调一次交互桌面启动。SSH Session 0 不启动游戏。task6 自行承担真实功能、截图和退出验收。

传送网络完整独立候选 · revision 6

结果与边界
APP 打开已安装的 Xaero 原生全屏地图，沿用同一 WorldMapSession/MapProcessor/GuiMap。M 与用户自定义按键不变，普通地图浏览不受网络入口限制。关闭由 APP 打开的地图会在连接、实际 CEF 主 frame、页面 URL 和导航代次仍有效时恢复原 TerminalScreen/页面上下文；失效则退出，旧异步结果不会重开终端。
原生维度选择只改变视图。新增每秒最多一次的只读元数据请求，不移动玩家、不收费、不提交传送。只有玩家点击原生石碑传送菜单才发送 UUID 请求。无 v2 协议时地图仍可浏览，传送选项明确提示服务端未就绪。

服务端实际接线
WaystoneMapNetwork 的 v2 UUID 请求与旧 v1 坐标请求均解析真实 ServerPlayer 和真实 WaystonesAPI 石碑。来源优先为附近真实石碑；其次为服务器配置明确登记、实际有效、同维度关联真实石碑且玩家在入口范围内的设施门。空、孤立、破损、未登记或玩家自建门不会自动获得入口或跨维度权限。
跨维度另检查玩家实际当前维度的有效服务器门与石碑关联，不能用目标维度或别处的门代替。同维度仍执行原 Waystones 规则；激活与 Sharestone 例外按原 API，GLOBAL 不替代激活。
执行仅使用 createDefaultTeleportContext（真实 fromWaystone）和 tryTeleportAsync。保留原权限、激活、XP、冷却、维度与事件行为，不伪造 source、不使用 force/unchecked/坐标命令、不增加 OP 或账号凭证。关联来源仍必须通过 Waystones 原生交互范围检查。
原生 validatePendingTeleport 与 validateRequirements 返回钩子在异步准备后、原费用消费前重新检查连接/玩家、来源维度、来源 UUID 与位置、设施登记/门框/关联、目标 UUID/位置/实际石碑与激活。Entity Pre 事件另在主玩家最终移动前复核并返回原生失败结果。只检查本网络自己创建的 context，不改变原物品或物理门通行。最终消息仅在原 API 返回实际玩家且实际目标维度与落点相符时报告完成；pending 不等于成功，单连接/实际玩家只允许一个在途操作，最近请求支持有界去重。

设施登记和距离
仅读取服务器本地 config/muxi-game-core/teleport-network.json。缺失、损坏、超限默认空登记，不自动写文件，不假设设施已经部署，不建门、不生成退出门、不写正式存档。
teleport-network.example.json 是本交付目录中的空登记示例，未写到任何真实配置目录。version=1，关联与入口分别暂定 radius=4/maxVertical=3；半径可配 1..8，垂直可配 0..8，最多 128 条设施/64 KiB。整数、维度标识、位置边界、规范 UUID 与重复设施均校验。
实际设施条目格式：{"dimension":"minecraft:overworld","portalPos":[x,y,z],"waystoneUid":"规范石碑UUID"}。portalPos 是实际已激活 Core 门内部方块。服务器操作者显式登记定义设施授权；Core 没有建造者/管理员归属元数据，候选不猜测所有者、不从客户端或徽标自动晋升设施。
校验实际 Core WorldPortals.find 的完整门框、原物理门维度方向、真实 Waystones UUID、同维度和球形距离；只读检查不加载/生成区块。门或石碑所在相关区块未加载时保守视为未连接。目标区块仍由原 Waystones 准备过程负责，不提前加载或绕过。

原生石碑渲染
实际 WaypointRenderer 钩子仅替换 waystones 来源的标记，画石碑轮廓和完整真实名称；其他原生用户标记保持原行为。原生字号比例保留，长名称换行。每帧标签布局避让；极密位置保留石碑图标并在 hover/原生菜单选择展示完整名称。
真实同维度关联有效门的石碑附小门徽标，hover 和选择显示已连接。未登记的玩家门仅可产生徽标，绝不获得设施权限。没有独立门节点、重复门/石碑点或虚构门名，不以名称/单字身份提交传送。服务器只返回原已激活/Sharestone 可见节点的有界元数据；客户端短期缓存绑定连接、实际玩家、当前来源维度和查询 UUID，重连/重生/超期即失效。
纯策略测试与范围工具保留；实际权限路由在 NetworkTeleportGuard/NetworkPortalFacilities，不能用合成 Facts 或客户端自报位置作为授权。

验证证据
Core 与 Terminal 完整独立编译通过，编译目标 Java 21。168 项已执行轻量行为检查 + 23 项安装 SDK 字节码/签名检查，总计 191 项。包括真实候选服务端适配器/处理器与 guard 函数（受控世界/API double）47 项、客户端/实际绘制与菜单 hook（合成图形/Xaero）34 项、只读配置22项、独立策略/范围26项、真实原生入口/CEF frame 30项、APP脚本9项。不是实际 MC/MCEF 或 live Mixin 初始化验收。
准确匹配安装版本 Waystones 21.1.42/Balm 21.0.65/WorldMap 1.45.0；字节码确认原生最终 validateRequirements 在 consumeRequirements 和 performTeleport 之前，最终 Entity Pre 读取失败 override，XaeroLib Esc/back 使用传入 escape/parent。
INTERFACE-TESTS-current.json 的 54 项是真实验收准备清单，未执行，不计入 191 项。

整合方法
两个最小补丁基于本任务已接受稳定+好友候选的记录 index tree；MANIFEST 列出明确 baseHead/baseIndexTree、文件选择与 clean apply/blob 核对。先采用此前稳定整合，Terminal 再采用好友增量，最后合并本次 native-map-full 补丁。若父级当前树已有其他修复，只合并相关小块，不覆盖全源码或把旧版本整套 JAR 安装进去。
本完整补丁已经包含先前 native-map-entry 初步补丁的全部变化，应二选一采用，不能重复应用初步补丁后再整套应用完整补丁。
审阅 JAR 是该独立候选的私有编译产物，版本号沿用候选基线，非发布/部署产物。父级负责统一整合与最后版本选择。X 关闭按钮另有 task6-close-x-review.zip；本地图包没有 X 或相机修复。
tests/ 包含候选相关源码（仅测试用）与重放脚本。使用 JDK24 的 --release21、Node 和已有编译依赖；可设置 TASK6_COMPILER_DEPENDENCIES 指向含 Gson/DFU/Netty/Mixin 的现有编译依赖 JAR。默认选本环境 task21 编译依赖。SDK 检查仅读现安装 mods 路径。进入 tests 运行 test_native_entry.py/test_policy.py/test_network_runtime.py/test_network_client.py/test_facility_config.py/test_native_sdk.py，以及 node test_ui_entry.cjs。不会启动游戏或改真实配置。根目录 build_candidate.py 记录全源码离线编译方法，需父级准备完整隔离仓库与此前已接受依赖候选后使用。

剩余实际验收
由本开发任务负责在父级协调的 131 独立实例自行验证，task14 只负责合包、部署健康：实际 Mixin 初始化、GL/像素风图标与密集长标签、原生键盘/鼠标返回、真实注册设施与破损/卸载状态、原生 XP/激活/冷却/事件拒绝和实际落点。未占用 131/008，未接管浏览器或活跃游戏，未部署设施、安装 JAR、提交、推送、发布、重启或修改共享源码。旧稳定、好友和初步地图 ZIP 的 SHA256 均保持原值。

131 已准备的独立入口（不是验收结果）
C:/Users/ranzh/Documents/Codex/task6-native-map-qa-20261002/desktop/run_task6_native_map.ps1
参数 -Mode all；X/原生入口/CEF 边界优先可用 -Mode ui。1 个私有 MC，6 GiB、4 工作线程、45 FPS，随机本地端口，世界就绪后约 120 秒前台焦点，最多 900 秒。task14 只启动固定入口；不接替功能测试。
独立 QA 包 task6-native-map-131-qa-kit.zip SHA256 48a918af8f843ade9c1cf5f5f61c37560bba838661927f2497da15ac8053794a。QA-only 终端由地图候选仅替换独立编译的 X TerminalScreen.class；两份产品源码补丁保持独立，原始稳定/好友/X交付不变。
原始 PNG、真实原生包处理/完成事件、真实外部 JS 拒绝、退出收据必须待实际运行并由 task6 审阅后才能确认。
