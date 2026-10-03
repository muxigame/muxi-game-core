# Shader QA host sidecar

Status: **36 mocked guard/CPU tests pass; native common-owned integration completed**. This is an optional read-only metrics consumer for the existing `local_mc_debug.py` owner, not a second launcher. It never starts/stops/attaches a JVM, invokes JFR/WMIC, edits a common owner marker, or enumerates other processes. Old task sources are provenance only, never runtime defaults.

## Use after the common owner starts the host

Requires the existing Python environment to provide `psutil`; nothing is downloaded or installed. Run from any working directory:

```powershell
python -B sample_owned_memory.py --instance-root <absolute-common-lab> --role host --verify-only
python -B sample_owned_memory.py --instance-root <absolute-common-lab> --role host --duration 900 --interval 2 --phase lifecycle
```

`--instance-root` is the shared lab containing `local-mc-owner.json`, **not** its `host/` subdirectory. `--role` currently accepts only `host`. Default project is the relocated workspace's `better-mc-remake`; an explicit `--project-root` must exist within that workspace and match the owner marker exactly. No instance is auto-discovered. Missing owner fields or a missing `processCreateTime` are errors, not an invitation to infer ownership.

`--verify-only` checks the owner and target process without collecting metrics or writing output. `--once` takes one sample after the same checks. Duration is bounded to 1..3600 seconds, interval to 1..60 seconds. If the common launcher runs this as a subprocess on Windows, use its existing hidden-process convention (`CREATE_NO_WINDOW`), retain the child handle, and let the sidecar end normally. This directory does not patch that launcher.

## Owner checks

The sidecar reads and invokes **only `owned_lab`** from the actual sibling `better-mc-remake/scripts/local_mc_runtime.py`; it does not call that module's process inventory. Loading it does not write a pycache beside the shared helper. In addition to its schema/allowed-root checks, the sidecar requires:

- Marker `projectRoot` matches the supplied/default project, `instanceRoot` matches the requested shared root, a valid `runId`, and `host` in declared roles.
- `processes.host` supplies an integer PID, finite positive `processCreateTime`, and `root` equal to `<common-lab>/host`.
- Java process name, exact recorded creation time, and cwd equal to that host root.
- The host's own `launch.args`, containing exactly matching `qa.local.root`, `qa.local.runId`, `qa.local.role=host`, absolute `--gameDir`, known client launch target (`forgeclient` or `neoforgeclient`) and BootstrapLauncher entrypoint.
- Either an exact sole absolute `@host/launch.args` process argument, or expanded arguments matching those identities plus the pinned version/loader/assets fields in that file. Extra `@` overrides, duplicate/bare QA properties and duplicate/equal-style identity arguments are rejected.

The host argfile SHA256, owner identity, role PID, creation time, cwd and command identity are checked on every sample. The full command line is read only for comparison; no token, UUID, username, environment or arbitrary exception message is printed/saved. A failed argument read gets one 50 ms retry that must pass the same checks. PID reuse or changing owner/argfile identity is a failure. On `AccessDenied`, only a subsequent `NoSuchProcess` plus `pid_exists=false` permits a `process-ended` record; live unreadable/reused PIDs remain failures.

## Evidence and interpretation

There is no external-output option. Exclusive-create JSONL output is restricted to:

```
<common-lab>/coordinator/shader-native/sidecar/host/os-<phase>-<timestamp>.jsonl
```

Redirected output paths are rejected. The sampler neither overwrites shader evidence nor writes shared coordinator commands. Each row includes `runId`, `role`, PID, epoch/monotonic timestamps and:

- Working set and process private bytes. Private bytes include the JVM heap and other private allocations; they are **not native-only bytes**. Working-set eviction is not proof an allocation was freed.
- This PID's cumulative user/system CPU seconds and system cumulative busy/idle seconds. Windows busy = user + system; interrupt/DPC values are already included, so they are not added twice. Process CPU delta / actual sample wall delta yields cores used; system busy delta / (busy + idle) delta yields whole-host utilization. The host logical CPU count is distinct from JVM `ActiveProcessorCount`.
- GPU Dedicated/Shared Usage separately by adapter LUID/physical index, and utilization separately by adapter and engine ID/type. The PDH wildcard is constrained to the authenticated PID. Engine/adapter percentages are never added together. Rate counters may be unavailable on their first sample.

Missing counters/failed CPU reads remain explicit missing/unavailable, never zero. PDH is WDDM process attribution, not an exact accounting of all shader allocations. Cache-zero, low GPU utilization, short menu windows and missing terminal GPU instances cannot prove full GPU leak freedom. The sidecar installs no GL object hooks and performs no GL synchronization.

Join JSON rows to the existing Java business checkpoints by `epochMs`, retain the time-alignment error and use sample-to-sample monotonic durations for CPU rates. Compare equivalent settled menus across completed reconnect cycles. Treat reload completion and later rendered-frame checkpoints separately; do not use game startup/openWorld time as login business latency. Do not infer competition causality solely from high host utilization.

## Verification only

```powershell
python -B test_guards.py
```

The tests use the **actual common `owned_lab` function** on temporary fixtures under this directory's `build/`, with every target process mocked. They cover absent/changed marker identity, missing create time, PID reuse, wrong role/root/cwd/executable, argfile mutation, expanded arguments and overrides, confirmed shutdown absence, and CPU missing-value/double-count behavior. They do not sample a real PID or start Minecraft. `build/test-receipt.json` records the sampler hash and results. Native integration on first-spawn-20261003-094748-184b5f recorded 255 samples for the authenticated host and ended normally (exit 0). The shared runner separately confirmed server/host exits 0/0. The retained JSONL is os-lifecycle-1791021187346961500.jsonl under that instance; missing counters remain explicit. This does not establish complete GPU object lifetime accounting.

Native common compatibility check: its host metadata uses `forgeclient`. Both known client target spellings are accepted in the owned argfile, but expanded process arguments must equal that argfile's actual target. Server/unknown targets and changing between aliases are rejected.
