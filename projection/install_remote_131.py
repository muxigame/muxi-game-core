"""Prepare the isolated 131 client; this script never starts Java or touches task14 outputs."""
from pathlib import Path
import ctypes
import hashlib
import json
import os
import re
import shutil
import sys
import time
import zipfile

ROOT=Path(r'C:\Users\ranzh\Documents\Codex\schematic-task23-20261002')
SOURCE=Path(r'C:\Users\ranzh\workspace\dev\muxigame\_client_test\game')
PACK=Path(r'C:\Users\ranzh\Documents\Codex\release-unified-task14-20261001\candidate-pack')
CLIENT=ROOT/'client'
ENGINE=ROOT/'engine'
JDK=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1')
OVERLAY_SHA='10b7cd94fadfec82ec6c76eebd1b08babd1c9da1d8346a5e05b2e707c6cce2c1'

def digest(p,kind='sha256'):
    h=hashlib.new(kind)
    with p.open('rb') as f:
        for data in iter(lambda:f.read(1048576),b''):h.update(data)
    return h.hexdigest()
def write(p,data):p.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
def progress(stage,**data):
    write(ROOT/'receipts/install-progress.json',dict(stage=stage,time=time.time(),**data))
    print(stage,flush=True)
def extract(archive,dest):
    for item in archive.infolist():
        target=(dest/item.filename).resolve()
        if dest.resolve() not in target.parents:raise ValueError('Unsafe archive path')
    archive.extractall(dest)

def allowed(rules):
    if not rules:return True
    result=False
    for rule in rules:
        platform=rule.get('os',{})
        if platform.get('name','windows')!='windows':continue
        if platform.get('arch','x86_64') not in ('x86_64','amd64'):continue
        if any((key=='has_custom_resolution')!=value for key,value in rule.get('features',{}).items()):continue
        result=rule.get('action')=='allow'
    return result

def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    if os.environ.get('COMPUTERNAME','').upper()!='JBC_FCRL':raise SystemExit('131 only')
    if CLIENT.exists() or ENGINE.exists():raise SystemExit('Refuse to overwrite an existing isolated client')
    if shutil.disk_usage(ROOT).free<30*1024**3:raise SystemExit('Insufficient free space for independent engine and client copy')
    overlay=ROOT/'input/schematic-client-overlay-task23-qa.zip'
    if digest(overlay)!=OVERLAY_SHA:raise SystemExit('Overlay hash differs')
    CLIENT.mkdir();ENGINE.mkdir()
    extract(zipfile.ZipFile(ROOT/'input/runtime-inputs-task23.zip'),ROOT/'input/extracted')
    formal=json.loads((ROOT/'input/extracted/formal-mods.json').read_text(encoding='utf-8'))['files']
    (CLIENT/'mods').mkdir()
    copied=[]
    progress('verify-and-copy-stock-mods')
    for entry in formal:
        if entry['policy']=='Optional':continue
        source=PACK/entry['path']
        if not source.is_file():source=ROOT/'input/extracted/formal-own-mods'/Path(entry['path']).name
        if not source.is_file() or digest(source,'sha1')!=entry['sha1']:raise ValueError('Formal mod mismatch: '+entry['path'])
        destination=CLIENT/entry['path']
        shutil.copy2(source,destination)
        copied.append({'path':entry['path'],'sha1':entry['sha1'],'sha256':digest(destination)})
    progress('copy-client-pack-assets',mod_count=len(copied))
    for folder in ('config','defaultconfigs','kubejs','shaderpacks','resourcepacks','tacz'):
        if (PACK/folder).is_dir():shutil.copytree(PACK/folder,CLIENT/folder)
    shutil.copytree(SOURCE/'mods/mcef-libraries',CLIENT/'mods/mcef-libraries')
    progress('copy-independent-engine')
    shutil.copytree(SOURCE/'libraries',ENGINE/'libraries')
    shutil.copytree(SOURCE/'assets',ENGINE/'assets')
    version=ENGINE/'versions/BatterMC5Remake';version.mkdir(parents=True)
    for suffix in ('.json','.jar'):
        shutil.copy2(SOURCE/('versions/BatterMC5Remake/BatterMC5Remake'+suffix),version/('BatterMC5Remake'+suffix))
    shutil.copytree(SOURCE/'versions/BatterMC5Remake/BatterMC5Remake-natives',CLIENT/'natives')
    extract(zipfile.ZipFile(overlay),CLIENT)
    qa_receipt=json.loads((ROOT/'input/qa-build-receipt.json').read_text(encoding='utf-8'))
    qa_source=ROOT/'input'/qa_receipt['jar']
    if digest(qa_source)!=qa_receipt['sha256']:raise ValueError('Task23 QA driver hash changed')
    shutil.copy2(qa_source,CLIENT/'mods'/qa_source.name)
    for item in copied:
        if digest(CLIENT/item['path'])!=item['sha256']:raise ValueError('Copied base mod changed')
    for name,expected in (('forgematica-0.4.2+mc1.21.1.jar','e19f1653c2f72c771bca2c21519620b71cb32511e25698ba70d5654d50e96c2d'),('mafglib-0.4.3+mc1.21.1.jar','d40789ad40e5643ae232a07b4535135fe56585c12538bc30158076f956aab081')):
        if digest(CLIENT/'mods'/name)!=expected:raise ValueError('Projection mod hash changed')
    # Private QA context only: no external account, no multiplayer join, no audio/focus theft.
    write(CLIENT/'config/muxi-game-core.json',{'schema':1,'features':{'identity':{'enabled':False},'login':{'enabled':False}}})
    (CLIENT/'options.txt').write_text('lang:zh_cn\nguiScale:2\nmaxFps:30\nenableVsync:false\nonboardAccessibility:false\nsoundCategory_master:0.0\nfullscreen:false\npauseOnLostFocus:false\nrenderDistance:3\nsimulationDistance:5\ngraphicsMode:0\n',encoding='utf-8')
    fml=CLIENT/'config/fml.toml';text=fml.read_text(encoding='utf-8') if fml.exists() else ''
    table=re.search(r'(?m)^\s*\[',text);root,rest=(text[:table.start()],text[table.start():]) if table else (text,'')
    for key,value in [('earlyWindowControl','false'),('earlyWindowProvider','""'),('versionCheck','false')]:
        pat=r'(?m)^([ \t]*'+re.escape(key)+r'[ \t]*=[ \t]*)[^\r\n]*'
        root=re.sub(pat,lambda m:m.group(1)+value,root,count=1) if re.search(pat,root) else root.rstrip('\r\n')+'\n'+key+' = '+value+'\n'
    fml.write_text(root+rest,encoding='utf-8')
    mcef=CLIENT/'config/mcef';mcef.mkdir(exist_ok=True)
    (mcef/'mcef.properties').write_text('skip-download=true\nuse-cache=false\nuser-agent=\ndownload-mirror=\n',encoding='utf-8')
    (CLIENT/'config/iris.properties').write_text('enableShaders=true\nshaderPack=Better MC - Low\ndisableUpdateMessage=true\n',encoding='utf-8')
    meta=json.loads((version/'BatterMC5Remake.json').read_text(encoding='utf-8'))
    libs=[ENGINE/'libraries'/i['downloads']['artifact']['path'] for i in meta['libraries'] if allowed(i.get('rules')) and i.get('downloads',{}).get('artifact')]
    libs=list(dict.fromkeys(libs+[version/'BatterMC5Remake.jar']))
    if any(not p.is_file() for p in libs):raise ValueError('Missing copied launch library')
    substitutions={'auth_player_name':'SchematicTask23','auth_uuid':'105b8ff96f624b24b8aabf11a6752300','auth_access_token':'0','version_name':'BatterMC5Remake','game_directory':str(CLIENT),'assets_root':str(ENGINE/'assets'),'assets_index_name':meta['assetIndex']['id'],'clientid':'','auth_xuid':'','user_type':'legacy','version_type':'release','resolution_width':'1280','resolution_height':'720','natives_directory':str(CLIENT/'natives'),'launcher_name':'schematic-task23-isolated-qa','launcher_version':'1','classpath':os.pathsep.join(map(str,libs)),'library_directory':str(ENGINE/'libraries'),'classpath_separator':os.pathsep}
    def expand(items):
        result=[]
        for item in items:
            if isinstance(item,dict):
                if not allowed(item.get('rules')):continue
                values=item['value'] if isinstance(item['value'],list) else [item['value']]
            else:values=[item]
            for value in values:
                for key,replacement in substitutions.items():value=value.replace('${'+key+'}',replacement)
                if '${' in value:raise ValueError('Unresolved launch substitution')
                result.append(value)
        return result
    javaargs=['-Xms512M','-Xmx6G','-XX:ActiveProcessorCount=4','-Dfile.encoding=UTF-8','-Dmuxi.schematicQA=true','-Dhttp.proxyHost=127.0.0.1','-Dhttp.proxyPort=9','-Dhttps.proxyHost=127.0.0.1','-Dhttps.proxyPort=9']+expand(meta['arguments']['jvm'])+[meta['mainClass']]+expand(meta['arguments']['game'])
    (CLIENT/'launch.args').write_text('\n'.join('"'+arg.replace('\\','/').replace('"','\\"')+'"' for arg in javaargs),encoding='utf-8')
    write(ROOT/'receipts/install-result.json',{'installed':True,'runtimeStarted':False,'computer':os.environ['COMPUTERNAME'],'client':str(CLIENT),'engine':str(ENGINE),'java':str(JDK/'bin/java.exe'),'baselinePack':'1.4.26','stockMods':copied,'projectionMods':[{'name':n,'sha256':digest(CLIENT/'mods'/n)} for n in ('forgematica-0.4.2+mc1.21.1.jar','mafglib-0.4.3+mc1.21.1.jar')],'qaDriver':qa_receipt,'isolatedNatives':True,'isolatedLibraries':True,'isolatedAssets':True,'world':'schematic-task23-local-only','multiplayerConnection':False,'networkListener':'No dedicated server, LAN opening or test HTTP server; integrated world only.','task14OrProductionMutation':False,'entryCoordinationPending':True})
    progress('installed-awaiting-independent-desktop-entry',client=str(CLIENT))
if __name__=='__main__':main()
