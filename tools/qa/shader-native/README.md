# Native shader business QA

Status: the unified native run `shader-native-20261003-085755-ed4314` completed 11 cases, 3 logout checks, actual Iris/resource reloads and logging rollback with business `passed=true`. The common runner recorded `normalExit=true`, server and host exit codes both 0. This validates the tested profile and pipeline lifecycle, not completion of first-login latency optimization: initial handleLogin-to-visible remained 147.734 seconds. This is a private business QA mod, never a production mod or a launcher.

Use the workspace `better-mc-remake/scripts/local_mc_debug.py` with one real client and a dedicated server in `--mode hold`. That tool owns PID identity, first automatic TCP connection, timeout and normal stop. This mod never launches/stops JVMs or changes the common coordinator command files. Root/role/runId must match its owner marker, offlineLoopback must be true and there must be exactly server + one client. Client connections are restricted to 127.0.0.1 and the owned port; actual server player remote addresses must also be loopback TCP.

## Compile only

From this directory, supply installed Java 21 JDK, explicit read-only MC/NeoForge/MixinExtras dependencies, and the read-only shader profile mods. Repeat --dependencies for extra compile jars. The builder extracts Sodium embedded compile jars under its own ignored build/nested directory; none are redistributed in the QA jar.

```powershell
python -B build.py --java-home <Java21Jdk> --dependencies <MinecraftNeoForgeCompileJar> --mods-dir <ReadOnlyShaderProfileMods>
```

Outputs: build/muxi-shader-native-qa.jar, build/compile.log, build/build-result.json with source/jar SHA256. No Java application/game is run by the builder. Do not commit build/, dependencies, worlds, profiles or logs. PROVENANCE.json records read-only historical source locations and hashes; old paths are evidence, not runtime defaults. Copied probes retain their original namespace and must not be installed alongside the old private QA jars.

## Required runner profile contract (common CLI now provides the interfaces)

- Both roles: matching tested Core/content dependencies and this QA jar; original item/dimension registry compatibility. The common tool already supplies local_mc_debug_qa.
- Client only: exact audited Iris/Sodium/Euphoria/Colorwheel and tested Veil/content profile, shaderpacks directory, iris.properties with shaderPack=Better MC - Low, enableShaders=true, maxShadowRenderDistance=16. Keep compiled profile class pins and needed assets/configuration matched. Do not inject shader, logging or Veil property overrides silently.
- Server only: content/dimension dependencies and prebuilt private world. No client rendering classes or client-only QA mixins are loaded on this side.
- World: first login in minecraft:overworld near (8.5,241,8.5), safe flat landing platforms already present at (8.5,224,8.5), (8.5,241,8.5), (16392.5,241,8.5) and (8.5,241,8.5) in muxi_game_core:adventure and muxi_game_core:overworld. A few blocks of movement must be possible. This mod does not create fixtures, force chunks, change game rules or override game protections.

The common tool now accepts --mod for both roles, --client-mod and --server-mod for role-specific jars, plus --shader-pack and explicit client render/simulation/FPS/graphics settings. Generic --data-dir remains symmetric and config import is restricted. For the reference shader profile use render 12, simulation 10, FPS 60 and graphics 1 explicitly. The native matrix identified above passed its lifecycle assertions with these interfaces; it does not establish acceptable first-login latency or compatibility with other profiles. Do not race file copies into a running instance or add a second launcher. Configure adequate boot/hold/stop budgets and retain the common normal-stop owner protocol.

## Automatic business sequence

ConnectScreen HEAD starts join-0 diagnostics while the common client still issues the native first TCP connect. After a qualified rendered frame (correct dimension, loaded chunk, compiled section, no screen/overlay), the server takes a baseline position. The client holds the native forward KeyMapping briefly and requires both client displacement and a later independent server position receipt. No physical keyboard/OS proof is claimed.

Each case observes 100 ticks after movement, checks actual successful IrisRenderingPipeline constructor count and current native ShaderPack dimension macro, and writes bounded trace snapshots. Expected count is one for each of three joins, zero for four same-dimension moves, and one for four cross-dimension moves. A mismatched count fails rather than silently relaxing expectations.

Two normal disconnects/reconnects follow join-0. Client logout clears swap/root/pending and binary metadata; server absence must be acknowledged before reconnect. The common auto-connect flag is one-shot and does not race these subsequent connects.

Eight routes exactly preserve the old fixture sequence: overworld Y224 -> Y241 -> X16392.5 -> X8.5 -> adventure -> overworld -> Core overworld -> overworld. All route Z=8.5. Server uses native teleportTo for same level and changeDimension(DimensionTransition) otherwise, on its own tick thread.

After routes, invoke real Iris.reload(), assert native pipeline validity and cleared swap diagnostic, then await real Minecraft.reloadResourcePacks(). Resource reload completion and restored shader validity are required; its pipeline count is recorded, not assumed. Final normal disconnect requires cleared metadata and server absence. Business QA leaves process stop to the common tool owner.

## Evidence and protocol

All private protocol/report files live in `<instance>/coordinator/shader-native/`, distinct from common `command-*` files. Monotonic command id, runId and routeId correlate server-command.json with immutable server-result-N.json. Server commands: arm, route, position, finish, absent. Receipts contain server-local callback/engine durations and actual player TCP proof; client waiting time stays in the client clock domain. Never subtract different-process nanoTime values.

client-result.json includes 11 cases, server receipts, client spans/mesh timelines, pipeline counts, macro checks, movement proof, logout snapshots, actual logging lease.valid/rejectsTrace before/after, and reload evidence. A failed assertion writes passed=false and stops business actions; the common runner must still stop its owned processes normally. No terminal credential/SSO tests are involved.

Acceptance requires BOTH client-result.json passed=true with 11 complete cases/3 logout acknowledgements and common run-result.json normalExit=true/all exitCodes=0. A common hold-only passed result is not shader QA acceptance. Reports without passed are incomplete.

Inclusive elapsed wall spans overlap and must not be added as exclusive work; uploads are CPU-side, compiled sections are not pixel/presentation proof, and pipelinePrepare invocations are not pipeline construction counts. Scalar capped records retain no world/event/packet objects. Client mesh job tokens reject stale-route events. Server and client traces use separate clocks and never share world instances.

## Review limitations

The native profile/connection/route/reload matrix identified above was completed through the unified runner by the designated MC owner. New changes still require a subsequent owned native run. Optional framebuffer screenshots are not implemented; this QA proves native state/render callbacks, not visual quality. Exact provider upgrades require hook/signature review before use.

## Additional acceptance and measurement boundaries

Each completed case requires default logging property absent, a valid lease, and actual `rejectsTrace=true`. Following the actual Iris and resource reload checks, the harness exercises explicit false, invalid value and property removal, checking lease release and default rejection restoration. JVM heap/nonheap, buffer pools and GC counters are sampled before connection/route, at the first qualified frame, case completion, menu logout and around reloads. These are point samples, not a complete GPU object ledger.

First visibility is independent of the server file ACK. TCP joins report both ConnectScreen-to-visible and handleLogin-to-visible; a missing comparable login boundary fails acceptance. The first server arm does not require a player UUID. The server traces arm on its tick, so a TCP login racing before that tick remains a possible coverage gap; the receipt records no claim that pre-arm work was captured.

File reads and replacement retry sharing errors five times with 5/10/15/20/25 ms backoff. The server records a command ID before execution and retains its result until publication, preventing repeat dimension changes during publication retries. Retry overhead can affect measurement. Fatal file errors fail the client run. Stage totals count all calls; detailed spans retain >=100 us or actual pipeline construction events with cap 16384 and explicit drops. Creative phase boundaries are elapsed wall intervals, not CPU or exclusive handler attribution.

Mixin configurations reserve only five exact `.mixin` packages; all helper classes and plugin are outside those packages. This removes the broad-package class-load hazard but does not prove runtime transformation compatibility. Native TCP/reload/movement and Mixin application passed for the identified run; compilation alone remains insufficient for subsequent changes.

## Dedicated-server logging observation

The first ServerTickEvent.Post callback captures `server-logging-ready.json`: server-local nanoTime/PID, player count, absent default property, and actual lease/valid/rejectsTrace state. Reflection failures are reported as probeError; this probe never calls update or installs the filter. Arm, route and finish receipts add the same read-only state. `server-login-timeline.json` records up to 16 PlayerLoggedInEvent boundaries and dropped count. Compare ready and login timestamps only within this server PID. A readiness-before-login flag means before that event boundary, not necessarily before all network login processing; absent events provide no before-login proof. Files retain observation timestamps even if publication retries delay delivery.

## Validated run and remaining latency boundary

For `shader-native-20261003-085755-ed4314`, handleLogin-to-qualified-visible was 147733.771 ms initially and 9298.4254 / 6445.2397 ms on the two reconnects. Actual pipeline construction counts were 1/1/1; the eight routes produced 0/0/0/0/1/1/1/1. All eleven cases passed dimension-macro, qualified-visible, client/server movement and final default logging rejection checks. All three logouts had server-absence receipts and empty swap/root/pending and binary lifecycle state. Iris reload built one pipeline; resource reload completed with valid shaders (pipeline count was observed, not required). Explicit false and invalid logging settings released the lease; removal restored valid trace rejection.

First-connect `loggingBefore` observed no lease, while every later before/after endpoint was valid and rejected TRACE. The dedicated server's first Post tick observed a valid rejecting lease with zero players before its first PlayerLoggedInEvent. These server facts do not prove client early-login filter coverage. No client installation timestamp exists in this run.

The first login's retained diagnostic spans cover 5388.723 ms by clipped interval union, leaving 142345.048 ms outside those spans. First landing-section compiled return occurred at +34170.188 ms, whereas qualified visibility required +147733.771 ms. Those boundaries differ in screen/overlay and frame conditions; the gap has no causal attribution here. Detailed route records dropped 30352 events, while retained Trace spans reported zero capacity drops (sub-100us filtering still applies). Do not add nested stage totals or infer shader, logging, GPU or idle cost from uncovered time. The ignored `build/shader-qa/attempt4-timing-review.json` records the scoped analysis and source hash.

Within the first-login window, existing logs show native `ReloadableResourceManager` start at 17:05:00.947 and FancyMenu's reload FINISHED marker at 17:07:24.477 (143.530 seconds), with qualified visibility around 17:07:26.451. KubeJS's earlier complete message is only its listener boundary. This supports a long resource-reload lifecycle overlapping login; it does not make that interval shader-only cost. Screen/overlay transition timing is not captured, so the additional UI wait remains unquantified.

## First-spawn variant (three-login native business acceptance passed)

The preserved attempt4 jar is `build/muxi-shader-native-qa-attempt4-6b8505c6a9b4.jar`. New observations and the first-spawn mode below are subsequent source changes and are not covered by attempt4's native PASS. They have separate three-login business acceptance in `first-spawn-20261003-094748-184b5f`, documented below.

Build with the same explicit read-only dependency inputs and add `--qa-mode first-spawn --expected-player FreshSpawn131`. The builder embeds `shader-native-profile.json`; its mode and expected name are covered by the resulting jar SHA and the common runner artifact receipt. Use the common `--client-name FreshSpawn131` and a private world without that account's `.dat` or `.dat_old`. No coordinator file is required, and no files are copied into running instances. Default `--qa-mode matrix` preserves the three-login/eight-route/movement matrix.

For controlled tests an optional preexisting `coordinator/shader-native/config.json` may override the embedded profile, with exactly `runId`, `mode`, `expectedPlayer` fields (expectedPlayer only required in first-spawn). Its runId must match the owned run exactly; unknown fields/modes or a player differing from owner.clientNames.host fail validation. This is not a generic configuration import. Both sides still require the private owner marker, matching role/runId/root, exactly one client and loopback connection.

First-spawn performs three TCP logins, each strictly one pipeline with Core overworld macros. It checks the actual login packet dimension immediately after PacketUtils main-thread handoff, the first qualified visible frame in `muxi_game_core:overworld`, and the server's same UUID, DONE=true and absent PENDING state. It observes the native initial landing within radius 10000 using the existing safeInitialSpawn predicate only after its entire nearby chunk footprint is already loaded. It reads no world blocks through a forcing getChunk call and writes no NBT. The two reconnect login-event positions must match the previous finish position (XZ <0.05, Y <1.01); an observed reconnect random-search object/attempt fails. Actual Iris/resource reloads, logging rollback and all three logout clears still run.

This mode deliberately sends no route or movement commands at random terrain: it does **not** provide native movement-input acceptance. That evidence remains in the separate matrix mode. Server request-map snapshots use the current candidate's private InitialSpawnPreparation MANAGERS/requests fields on the server thread. Changes are bounded at 32 plus drops; PlayerLoggedIn snapshots capture up to three events. Empty private request maps prove only this helper's observed cleanup, not removal of every native ticket. Snapshot sampling cannot prove absence of every transient operation or statistical randomness. Unknown private APIs become probeError and fail first-spawn acceptance.

Both modes now record at most 64 scalar events per active join (ConnectScreen start to first qualified visible; no startup/routes). Screen/overlay class changes are sampled at client ticks; logging state is sampled every 20 ticks plus a forced read-only snapshot at the earliest main-thread handleLogin boundary. No update/install call is used by these observers. A narrow ReloadableResourceManager.createReload wrapper calls the original once, returns the identical ReloadInstance, and observes completion of its existing future without replacement or cancellation. Tokens reject completion events belonging to an ended/previous join. Events are wall-clock boundaries, not exclusive costs; sparse UI sampling does not prove an exact transition time.

CPU checks: `python -B tests/run_cpu_checks.py --java-home <JDK21> --dependencies <ReadOnlyCompileJar>` validates caps, stale-token isolation, unchanged native result/future/exception identity and strict profile rejection in isolated Java helpers; it never invokes Minecraft main. Compile success and these checks do not validate new Mixin application or native first-spawn behavior.

## First-spawn attempt5 and diagnostic follow-up

Run `first-spawn-20261003-092455-03fb2e` with QA `e74a7179...` observed one fresh CoreOW login packet, one pipeline, a safe in-radius landing, DONE/cleared PENDING and subsequent empty private request map. The observed pending-to-ready interval was 6746.5128 ms with server ticks 3279 to 3413. It then **failed** `Incorrect dimension macro`; neither two reconnects nor the whole first-spawn profile passed. Both owned JVMs exited normally. Its original jar is preserved as `build/muxi-shader-native-qa-first-spawn-attempt5-e74a717992df.jar`; the ignored scoped review is `build/shader-qa/first-spawn-092455-evidence.json`.

The next diagnostic-only QA change records expected macro, actual dimension macro keys, FIRST_LOADED, native dimension, current level and enabled/fallback/pipeline before asserting. Failure also records the current RouteTimeline and DimensionSwap snapshots. The dimension-macro and one-pipeline assertions remain unchanged. These diagnostic additions were subsequently used in the successful three-login business run below; they do not change attempt5's failed result.

## First-spawn three-login acceptance: 094748

Run `first-spawn-20261003-094748-184b5f` (Core candidate c05, QA `9d022424...`) completed `passed=true`, phase 10, exactly three login cases and three logout checks. This is first-spawn acceptance only: the eight fixed routes and native movement-input checks were not performed. The earlier 085755 matrix remains separate evidence for those behaviors on its own candidate. The compact source-hashed receipt is `build/shader-qa/first-spawn-094748-acceptance.json`.

| Case | handleLogin to qualified visible | Pipeline constructions | FIRST_LOADED | injectCount |
|---|---:|---:|---|---:|
| Fresh first login | 96136.4059 ms | 1 | true | 3 |
| First reconnect | 10897.9980 ms | 1 | false | 5 |
| Second reconnect | 6699.1986 ms | 1 | false | 5 |

All three actual login packets and final levels were `muxi_game_core:overworld`; each captured source contained exactly the expected `CURRENT_EUPHORIA_PATCHES_DIMENSION_MUXI_GAME_CORE_OVERWORLD` macro with shaders enabled and fallback false. The first-entry hint was preserved only on the initial pack. The second reconnect reused the first reconnect's parsed pack/source identity while still constructing its own pipeline.

The fresh account landed at (1316.5, 70, 1321.5), with an already-loaded safety footprint and native safeInitialSpawn=true. Observed pending-to-ready preparation took 6316.0868 ms while server ticks advanced 4653 to 4779 (126 ticks); one search attempt was observed. DONE remained true and PENDING absent. Both reconnects retained the same UUID and exact observed position. Private requests were empty after all three logins and logouts; this does not certify the entire native ticket system.

Actual Iris reload constructed one pipeline and retained correct macros. Resource reload completed with valid shader state; its observed construction count was zero, which is allowed by the unchanged acceptance. Explicit false and invalid logging properties released the lease; removing the property restored valid TRACE rejection. Every logout cleared swap/root/pending and binary memory/metadata, and received server-absence plus zero-private-request receipts.

The business assertions passed, and the common runner independently recorded `normalExit=true`, server=0 and host=0 (runId `bdea1ca2ec2b45d58ffad4cc4f235e84`). No performance completion claim follows: first-login visible latency was still 96.136 seconds, and no startup timing is included here.

The first login also retained a native resource-reload future interval of 89762.7953 ms and a setLevel elapsed interval of 2495.5267 ms. These are existing, overlapping observation boundaries, not an additive cause breakdown; no new startup attribution is included.
