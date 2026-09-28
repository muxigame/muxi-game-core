"""Render the real task UI in an invisible, isolated native Minecraft client with synthetic fixture data.

Uses existing libraries/assets and an offline QA identity, never the user's account/token or production game directory.
"""
from __future__ import annotations
from datetime import datetime
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
import build as core_build


def allowed(rules: list[dict] | None) -> bool:
    if not rules: return True
    result=False
    for rule in rules:
        platform=rule.get('os',{})
        if platform.get('name','windows')!='windows': continue
        if platform.get('arch','x86_64') not in ('x86_64','amd64'): continue
        if any((key=='has_custom_resolution')!=value for key,value in rule.get('features',{}).items()): continue
        result=rule.get('action')=='allow'
    return result


def main() -> None:
    game=ROOT.parent/'_client_test/game'
    version='BatterMC5Remake'
    meta=json.loads((game/f'versions/{version}/{version}.json').read_text(encoding='utf-8'))
    lab=ROOT/'build'/('tasks-client-smoke-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
    lab.mkdir(parents=True,exist_ok=False); (lab/'mods').mkdir(); (lab/'natives').mkdir(); (lab/'config').mkdir()
    (lab/'config/fml.toml').write_text('earlyWindowControl = false\nearlyWindowProvider = ""\nversionCheck = false\n',encoding='utf-8')
    (lab/'options.txt').write_text('lang:zh_cn\nguiScale:2\nmaxFps:30\nenableVsync:false\nonboardAccessibility:false\nsoundCategory_master:0.0\nfullscreen:false\npauseOnLostFocus:false\n',encoding='utf-8')
    release=json.loads((ROOT/'build/release.json').read_text(encoding='utf-8'))
    core=ROOT/'build/libs'/release['artifact']; shutil.copy2(core,lab/'mods'/core.name)
    libraries=[]
    for lib in meta['libraries']:
        if not allowed(lib.get('rules')): continue
        artifact=lib.get('downloads',{}).get('artifact')
        if artifact: libraries.append(game/'libraries'/artifact['path'])
    libraries.append(game/f'versions/{version}/{version}.jar')
    # The locally merged launcher manifest repeats shared vanilla/NeoForge dependencies.
    libraries=list(dict.fromkeys(libraries))
    missing=[str(p) for p in libraries if not p.is_file()]
    if missing: raise SystemExit('Missing existing client dependencies: '+', '.join(missing))
    compiler,_=core_build.java_tools(None)
    neo=game/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-client.jar'
    mc=game/'libraries/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar'
    # Only for compilation; runtime uses the exact version manifest's classpath.
    server_libs=list((ROOT.parent/'bmc5server/libraries').rglob('*.jar'))
    cp=os.pathsep.join(map(str,[core,neo,mc,*libraries,*server_libs]))
    classes=lab/'test-classes'
    core_build.compile_java(compiler,sorted((ROOT/'tests/client-smoke/java').rglob('*.java')),classes,cp,lab/'compile.args')
    with zipfile.ZipFile(lab/'mods/muxi-tasks-client-smoke-only.jar','w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n'
            '[[mixins]]\nconfig="muxi_hidden_render.mixins.json"\n'
            '[[mods]]\nmodId="muxi_tasks_client_smoke"\nversion="1.0.0"\ndisplayName="Isolated native task UI tests"\n'
            '[[dependencies.muxi_tasks_client_smoke]]\nmodId="muxi_game_core"\ntype="required"\nversionRange="[1.5.0,)"\nordering="AFTER"\nside="CLIENT"\n')
        for p in classes.rglob('*.class'): z.write(p,p.relative_to(classes).as_posix())
        z.writestr('muxi_hidden_render.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.core.taskssmoke.mixin','compatibilityLevel':'JAVA_21','client':['HiddenWindowMixin','WeaponIconFixtureMixin'],'injectors':{'defaultRequire':1}}))
    old_natives=game/f'versions/{version}/{version}-natives'
    if old_natives.is_dir(): shutil.copytree(old_natives,lab/'natives',dirs_exist_ok=True)
    substitutions={
        'auth_player_name':'MuxiTaskPreview','auth_uuid':'e0e0554c6ef64fa4bf251efad6fc5f4d','auth_access_token':'0',
        'version_name':version,'game_directory':str(lab),'assets_root':str(game/'assets'),
        'assets_index_name':meta['assetIndex']['id'],'clientid':'','auth_xuid':'','user_type':'legacy','version_type':'release',
        'resolution_width':'1280','resolution_height':'720','natives_directory':str(lab/'natives'),
        'launcher_name':'muxi-isolated-ui-test','launcher_version':'1','classpath':os.pathsep.join(map(str,libraries)),
        'library_directory':str(game/'libraries'),'classpath_separator':os.pathsep
    }
    def expand(items):
        output=[]
        for item in items:
            if isinstance(item,dict):
                if not allowed(item.get('rules')): continue
                values=item['value'] if isinstance(item['value'],list) else [item['value']]
            else: values=[item]
            for value in values:
                for key,replacement in substitutions.items(): value=value.replace('${'+key+'}',replacement)
                if '${' in value: raise ValueError('Unresolved client launch template')
                output.append(value)
        return output
    arguments=['-Xms512M','-Xmx2G','-XX:ActiveProcessorCount=4','-Dfile.encoding=UTF-8','-Djava.awt.headless=true',*expand(meta['arguments']['jvm']),meta['mainClass'],*expand(meta['arguments']['game'])]
    argfile=lab/'launch.args'
    argfile.write_text('\n'.join('"'+a.replace('\\','/').replace('"','\\"')+'"' for a in arguments),encoding='utf-8')
    runtime=ROOT.parent/'perf-lab/java21/jdk-21.0.2/bin/java.exe'
    with (lab/'boot.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen([str(runtime),'@'+str(argfile)],cwd=lab,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
        try: code=process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            process.terminate()
            try: process.wait(timeout=10)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
            raise SystemExit('Isolated client timed out; logs: '+str(lab/'boot.log'))
    report=lab/'client-smoke-result.json'
    result=json.loads(report.read_text(encoding='utf-8')) if report.exists() else {'success':False,'error':'No native UI result'}
    result.update({'exitCode':code,'lab':str(lab)})
    print(json.dumps(result,ensure_ascii=False,indent=2))
    if not result.get('success') or code:
        print('\n'.join((lab/'boot.log').read_text(encoding='utf-8',errors='replace').splitlines()[-85:]))
        raise SystemExit(1)


if __name__=='__main__': main()
