# Optional shader program cache and Veil event dispatch

Veil's guarded empty-phase dispatch optimization defaults to **on** after
bootstrap and only for the pinned runtime below. Use
`-Dmuxi.veilShaderEventDispatch=false` to restore native dispatch. Program binary
caching remains **off** by default. This changes no server teleport permissions
and does not deploy a production launcher or client.

| JVM option | Optional behavior |
| --- | --- |
| `-Dmuxi.programBinaryCache=true` | Restore validated native program binaries for pinned Iris Extended/Composite, Iris-owned Sodium and Colorwheel programs. |
| `-Dmuxi.veilShaderEventDispatch=false` | Disable the default optimization that skips empty native priority listener arrays for Veil shader-processor registration, retaining fresh events and processor lists. |
| `-Dmuxi.programBinaryCache.diagnostics=true` | Retain bounded content-key diagnostics for local QA; unnecessary for normal use. |

Enable program binary caching only after validating the matching runtime and
shader pack, driver, resource refresh and login flow. The options are independent. Current
earlier native Veil OFF/ON measurements kept binary caching **on in both
phases**. The exact committed candidate now also passed binary OFF / Veil OFF
and binary OFF / Veil ON native transfers. The default-on validation below also covers a fresh-process first login
without an explicit Veil option. Driver-warm native linking can
already be fast, so binary caching has no guaranteed whole-transfer benefit.

## Guarded reconnect refresh

The reconnect extension checks ten public provider classes and reads the
vendor's actual `lastDimension` state. A matching current native pack may be
reused only when its root, options, zip mode and single target-dimension macro
match. Other supported target dimensions receive freshly validated defines.
Native GPU pipelines are still destroyed and rebuilt; only the matching vendor
dimension-reload callback is consumed after a successful refresh.

A true first join keeps the native path. Reconnect requires ten pinned provider
classes, the vendor's initialized `lastDimension`, and its actual macro counter
at least two. A pack containing `EUPHORIA_PATCHES_FIRST_LOADED` cannot be reused;
it is rebuilt with fresh validated target macros after initialization is complete.
The pinned constructor's second macro generation is included in the lifecycle
model. No vendor counter is reset or written. Unknown fields/providers,
incomplete initialization, invalid macros and failures retain native behavior.
A failed attempt cannot retry each frame, although one failed generation or
construction may still schedule bounded extra native macro/settings work before
fallback. Manual reloads remain native; logout clears parsed packs and level
references.

CPU coverage: 33 lifecycle assertions plus a missing-counter fallback check
against the actual implementation and controlled vendor doubles. These include
both native macro generations and do not replace native validation.

## Compatibility and native fallback

Adapters require exact public class hashes for the tested provider ABI. The
verified profile uses Minecraft 1.21.1, Iris 1.8.14-beta.1, Sodium 0.8.13,
Colorwheel 1.2.9, Veil 4.3.2, FML loader 4.0.44 and event bus 8.0.5.
Missing/changed providers bypass their optional adapter. Unsupported transformed
program calls (separable programs, transform feedback, indexed fragment binding)
also retain native linking. Version changes require a new audit; never update
the pins merely to bypass a mismatch.
Pins cover public original class bytes and unsupported calls inspected in the
adapted target nodes. They do not establish compatibility with arbitrary
third-party transformations outside those call sites; validate the full runtime.

Binary identity includes actual compiled shader sources and stages, attribute
and fragment-output bindings, dimension, selected pack content, options, actual
defines, driver identity and binary formats. Loading must report successful
native `GL_LINK_STATUS`; corrupt, rejected or unsupported records fall back to
the original link. Optional-cache exceptions disable caching. Native link
failures propagate and the original link runs exactly once on fallback.

Veil retains phase-major native mod order, inherited and dynamically registered
listeners, original nonempty container dispatch and exception handling. Unknown
containers/buses use native dispatch. Processor registries, listeners, resource
providers and GPU programs are not reused by this optimization.

## Invalidation and ownership

`.muxi-program-cache-v2` contains only owned, checksummed CPU byte records.
Limits are 8 MiB per record, 256 records and 64 MiB each for memory and disk.
Symlinked cache directories and leaf files are rejected. Writes use a new owned
temporary file and atomic replacement where supported. Native capture/load
buffers are freed in `finally` blocks.

Real shader reload and resource-refresh completion invalidate records. Managed
Core dimension reloads retain eligible binaries while rebuilding native GPU
objects. Logout clears binary RAM, actual weak scope metadata, bindings and
diagnostic records. `Minecraft.close` adds a cleanup backstop because native
shutdown can call `System.exit` without returning from `run`.

Shader-pack source metadata follows the native pack owner, allowing a direct
saved-home join after the Core capture slot was cleared. It does not retain a
global native pack or extend a GPU lifetime.

## Guarded first-login TRACE filtering

The existing `CreativeTraceGate` defaults on. Explicit
`-Dmuxi.creativeTraceGate=false` restores native filtering; invalid values also
disable it. Thirteen public provider/message class pins, current logger/filter
identity, unchanged Log Begone settings and every output threshold must match.
Only the two native creative-event TRACE formats that no output accepts are
rejected before costly filtering. Event listeners and creative items are still
built normally. Unknown configurations and any output accepting TRACE retain
native behavior. A live lease alone does not prove the per-event guard accepts
optimization, so QA records `rejectsTrace` separately.

Client ticks maintain the gate in client processes (including integrated
servers). A separate dedicated-server subscriber installs it at ServerStarted,
maintains it on server ticks and releases/reset state at ServerStopped. The
shared helper contains no client event types. This closes a dedicated-server
coverage gap; integrated-server timings do not establish dedicated-server
performance savings. Provider pins and per-event fallback remain unchanged.

## Earlier default-on and reconnect native validation (f49a2d7)

The private candidate passed three measured native logins and eight transfers
with no `muxi.veilShaderEventDispatch` property, binary caching off and the
independent logging optimization off. Veil dispatch was enabled without a
failure latch on every sample. Actual successful GPU pipeline constructions
were **1 / 2 / 1** for first join / first reconnect / second reconnect. The
last join reused the current parsed pack and consumed the identified redundant
vendor callback. The first reconnect retained the early-macro fallback.

Four same-dimension transfers built no new pipeline. Four cross-dimension
transfers covering the main world and both Core dimensions built one pipeline
each, with the expected target macro. Native Iris rendering remained enabled
without fallback, movement reached the server, logout cleared owned parsed
packs and pending state, and the client exited normally.

This profile uses the current 0.2.1 framework required by the integrated Core
SSO code. Two legacy private-test game addons restricted to framework 0.1.x
were excluded; shader providers were retained. Concurrent clients and this
dependency adjustment preclude a controlled whole-login timing comparison.
These are native offline client/server checks, not production authentication
or resource-sync validation, and do not establish that total first-login
latency is solved. Game startup and local-server boot are outside this metric.

## Validation and measured limits

The private offline profile passed 24 native transfers with resource/manual
reload, checksum corruption, driver rejection, native shader rendering and
server-observed movement. The final cleanup build passed six native logins and
logout/normal-exit checks: binary entries/bytes, actual weak scopes and binding
metadata were zero. CPU integrity/fallback checks: 34; native event-bus ordering,
inheritance, dynamic-listener and default/rollback guard checks: 16.

Veil OFF/ON cross-transfer warm medians were 6.20/2.90 seconds; rollback OFF was
6.64 seconds. Client login medians were 29.36/19.44 seconds, excluding local
server/datapack boot. Samples are small, phase order fixed and driver/JIT warmth
shared. These are not production authentication/resource-sync measurements.

The performance goal is **not solved**: an ON cross-transfer tail remains 6.22
seconds, JEI reports 8.7–9.7 seconds of startup on the render thread in ON login
windows, and one first far landing took 29.11 seconds with no shader rebuild.
Only the landing chunk was explicitly pregenerated; surrounding chunks were
uncontrolled. The original trace did not reopen its route window for same-
dimension teleport, so stale route labels do not establish landing network time.
Regex/material/stage experiments are excluded from this candidate.

On the exact candidate built from `99f4503`, binary caching stayed off for ten
successful native transfers, with Veil off for four and on for six. Warm cross-
transfer medians were 6.28 seconds off and 0.78 seconds on (two samples per
group); phase order was fixed. Native shaders remained enabled without fallback,
movement reached the server, and the client exited normally with RAM, weak
scope and binding metadata all zero. This separately validates Veil-only use.

New private probes measured 5.52 seconds in JEI item collection, including 139
creative-tab mod-event dispatches totaling 5.37 seconds. This is nested elapsed
time, not exclusive listener or log-filter CPU cost. No creative-event or JEI
optimization is included. A 16,384-block first landing took 0.51 seconds, with
the target chunk received at about 58 ms; the return took 57 ms. Shader rebuild
did not occur. The earlier 29-second far tail was not reproduced and remains
unexplained. The detailed span limit was reached, so interval coverage is
partial; cumulative stage counters and target packet markers continued.

Run CPU checks with Java 21:

```text
python tests/run_shader_program_cache_tests.py --javac <javac> --java <java>
```

To also run the actual installed native-bus checks, supply the public compiler
dependencies and client-library metadata (no account files are read):

```text
python tests/run_shader_program_cache_tests.py --javac <javac> --java <java> --dependencies <dependencies.jar> --client-libraries <libraries-directory> --client-version-json <public-version-json>
```

## Cold reconnect follow-up validation

The guarded Euphoria initial-load refresh avoids reusing a parsed pack that still
carries FIRST_LOADED state. On the current explicit loopback profile, the first
connection and both reconnects each constructed exactly one native Iris pipeline;
four same-dimension moves constructed none, and four cross-dimension moves each
constructed one. Actual target macros, native movement/server receipts, three
logout cleanups, Iris/resource reloads and logging false/invalid/default rollback
passed. Both JVMs exited normally. The candidate passed 6995 Core checks.

This establishes shader behavior and compatibility, not total first-login
performance: first handleLogin-to-visible was 147.734 seconds, versus
9.298/6.445 seconds on reconnect. Login-period resource reload overlapped most
of the first window; existing sparse shader spans cannot attribute that tail.
Game startup is excluded. The dedicated logging gate was active before the
recorded login event; early client installation was not timestamped. The fixture
uses an existing account and does not cover new-account random-spawn generation.
See the operational handoff for exact profile conditions, artifacts and timings.

## Operational sampling handoff

See [the shader QA operations handoff](shader-qa-operations.md) for the validated runner sources and contracts to integrate into the shared QA tooling.
