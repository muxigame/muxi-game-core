# Initial survival preparation

The native configuration task prepares a random survival landing before JoinWorldTask. It never creates or loads a ServerPlayer. A read-only NBT prediction only controls whether to prepare: the original PlayerList.load and its events remain authoritative, and InitialPlayerSpawnMixin consumes a connection-owned prepared landing before the game login packet.

Bounds: 16 pending connections, 2 active searches, 90 seconds total per request, 24 random centers within the original 10,000-block uniform disk. The original nine offset order and safeInitialSpawn rules remain. A center +/-6 footprint covers at most four FULL chunks, including hidden biome and collision lookups. All world calls happen on the server thread. Each preparation tick schedules at most one chunk, checks at most two candidate positions, and cooperatively yields after approximately 1 ms. An individual native/provider call cannot be preempted.

Each chunk has an independent request-key region ticket at distance 0 (FULL level 33). The private getChunkFutureMainThread invoker schedules work without the public API's managedBlock. Both a successful generation future and getChunkNow readiness are required. Ready results retain tickets until original placeNewPlayer returns, disconnect, expiry, or stop. Cleanup never cancels shared generation futures. Normal player construction and NBT load can still perform their original synchronous work; this change removes the proven random-survival generation wait, not all possible login blocking.

Prediction reads only the selected profile's file, with an 8 MiB compressed and 16 MiB NBT accounting cap on a bounded single IO thread. It never writes player NBT, performs datafixes, or fires load events. The singleplayer owner's in-memory LoadedPlayerTag takes the same precedence as native load. This Player tag was already datafixed during level loading and normally has no nested DataVersion, so only this owner-memory branch checks markers without a version requirement. Disk hints still require current-version NeoForgeData with clear existing-player markers to skip preparation. An existing unreadable/oversized primary always prepares; a confirmed missing primary can use .dat_old as a hint. Native load remains authoritative even if files change after prediction.

If a load callback changes a predicted existing player into a new player, no prepared landing exists: the connection is refused before the login packet, without synchronous fallback. Repeated retries are not guaranteed to resolve a callback that makes the same change every time. Duplicate UUID connections are refused earlier than native handleConfigurationFinished; this never disconnects an existing player. Separate UUID connections have separate requests/tickets. The random distribution is retained, not the old ServerPlayer random stream's exact sequence.

## Offline focused verification

Run from the Core root:

```powershell
python tests/initial-spawn/run_initial_spawn_tests.py --java-home <JDK21> --core-jar <previous-Core-jar>
```

The previous Core jar supplies only unchanged classes; the seven owned production sources and the legacy dimensions smoke source are compiled against the installed server libraries. The tests execute the actual production search state machine with controlled future/readiness behavior and the actual NBT prediction with private temporary files. They cover footprint boundaries, budgets, unresolved/failed futures, ticket hold/cleanup, independent searches, 24 x 9 limits, native marker precedence and conservative primary/backup handling. The legacy embedded-player smoke now explicitly tests existing-player travel only; it makes no first-login acceptance claim.

These tests do not establish native mixin transformation, custom configuration task execution/order, keepalive during a stalled provider, global manager deadlines, simultaneous TCP connections, or disconnect cleanup in the actual game. Real firstSpawn QA must cover a fresh UUID, first game packet in muxi_game_core:overworld (one pipeline), native DONE marker, two same-UUID reconnects without rerandomization, and pending-request/ticket cleanup. Follow-up failure cases must exercise two accounts, disconnect during preparation, timeout/generation failure, and clean stop. Do not disable the watchdog or mutate player NBT to manufacture acceptance.
