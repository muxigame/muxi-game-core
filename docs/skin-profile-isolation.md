# Player skin profile isolation

CSL 14.24 mutates the GameProfile passed to SkinManager.getInsecureSkin by adding
CSL$IsSkull. The existing YSM 2.6.5 client passes live player GameProfiles to this
method in two rendering paths. CSL subsequently skips its normal asynchronous
player loader when that marker exists. This is a confirmed profile mutation and
loader-path defect; full visual reproduction of the reported flash still needs
the reserved JBC_FCRL client slot.

The client-only compatibility mixin wraps the complete getInsecureSkin body and
passes a fresh GameProfile/property map. UUID, UID login name, textures and their
signatures are preserved. MixinExtras 0.5.3 already bundled with NeoForge provides
WrapMethod, so no new library/mod is installed. The existing compatibility plugin
enables the mixin only when customskinloader is installed. Server identity,
nickname synchronization, save ownership and YSM files are unchanged.

## Lightweight verification

Run tests/run_skin_profile_smoke.py with --client-game, --csl and
--neoforge-universal pointing to the existing installation. It invokes the
actual CSL setSkullType method to reproduce the original pollution, then invokes
the production wrapper for 120 lookups. It checks independent property maps,
stable UID/UUID, retained textures/signatures, two distinct users, and an unset
skin. It starts no Minecraft client or server.

The standard build.py --test also compiles the full Core and runs existing tests.
The generated jar keeps the baseline version for review; it is not a release or
an installation instruction. Apply the source patch to the current integration
branch before a separately reviewed client build.

## Remote visual validation

Use the confirmed existing SSH target jbc-1@192.168.110.131 (JBC_FCRL). Its project
is C:\Users\ranzh\workspace\dev\muxigame. Sync reviewed source through the existing
Git repositories, not SCP. Wait for the terminal worker's GUI slot and the user's
interaction precheck; do not launch another heavy client on jby-008.

1. Use the existing isolated client/test setup on JBC_FCRL. Keep YSM 2.6.5 and all
   other mods. Record the Core/client mod hashes and fixture configuration.
2. Use two synthetic remote UID profiles with visibly different skins, plus one
   unset default profile. Serve fixture skin JSON/PNG locally in the isolated
   test environment; do not change real accounts or production API responses.
3. Observe initial appearance and again at 5, 30, 65 and 125 seconds (or more than
   two configured nickname refresh periods). Exercise YSM rendering, TAB/chat
   heads, and reconnect/respawn in the isolated setup. Record UID/UUID before and
   after and ensure both custom skins remain distinct.
4. Keep normal authenticated GameProfile textures intact. Check that independent
   insecure/head lookups do not leave CSL$IsSkull in the live player profile.
5. After visual validation, a normal client restart loads the approved client
   build. No game-server restart or change to UID/UUID/save data is needed.

The publicly authorized real samples 10000/10002/10003/10004 currently all return
the same 64x64 packaged Steve PNG with successful HTTP status/hash checks. This
does not prove whether a player uploaded Steve or has no platform record, and
does not establish the source of historical skin settings. Those samples cannot
demonstrate retention of a currently returned custom texture; use isolated
fixtures for the distinct-skin lifecycle check.
