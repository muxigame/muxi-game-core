# Initial survival spawn preparation

A new account previously selected its random survival landing from
`PlayerList.placeNewPlayer`, synchronously calling `ServerChunkCache.getChunk`
while handling configuration completion. A full-pack first login reached this
wait on the server tick thread and produced a watchdog crash. Shader refresh
changes do not address that server path.

The replacement prepares the landing as a NeoForge configuration task before
JoinWorldTask. It leaves native player construction, PlayerList.load, player
load callbacks and the final initial-spawn flag decision in their original
order. It never sends an overworld login packet as a temporary staging step.
The prepared landing belongs to the actual Connection identity and UUID, not
a reusable UUID cache. Native persisted flags remain authoritative.

A bounded single-thread reader inspects only the incoming account's NBT as a
preparation hint. It does not invoke load callbacks, datafixes, backups or writes.
Uncertain predictions prepare conservatively. A current, clearly initialized
saved player can skip preparation; final native load can still invalidate that
prediction. If another mod changes such a saved player back to requiring an
initial spawn during load, this connection is rejected before its game login
packet. Repeated reconnect is not guaranteed to fix persistent callback rewrites.

Search preserves the uniform disk distribution within radius 10,000, up to
24 random centers, the existing nine offsets and original safe-surface rules.
Each center requests at most four FULL chunks covering center x/z plus or minus
six blocks: candidate offsets, collision lookups and biome interpolation can
cross different chunk boundaries. A narrow invoker calls the private native
future scheduler; the public main-thread future API also blocks and is not used.
Own request-key tickets keep the chunks present through final validation and
placement. Shared native generation futures are never cancelled.

Preparation allows at most 16 pending and two active searches per server,
one new chunk request and two candidate checks per tick, with an approximately
one-millisecond cooperative search budget and a 90-second total request deadline.
A vendor callback cannot be preempted; final placement revalidation and native
next-task work lie outside that search budget. Original player construction and
arbitrary mod callbacks may also load chunks. This change removes the identified
initial-survival synchronous wait, not every possible login-thread chunk access.
The configured watchdog remains unchanged.

Success, disconnection, timeout, failure and server stop release owned state and
tickets. A duplicate account is rejected earlier in preparation without kicking
an existing session. Missing or invalid preparation disconnects normally; there
is no synchronous fallback. DONE is written only after the prepared landing is
successfully applied.

The focused search/NBT tests and isolated full build (6995 Core checks) pass.
The final candidate c05a6bdb passed native run first-spawn-20261003-094748-184b5f:
a fresh account's first actual game login packet selected Core survival, its
original landing was safe with the required footprint already loaded, DONE was
set, PENDING cleared, and the connection request was removed. The observed
search interval was 6.316 seconds while the server advanced 126 ticks. Two
reconnects retained the UUID and saved position without a new search. Three
logout/server-absence acknowledgements, real shader/resource reloads and normal
server/client exit codes zero completed successfully. QA did not teleport,
modify player NBT or move the player across an unprepared random landing.

This validates one fresh account and two reconnects, not simultaneous new
accounts or every native timeout/disconnect path. It does not repeat the older
eight-route shader matrix. The prior first-spawn attempt remains a shader-macro
failure despite its successful server preparation. Native client exit
0xC0000409 from the original blocked horse run remains independently unconfirmed.
First handleLogin-to-qualified-visible remains 96.136 seconds in the final run;
this server fix does not resolve that remaining client wait.
