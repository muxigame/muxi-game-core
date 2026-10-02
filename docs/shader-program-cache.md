# Optional shader program cache and Veil event dispatch

Both client options default to **off**. This source candidate does not enable
either option in a production launcher or change server teleport permissions.

| JVM option | Optional behavior |
| --- | --- |
| `-Dmuxi.programBinaryCache=true` | Restore validated native program binaries for pinned Iris Extended/Composite, Iris-owned Sodium and Colorwheel programs. |
| `-Dmuxi.veilShaderEventDispatch=true` | Skip empty native priority listener arrays for Veil shader-processor registration, retaining fresh events and processor lists. |
| `-Dmuxi.programBinaryCache.diagnostics=true` | Retain bounded content-key diagnostics for local QA; unnecessary for normal use. |

Enable only after validating the matching runtime and the intended shader pack,
driver, resource refresh and login flow. The options are independent. Current
native Veil OFF/ON measurements kept binary caching **on in both phases**;
Veil-only native operation and a fresh-process first login with Veil enabled
remain additional validation configurations. Driver-warm native linking can
already be fast, so binary caching has no guaranteed whole-transfer benefit.

## Compatibility and native fallback

Adapters require exact public class hashes for the tested provider ABI. The
verified profile uses Minecraft 1.21.1, Iris 1.8.14-beta.1, Sodium 0.8.13,
Colorwheel 1.2.9, Veil 4.3.2, FML loader 4.0.44 and event bus 8.0.5.
Missing/changed providers bypass their optional adapter. Unsupported transformed
program calls (separable programs, transform feedback, indexed fragment binding)
also retain native linking. Version changes require a new audit; never update
the pins merely to bypass a mismatch.

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

## Validation and measured limits

The private offline profile passed 24 native transfers with resource/manual
reload, checksum corruption, driver rejection, native shader rendering and
server-observed movement. The final cleanup build passed six native logins and
logout/normal-exit checks: binary entries/bytes, actual weak scopes and binding
metadata were zero. CPU integrity/fallback checks: 34; native event-bus ordering,
inheritance and dynamic-listener checks: 9.

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

Run CPU checks with Java 21:

```text
python tests/run_shader_program_cache_tests.py --javac <javac> --java <java>
```

To also run the actual installed native-bus checks, supply the public compiler
dependencies and client-library metadata (no account files are read):

```text
python tests/run_shader_program_cache_tests.py --javac <javac> --java <java> --dependencies <dependencies.jar> --client-libraries <libraries-directory> --client-version-json <public-version-json>
```
