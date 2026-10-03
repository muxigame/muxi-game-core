"""Ordinary-default shader repair validation: client login/reconnect + eight routes."""
from pathlib import Path
source_path=Path(__file__).with_name('prepare_latency_round.py')
source=source_path.read_text(encoding='utf-8')
changes=[
 ("shutil.copy2(release['artifact'], lab / 'mods/dev-core-baseline.jar')", "shutil.copy2(release['artifact'], lab / 'mods/dev-core-baseline.jar')\nframework=next((Path(release['buildSource']).parent/'muxi-minigames/build/libs').glob('*.jar'))\nfor previous in (lab/'mods').glob('muxi-minigames-*.jar'): previous.unlink()\nshutil.copy2(framework,lab/'mods'/framework.name)"),
 ("'latest-latency-round-'", "'latest-shader-fix-'"),
 ("'logging-integration-handoff.json'", "'shader-fix-candidate.json'"),
 ("probe_roots = [root / 'route-timeline-src', root / 'binary-io-probe-src']", "probe_roots = [root / 'route-timeline-src', root / 'binary-io-probe-src', root / 'shader-fix-probe-src']"),
 ("'qa.transfer.samples=16'", "'qa.transfer.samples=8'"),
 ("'if(index>=2)'", "'if(index>=3)'"),
 ("'muxi.creativeTraceGate=' + flag", "'muxi.creativeTraceGate=false'"),
 ("'muxi.veilShaderEventDispatch=' + flag,", ""),
 ("'nativeTransferRoutes': 16", "'nativeTransferRoutes': 8"),
 ("'creativeTraceRequested': enabled", "'creativeTraceRequested': False"),
 ("'veilRequested': enabled", "'veilPropertyAbsent': True"),
 ("'current-committed-native-login-reconnect-distance-three-dimension-timeline'", "'shader-fix-default-login-reconnect-eight-routes'")
]
for old,new in changes:
 assert source.count(old)==(2 if old=="'if(index>=2)'" else 1),(old,source.count(old))
 source=source.replace(old,new)
# Use actual guarded state when the command-line property is deliberately absent.
source=source.replace('Boolean.getBoolean("muxi.veilShaderEventDispatch")', 'net.muxigame.core.compat.shaders.VeilShaderEventDispatch.enabled()')
old="(lab / 'qa-source/JoinNativeQA.java').write_text(join, encoding='utf-8')"
new='''join=join.replace('sample.add("shaderState",state);', 'sample.add("shaderState",state);sample.add("dimensionSwapAfter",reflection("net.muxigame.core.compat.shaders.DimensionShaderSwap","diagnosticSnapshot"));sample.addProperty("veilPropertyAbsent",System.getProperty("muxi.veilShaderEventDispatch")==null);')
join=join.replace('sample.add("parsedPackCacheAfterLogout",reflection("net.muxigame.core.compat.shaders.DimensionShaderSwap","diagnosticLifecycleSnapshot"));', 'JsonObject swapAfterLogout=reflection("net.muxigame.core.compat.shaders.DimensionShaderSwap","diagnosticLifecycleSnapshot").getAsJsonObject();sample.add("parsedPackCacheAfterLogout",swapAfterLogout);if(swapAfterLogout.get("parsedPacks").getAsInt()!=0||swapAfterLogout.get("capturedPackRoot").getAsBoolean()||swapAfterLogout.get("pendingLevel").getAsBoolean())throw new IllegalStateException("Dimension shader state retained after logout");')
(lab / 'qa-source/JoinNativeQA.java').write_text(join, encoding='utf-8')'''
assert source.count(old)==1
source=source.replace(old,new)
old="shutil.copy2(framework,lab/'mods'/framework.name)"
new=old+"\nexcluded=lab/'excluded-legacy-games';excluded.mkdir(exist_ok=True)\nfor pattern in ['muxi-outbreak-*.jar','muxi-zombie-challenge-*.jar']:\n for legacy in (lab/'mods').glob(pattern): shutil.move(str(legacy),str(excluded/legacy.name))"
assert source.count(old)==1
source=source.replace(old,new)
source=source.replace("'nativeJoins': 3, 'measuredNativeJoins': 2", "'nativeJoins': 4, 'measuredNativeJoins': 3")
exec(compile(source,str(source_path),'exec'),{'__file__':str(source_path),'__name__':'__main__'})
