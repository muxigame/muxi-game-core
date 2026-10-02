"""Inspect exact installed mod bytecode descriptors and native execution ordering; no Minecraft launch."""
from pathlib import Path
import subprocess,re,json,hashlib,zipfile
root=Path(__file__).resolve().parent;out=root/'evidence';out.mkdir(exist_ok=True)
mods=Path('C:/Users/Administrator/WorkSpace/muxigame/_client_test/game/mods')
world=mods/'xaeroworldmap-neoforge-1.21.1-1.45.0.jar';waystones=mods/'waystones-neoforge-1.21.1-21.1.42.jar';balm=mods/'balm-neoforge-1.21.1-21.0.65.jar'
classpath=';'.join(map(str,[world,waystones,balm]))
checks=[]
def inspect(name,cp=classpath):
 return subprocess.run(['C:/Program Files/Java/jdk-24/bin/javap.exe','-classpath',cp,'-p','-s','-c',name],check=True,capture_output=True).stdout.decode('utf-8')
def check(condition,label):
 assert condition,label
 checks.append(label)
targets={'xaero.map.mods.gui.WaypointRenderer':world,'xaero.map.gui.GuiMap':world,'xaero.map.mods.gui.WaypointReader':world,'net.blay09.mods.waystones.core.WaystoneTeleportManager':waystones}
classes={name:inspect(name) for name in targets}
base=root/'muxi-game-core/src/main/java/net/muxigame/core/compat/mixin'
for path,target in [(base/'xaeroworldmap/NetworkStoneRendererMixin.java','xaero.map.mods.gui.WaypointRenderer'),(base/'xaeroworldmap/NetworkMapFrameMixin.java','xaero.map.gui.GuiMap'),(base/'xaeroworldmap/WaystoneWaypointMenuMixin.java','xaero.map.mods.gui.WaypointReader'),(base/'waystones/NetworkPendingTeleportMixin.java','net.blay09.mods.waystones.core.WaystoneTeleportManager')]:
 for method in re.findall(r'method\s*=\s*"([^"]+)"',path.read_text('utf-8')):
  name,descriptor=method.split('(',1);descriptor='('+descriptor
  signature=re.compile(r'\b'+re.escape(name)+r'\([^\n]+\);\s+descriptor: '+re.escape(descriptor))
  check(bool(signature.search(classes[target])),f'{target}.{method} exists exactly')
parent=inspect('xaero.map.element.render.ElementRenderer')
check('public C getContext();' in parent and 'descriptor: ()Ljava/lang/Object;' in parent,'renderer uses the native public inherited context getter')
check('(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/screens/Screen;Lxaero/map/MapProcessor;Lnet/minecraft/world/entity/Entity;)V' in classes['xaero.map.gui.GuiMap'],'native map constructor remains four native arguments')
check('worldmapWaypointsScale' in classes['xaero.map.mods.gui.WaypointRenderer'],'native waypoint setting scale retained')
manager=classes['net.blay09.mods.waystones.core.WaystoneTeleportManager']
check('WaystoneTeleportEntityEvent$Pre.getOverrideResult' in manager,'native final entity path reads guard failure override')
match=re.search(r'private static [^\n]*lambda\$tryTeleportAsync\$14.*?(?=\n  (?:public|private|protected))',manager,re.S)
check(bool(match),'exact native async final-stage method identified')
stage=match.group(0)
check(stage.index('validateRequirements:')<stage.index('consumeRequirements:')<stage.index('performTeleport:'),'native final guard injection precedes native XP consumption and movement')
check('validatePendingTeleport:' in manager,'native post-preparation pending validation target exists')
pending=root/'muxi-game-core/src/main/java/net/muxigame/core/compat/mixin/waystones/NetworkPendingTeleportMixin.java'
check('require=1' in pending.read_text('utf-8'),'native guard hooks require target match')
with zipfile.ZipFile(world) as archive:
 nested=next(n for n in archive.namelist() if n.startswith('META-INF/jarjar/xaerolib-') and n.endswith('.jar'))
 copy=out/'xaerolib-inspection.jar';copy.write_bytes(archive.read(nested))
try:
 lib=inspect('xaero.lib.client.gui.ScreenBase',str(copy))
finally:copy.unlink()
close=re.search(r'public void onClose\(\);.*?(?=\n  public)',lib,re.S).group(0)
check('Field escape:' in close and 'onExit:' in close,'native Esc close uses supplied escape adapter')
back=re.search(r'public void goBack\(\);.*?(?=\n  public)',lib,re.S).group(0)
check('Field parent:' in back and 'onExit:' in back,'native back uses supplied parent adapter')
mc=root/'muxi-game-core/src/main/resources/muxi_game_core.compat.maps.mixins.json'
config=json.loads(mc.read_text('utf-8'))
check('waystones.NetworkPendingTeleportMixin' in config['mixins'],'guard is common server/client registration')
check(all(n in config['client'] for n in ['xaeroworldmap.NetworkStoneRendererMixin','xaeroworldmap.NetworkMapFrameMixin']),'native rendering hooks registered for client')
plugin=(base.parent/'CompatMixinPlugin.java').read_text('utf-8')
check('Map.entry("waystones", "waystones")' in plugin,'Waystones guard is enabled by installed-mod filter')
sources=[root/'muxi-terminal/src/main/java/net/muxigame/terminal/client/map/NativeMapLauncher.java',*list((root/'muxi-game-core/src/main/java/net/muxigame/core/feature/waystones').rglob('*.java')),*list((root/'muxi-game-core/src/main/java/net/muxigame/core/client/waystones').glob('*.java'))]
text='\n'.join(p.read_text('utf-8') for p in sources)
check(not re.search(r'forceTeleport|createUnchecked|performCommand|sendCommand|setOp\(|setBlock\(|buildExit\(',text),'no forced coordinate teleport, commands, OP or world construction')
check('createDefaultTeleportContext' in text and 'tryTeleportAsync' in text,'execution uses original public Waystones rules')
check('KeyMapping' not in text and '.save(' not in text,'no hotkey remapping or minimap/global settings writes')
report={'success':True,'checks':len(checks),'scope':'exact installed native SDK descriptor/bytecode/static routing inspection, not a live Mixin/game test','assertions':checks,'installedJars':[{ 'file':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in [world,waystones,balm]]}
(out/'native-sdk-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'success':True,'checks':len(checks)}))
