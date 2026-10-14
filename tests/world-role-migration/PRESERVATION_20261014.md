# Owner preservation and recovery, 2026-10-14

Deletion authorization is revoked. No deletion, recycling, production migration or deployment was performed. The only code source is the enclosing muxigame/muxi-game-core repository. Source commit 5863553c55d5b79ec52d2b033eb6f86de52210aa is confirmed on GitHub dev via the connected read-only GitHub API; this session's earlier SSH push failed host-key verification.

## Preserved code and experience

Runtime home-spawn guards, world-role layout/portal/respawn guards, offline migration tools and synthetic/native QA source are tracked. HANDOFF.md, ../home-spawning/HANDOFF.md, FULL_NATIVE_PREPARATION.md and RECONCILIATION_1.4.38.md retain the behavior, defects, limitations and recovery details. Ownership manifests now live alongside the tests; candidate builders no longer obtain their source lists from disposable build output.

The old home minecraft:overworld becomes muxi_game_core:homeworld; old survival muxi_game_core:overworld becomes minecraft:overworld; adventure is unchanged. Nether/End directories remain intact. Preserve seed 8324402536652151177 and shared default spawn (-496,74,304). Changing level.dat alone was insufficient: custom generator stems were overwritten on cold boot; the offline transaction therefore installs and enables a pinned preservation data pack. Empty MCA placeholders are valid. Player/NPC .dat_old and raw OPAC .nbt require the same dimension transform. Shared Sable external-slot ownership is ambiguous and must be refused. Unknown config/codec/schema and conflicting target cache directories must be refused rather than merged.

## Evidence and limits

Prior checks: migration synthetic 124; SavedData/timer 108; Xaero cache 94; integrated Sable/config 507 (64 forward and 44 rollback fault points); external config boundary 4; mixin targets 44. Standalone actual Sable codec checks: 24 before and 24 after. Home guard native checks: 267. Fresh world-role native 270 and migrated cold boot 275 used a restricted 16-mod profile and the older local framework; these do not prove the registered full combination.

preserved-evidence retains the 350 registered product input names, versions, hashes and resolved local source paths; registered ABI compilation of 44 owner Java sources passed with framework c8818fece84986ddeb3878befb7edeafb2e69b432970bbf2b0c4c911182eb3b6. Registered input verification and the ungranted queue gate passed. No full 350-mod native run occurred. No current live config equality or production readiness is claimed. These are historical summaries, not tests rerun during preservation.

Outstanding: granted full-native test slot; actual full-combination startup/physics/construct/entity/chunk tickets/restart; real portal/bed/anchor/first-login/network/client cache behavior; real offline plan with zero blockers; separate production maintenance/cutover authorization. The run-stage queue receipt must remain slotGranted=false until explicitly granted. Do not start Minecraft as part of recovery.

## Recover dependencies and build

Use JDK 21, Python 3, Minecraft 1.21.1 and NeoForge 21.1.250. The registered 1.4.38 artifact index is ../docs/release-1.4.38-artifacts.json relative to the Core parent; published manifest SHA256 is 157799764fbd5741684f879fb69d0150d73002d76eae89dd7de7ffccb0e4ce71. Obtain open-source dependencies from their declared project/distribution sources and check the exact profile hashes. For private project jars use the existing muxigame repositories and release archive. Do not substitute a same-named but differently hashed file.

Before invoking a candidate builder, resolve exact verified dependencies into local build inputs and regenerate guard-classpath.txt; it contains environment-specific absolute paths and is not a portable lockfile. Required original Core ABI hash: 46acc0ad4bd4b3cd0ff3ed812f649cfbbe6fc825820b3c9bca9267b8e0547d59; CustomNPC stock4 hash: fe9f1ff4bb4c072f5df88e381904a63f0637c874b29e9e8a4c35a958f104b1f5; Sable 2.0.5 hash: c8710f85bc780bbf523e6726ceaaf76dd0b8c61479db497b382c6bb34317e7ab. All 350 names and hashes are in preserved-evidence/registered-production-1.4.38-profile.json. Old local framework hash 75aead1e... is unsuitable for registered ABI validation. The existing world-role builder still chooses a local framework path; verify its hash against c8818fec... before rebuilding, and regenerate stale reports.

For full-native preparation, copy the preserved profile metadata to build/world-role-migration-20261014/full-native-preparation/registered-production-1.4.38-profile.json only after re-resolving its source paths and validating every input SHA256. Recompile QA from tracked source. Regenerate preparation manifests and leave the queue gate closed. A previous prepared manifest is invalid after caches/inputs are removed. See the preparation document for commands; this recovery step does not grant a runtime slot.

For offline migration, create and inspect a fresh plan on the user's intended stopped world, require zero blockers, and retain the transaction journal and its original files for exact rollback. Never apply an old fixture plan to a real world. Xaero cache migration requires explicit per-server roots. Existing production/user worlds, rollback journals and original files were not copied into this preservation set.

## Local restricted material and inputs to retain

Third-party javap audit outputs are preserved locally at C:/Users/ranzh/workspace/dev/muxigame/.local-operations/131-world-role-preservation-20261014/private-code. They are license-uncleared and must not be published. index.json records every original/retained path, size and SHA256. No third-party implementation text is included in the public evidence summaries.

No unique, irreplaceable owner source was found outside the committed project after this preservation. No Minecraft instance, world, cache or binary package was copied. If the verified registered release archive or legitimate production retrieval will not remain available, retain these exact ABI inputs until matching replacements are acquired: muxi-game-core/build/home-spawning-20261013/production-core.jar (475126 bytes, hash above) and production-customnpcs-stock4.jar (10648355 bytes, hash above), plus the resolved 350 input paths in the preserved profile. They were not duplicated. Retain the ignored local restricted preservation directory if the audit text itself is wanted. The project source, tests and handoff can recreate synthetic fixtures; old synthetic worlds and logs are not unique inputs.
