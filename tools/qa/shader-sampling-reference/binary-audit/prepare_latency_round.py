from pathlib import Path
import hashlib, importlib.util, json, os, re, shutil, subprocess, sys, zipfile

root = Path(__file__).resolve().parent
here, out = root.parent, root.parent / 'transfer-lab'
enabled = '--off' not in sys.argv
mode = 'on' if enabled else 'off'
pointer = out / ('latest-latency-round-' + mode + '.json')
old = Path(json.loads((out / 'latest-veil-stability.json').read_text(encoding='utf-8'))['lab'])
release = json.loads((root / 'logging-integration-handoff.json').read_text(encoding='utf-8'))
if '--resume' in sys.argv:
    lab = Path(json.loads(pointer.read_text(encoding='utf-8'))['lab'])
else:
    subprocess.run([r'C:\Python38\python.exe', str(here / 'run_transfer_native.py'), '--shaders', 'on', '--fix',
                    '--program-control', '--prepare-only', '--samples', '16', '--world-template',
                    str(old / 'saves/transfer-private')], cwd=here, check=True)
    lab = Path(json.loads((out / 'latest-on-fix-programcontrol.json').read_text(encoding='utf-8'))['lab'])
    pointer.write_text(json.dumps({'lab': str(lab)}), encoding='utf-8')
assert out.resolve() in lab.resolve().parents and not (lab / 'pid.json').exists()
shutil.copy2(release['artifact'], lab / 'mods/dev-core-baseline.jar')
for name in ['program-lab.jar', 'stage-lab.jar', 'regex-lab.jar', 'material-lab.jar']:
    p = lab / 'mods' / name
    if p.exists(): p.unlink()
for source in (old / 'qa-source').glob('*.java'):
    shutil.copy2(source, lab / 'qa-source' / source.name)

route = 'net.muxigame.binaryqa.route.RouteTimeline'
logging_diagnostic = '''
 private JsonObject loggingGate()throws Exception{
  Class<?> type=Class.forName("net.muxigame.core.compat.logging.CreativeTraceGate");
  var current=type.getDeclaredField("current");current.setAccessible(true);Object lease=current.get(null);
  var reason=type.getDeclaredField("unavailableReason");reason.setAccessible(true);
  JsonObject result=new JsonObject();result.addProperty("requested",Boolean.getBoolean("muxi.creativeTraceGate"));
  result.addProperty("reason",String.valueOf(reason.get(null)));boolean valid=false;
  if(lease!=null){var method=lease.getClass().getDeclaredMethod("valid");method.setAccessible(true);valid=(Boolean)method.invoke(lease);}
  result.addProperty("activeValidLease",valid);return result;
 }
'''
join = (lab / 'qa-source/JoinNativeQA.java').read_text(encoding='utf-8')
join = join.replace('public final class JoinNativeQA {', 'public final class JoinNativeQA {' + logging_diagnostic)
join = join.replace('veil-join-result.json', 'latency-join-result.json')
join = join.replace('if(index>=6)', 'if(index>=2)')
join = join.replace('System.setProperty("muxi.veilShaderEventDispatch","true");', '')
join = join.replace('System.setProperty("muxi.veilShaderEventDispatch",index<2||index>=4?"true":"false");', '')
join = join.replace('index<2||index>=4?"on":"off"', 'Boolean.getBoolean("muxi.veilShaderEventDispatch")?"on":"off"')
join = join.replace('begin=System.nanoTime();ready=moved=0;',
                    'begin=System.nanoTime();' + route + '.begin("join-"+index,"minecraft:overworld",Double.NaN,Double.NaN,Double.NaN);ready=moved=0;')
join = join.replace('ready=System.nanoTime();sample.addProperty',
                    'ready=System.nanoTime();' + route + '.mark("qualifiedVisible");sample.addProperty')
join = join.replace('sample.addProperty("serverObservedMovement",true);',
                    'sample.addProperty("serverObservedMovement",true);' + route + '.mark("serverObservedMovement");' + route + '.end("join-complete");' + route + '.dump("timeline-join-"+index+".json");')
join = join.replace('sample.add("programBinariesBefore",binaries());',
                    'sample.addProperty("binaryRequested",Boolean.getBoolean("muxi.programBinaryCache"));sample.addProperty("loggingRequested",Boolean.getBoolean("muxi.creativeTraceGate"));sample.add("loggingGateBefore",loggingGate());sample.add("programBinariesBefore",binaries());')
join = join.replace('sample.add("programBinariesAfter",binaries());', 'sample.add("loggingGateAfter",loggingGate());sample.add("programBinariesAfter",binaries());')
join = join.replace('}catch(Exception ignored){}mc.stop();',
                    '}catch(Exception ignored){}try{' + route + '.end("failure");' + route + '.dump("timeline-failed-join.json");}catch(Exception ignored){}if(mc.level!=null){mc.level.disconnect();mc.disconnect(new TitleScreen());}mc.stop();')
assert 'if(index>=2)' in join and '.begin("join-"' in join
(lab / 'qa-source/JoinNativeQA.java').write_text(join, encoding='utf-8')

transfer = (lab / 'qa-source/TransferNativeQA.java').read_text(encoding='utf-8')
transfer = transfer.replace('public final class TransferNativeQA {', 'public final class TransferNativeQA {' + logging_diagnostic)
start = transfer.index('        System.setProperty("muxi.veilShaderEventDispatch",index<16')
end = transfer.index('        target=selected.location().toString();', start)
transfer = transfer[:start] + '''        net.muxigame.binaryqa.NativeProbe.reset();net.muxigame.binaryqa.Trace.clearSpans();
        int step=index%8;
        ResourceKey<Level> selected=switch(step){case 4->adventure;case 6->survival;default->Level.OVERWORLD;};
        targetX=step==2?16392.5:8.5;targetY=step==0?224:241;
        String group=step<2?"same-dimension-short-vertical":step<4?"same-dimension-far-16384-blocks":"three-Core-dimensions";
''' + transfer[end:]
transfer = transfer.replace('requestNs=System.nanoTime();sample.addProperty("requestNs",requestNs);',
                            'requestNs=System.nanoTime();' + route + '.begin("transfer-"+index,target,targetX,targetY,8.5);sample.addProperty("requestNs",requestNs);')
transfer = transfer.replace('net.muxigame.binaryqa.NativeProbe.mark("visible");',
                            'net.muxigame.binaryqa.NativeProbe.mark("visible");' + route + '.mark("qualifiedVisible");')
transfer = transfer.replace('serverQueueMs=elapsed(System.nanoTime());',
                            'serverQueueMs=elapsed(System.nanoTime());' + route + '.mark("serverTaskBegin");')
transfer = transfer.replace('sample.addProperty("serverObservedMovement",true);',
                            'sample.addProperty("serverObservedMovement",true);' + route + '.mark("serverObservedMovement");')
transfer = transfer.replace('samples.add(sample);sample=null;write();index++;',
                            route + '.end("transfer-complete");' + route + '.dump("timeline-transfer-"+index+".json");samples.add(sample);sample=null;write();index++;')
transfer = transfer.replace('private void start(Minecraft mc) {', 'private void start(Minecraft mc)throws Exception {')
transfer = transfer.replace('sample.addProperty("shaderExpected",Boolean.getBoolean("qa.transfer.shaders"));',
                            'sample.addProperty("binaryRequested",Boolean.getBoolean("muxi.programBinaryCache"));sample.addProperty("loggingRequested",Boolean.getBoolean("muxi.creativeTraceGate"));sample.add("loggingGateBefore",loggingGate());sample.addProperty("shaderExpected",Boolean.getBoolean("qa.transfer.shaders"));')
transfer = transfer.replace('sample.add("programBinariesAfter",programs("snapshot"));', 'sample.add("loggingGateAfter",loggingGate());sample.add("programBinariesAfter",programs("snapshot"));')
transfer = transfer.replace('}catch(Exception ignored){}mc.stop();',
                            '}catch(Exception ignored){}try{' + route + '.end("failure");' + route + '.dump("timeline-failed-transfer.json");}catch(Exception ignored){}if(mc.level!=null){mc.level.disconnect();mc.disconnect(new TitleScreen());}mc.stop();')
assert '.begin("transfer-"' in transfer and 'step=index%8' in transfer and 'new JoinNativeQA()' not in transfer
(lab / 'qa-source/TransferNativeQA.java').write_text(transfer, encoding='utf-8')

# Prior origin stack walking is unnecessary for this packet/mesh diagnosis.
(lab / 'qa-source/CreativeOrigins.java').write_text('''package net.muxigame.binaryqa;
public final class CreativeOrigins {
 public static void begin(net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> key,net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters params){}
 public static void end(){} public static void dispatch(long begin,long end){} public static void dump(){}
}
''', encoding='utf-8')
trace = (lab / 'qa-source/Trace.java').read_text(encoding='utf-8')
trace = trace.replace('private static long loginNs,respawnNs;', 'private static long loginNs,respawnNs,droppedSpans;')
trace = trace.replace('if(spans.size()<8192&&(end-begin>=100_000L||name.equals("creativeTabModDispatch")))spans.add',
                      'if(spans.size()>=16384)droppedSpans++;else if(end-begin>=100_000L||name.equals("creativeTabModDispatch"))spans.add')
trace = trace.replace('clearSpans(){spans.clear();}', 'clearSpans(){spans.clear();droppedSpans=0;}')
trace = trace.replace('"nowNs",System.nanoTime());', '"nowNs",System.nanoTime(),"droppedSpans",droppedSpans);')
(lab / 'qa-source/Trace.java').write_text(trace, encoding='utf-8')

probe_roots = [root / 'route-timeline-src', root / 'binary-io-probe-src']
if '--prepare-files-only' in sys.argv:
    print(json.dumps({'lab': str(lab), 'waitingForPrivateProbes': True})); sys.exit(0)
assert all(p.exists() for p in probe_roots), 'Private probes must be ready before compilation'
core = Path(release['repository'])
jdk = Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1')
deps = Path(r'C:\Users\ranzh\Documents\Codex\2026-10-02\task-10\album-build\dependencies.jar')
args = (lab / 'launch.args').read_text(encoding='utf-8')
libraries = [Path(x) for x in re.findall(r'[A-Z]:[^;"\r\n]*\.jar', args)
             if Path(x).exists() and Path(x).name.startswith(('lwjgl-', 'log4j-', 'loader-', 'bus-'))]
cp = os.pathsep.join(map(str, [deps, Path(release['artifact']), out / 'modules/dev-probe-baseline.jar',
                              *libraries, *list((out / 'release-pack/mods').glob('*.jar')), *list((out / 'modules/nested').glob('*.jar'))]))
spec = importlib.util.spec_from_file_location('latency_builder', core / 'build.py')
builder = importlib.util.module_from_spec(spec); spec.loader.exec_module(builder)
sources = list((lab / 'qa-source').glob('*.java')) + [p for p in (root / 'qa-src').rglob('*.java') if p.name not in ['Trace.java', 'TraceMinecraft.java']]
sources += [p for folder in probe_roots for p in folder.rglob('*.java') if 'build' not in p.parts]
builder.compile_java(jdk / 'bin/javac.exe', sources, lab / 'qa-classes', cp, lab / 'latency-qa.args')
with zipfile.ZipFile(old / 'mods/muxi-transfer-qa-only.jar') as z:
    resources = {n: z.read(n) for n in z.namelist() if not n.endswith('.class')}
for folder in probe_roots:
    configs = list(folder.glob('*.mixins.json'))
    assert len(configs) == 1, (folder, configs)
    resources[configs[0].name] = configs[0].read_bytes()
    resources['META-INF/neoforge.mods.toml'] += ('\n[[mixins]]\nconfig="' + configs[0].name + '"\n').encode()
jar = lab / 'mods/muxi-transfer-qa-only.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
    for name, data in resources.items(): z.writestr(name, data)
    for p in (lab / 'qa-classes').rglob('*.class'): z.write(p, p.relative_to(lab / 'qa-classes').as_posix())
args = '\n'.join(line for line in args.splitlines() if not any(x in line for x in [
    'qa.transfer.samples=', 'muxi.programBinaryCache=', 'muxi.veilShaderEventDispatch=', 'muxi.creativeTraceGate=',
    'qa.transfer.programPaired=', 'muxi.binaryCache.inputFingerprint', 'qa.transfer.distanceControls=']))
flag = str(enabled).lower()
prefix = ['qa.transfer.samples=16', 'muxi.programBinaryCache=false', 'muxi.veilShaderEventDispatch=' + flag,
          'muxi.creativeTraceGate=' + flag, 'qa.transfer.programPaired=false', 'qa.transfer.distanceControls=false']
(lab / 'launch.args').write_text('\n'.join('"-D' + value + '"' for value in prefix) + '\n' + args, encoding='utf-8')
inputs = json.loads((lab / 'inputs.json').read_text(encoding='utf-8'))
inputs.update({'candidateBaseHead': release['commit'], 'actualCoreJarSha256': release['sha256'],
               'qaMode': 'current-committed-native-login-reconnect-distance-three-dimension-timeline',
               'binaryEnabled': False, 'veilRequested': enabled, 'creativeTraceRequested': enabled,
               'nativeJoins': 3, 'measuredNativeJoins': 2, 'transferSetupJoins': 1, 'nativeTransferRoutes': 16, 'creativeOriginStackWalkingDisabled': True,
               'privateTimelineProbesOnly': True, 'oneOwnMCMaximum': True, 'notUninstrumentedBenchmark': True,
               'landingPreGenerated': True, 'neighboringChunksGenerationNotAssumed': True,
               'qaJarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
               'qaSourceSha256': {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}})
(lab / 'inputs.json').write_text(json.dumps(inputs, indent=2), encoding='utf-8')
print(json.dumps({'prepared': str(lab), 'candidateSha256': release['sha256'], 'mode': mode, 'MCNotLaunched': True}))
