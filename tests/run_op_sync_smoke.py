"""Exercise actual website APIs and native NeoForge OP handlers in two disposable JVMs.

Only existing public libraries are read. No production configuration, database, keys,
worlds or players are copied. Minecraft's TCP listener is disabled by a test-only mixin.
The only HTTP server binds loopback, uses synthetic accounts, and is closed on exit.
"""
from __future__ import annotations
import argparse
from contextlib import ExitStack
from datetime import datetime
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import threading
import time
from unittest.mock import patch
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
import build as core_build


def run():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server',type=Path,required=True,help='Existing public NeoForge libraries')
    parser.add_argument('--website',type=Path,required=True,help='Isolated website server source')
    parser.add_argument('--java-home',type=Path,required=True)
    args=parser.parse_args()
    server=args.server.resolve()
    lab=ROOT/'build'/('op-sync-smoke-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    (lab/'mods').mkdir(parents=True);(lab/'config').mkdir()
    os.environ['BMC_SKIP_DOTENV']='1';os.environ['BMC_DATABASE_PATH']=str(lab/'synthetic-bootstrap.db')
    os.environ['BMC_PUBLIC_URL']='http://testserver';os.environ['BMC_GAME_OP_SYNC_ENABLED']='1'
    os.environ['BMC_GAME_SERVICE_KEY']='synthetic-op-service-credential-00000000'
    sys.path.insert(0,str(args.website.resolve()))
    from fastapi.testclient import TestClient
    from app import main
    from app.oidc import WebsiteAuthStore,WebsiteAccount
    from app.player_platform import PlatformStore
    auth=WebsiteAuthStore(lab/'synthetic-op.db');store=PlatformStore(auth.database)
    sessions={}
    for uid in (10090,10091):
        account=WebsiteAccount(subject='synthetic-op-'+str(uid),uid=uid,username='test'+str(uid),
            nickname='Synthetic '+str(uid),game_name=str(uid),email=None,email_verified=False,role='player')
        auth.player_profile(account);sessions[uid]=auth.create_session(account)
    store.initialize_permissions(10090,True,0)
    with ExitStack() as stack:
        stack.enter_context(patch.object(main,'web_auth_store',auth));stack.enter_context(patch.object(main,'platform_store',store))
        client=stack.enter_context(TestClient(main.app));client.cookies.set('bmc_session',sessions[10090])
        def save(level):
            target=client.get('/api/v1/platform/game-ops/10091').json()['target']
            response=client.post('/api/v1/platform/game-ops/10091',headers={'origin':'http://testserver'},json={
                'level':level,'expectedRevision':target['sync']['revision'],'expectedLevel':target['desiredLevel'],
                'identityConfirmation':target['identityConfirmation'],'reason':'isolated native integration test'})
            response.raise_for_status();return response.json()
        save(4)
        traffic=[];drop={'ack':True}
        class Bridge(BaseHTTPRequestHandler):
            def log_message(self,*args):pass
            def handle_api(self):
                size=int(self.headers.get('content-length','0'))
                body=self.rfile.read(size)
                # One lost ACK tests the real client's durable retry rather than a mock target.
                if self.path.endswith('/ack') and drop['ack']:
                    drop['ack']=False;traffic.append({'path':self.path,'status':503})
                    self.send_response(503);self.end_headers();return
                response=client.request(self.command,self.path,content=body,headers={
                    'x-muxi-server-key':self.headers.get('x-muxi-server-key',''),
                    'content-type':self.headers.get('content-type','application/json')})
                traffic.append({'path':self.path,'status':response.status_code})
                self.send_response(response.status_code);self.send_header('content-type','application/json')
                self.send_header('content-length',str(len(response.content)));self.end_headers();self.wfile.write(response.content)
            do_GET=handle_api
            do_POST=handle_api
        bridge=ThreadingHTTPServer(('127.0.0.1',0),Bridge)
        worker=threading.Thread(target=bridge.serve_forever,daemon=True);worker.start()
        try:
            release=json.loads((ROOT/'build/release.json').read_text());core=ROOT/'build/libs'/release['artifact']
            shutil.copy2(core,lab/'mods'/core.name)
            for pattern in ('waystones-*.jar','balm-*.jar'):
                found=list((server/'mods').glob(pattern))
                if len(found)!=1:raise ValueError('Expected one existing public runtime dependency: '+pattern)
                shutil.copy2(found[0],lab/'mods'/found[0].name)
            (lab/'config/muxi-game-core.json').write_text('{"schema":1,"features":{}}',encoding='utf-8')
            (lab/'config/fml.toml').write_text('versionCheck = false\n',encoding='utf-8')
            (lab/'config/neoforge-server.toml').write_text('advertiseDedicatedServerToLan = false\n',encoding='utf-8')
            (lab/'eula.txt').write_text('eula=true\n',encoding='utf-8')
            (lab/'server.properties').write_text(
                'server-ip=127.0.0.1\nserver-port=25589\nonline-mode=false\nlevel-name=qa-world\n'
                'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
                'view-distance=2\nsimulation-distance=2\nmax-players=1\nenable-rcon=false\nenable-query=false\n'
                'spawn-protection=0\nop-permission-level=4\nfunction-permission-level=2\n',encoding='utf-8')
            compiler,runtime=core_build.java_tools(args.java_home)
            jars=sorted((server/'libraries').rglob('*.jar'));neo=server/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-server.jar'
            mapped=next(p for p in jars if p.name=='server-1.21.1-20240808.144430-srg.jar')
            cp=os.pathsep.join(str(p) for p in [core,neo,mapped,*jars])
            classes=lab/'test-classes'
            core_build.compile_java(compiler,sorted((ROOT/'tests/op-sync-smoke/java').rglob('*.java'))+
                sorted((ROOT/'tests/smoke-common/java').rglob('*.java')),classes,cp,lab/'compile.args')
            with zipfile.ZipFile(lab/'mods/muxi-op-smoke-only.jar','w',zipfile.ZIP_DEFLATED) as z:
                z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n'
                    '[[mixins]]\nconfig="muxi_op_smoke.mixins.json"\n[[mods]]\nmodId="muxi_op_smoke"\nversion="1.0.0"\ndisplayName="Isolated OP tests"\n')
                z.writestr('muxi_op_smoke.mixins.json',json.dumps({'required':True,'minVersion':'0.8',
                    'package':'net.muxigame.core.taskssmoke.mixin','compatibilityLevel':'JAVA_21','mixins':['DisableNetworkMixin']}))
                for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
            launch=shlex.split((server/'libraries/net/neoforged/neoforge/21.1.250/win_args.txt').read_text())
            libraries=(server/'libraries').as_posix()
            launch=[s.replace('libraries/',libraries+'/').replace('-DlibraryDirectory=libraries','-DlibraryDirectory='+libraries) for s in launch]
            phases=[]
            for phase in (1,2):
                command=[str(runtime),'-Xms256M','-Xmx2G','-XX:ActiveProcessorCount=4','-Dfile.encoding=UTF-8',
                    '-Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.WindowsSelectorProvider',
                    f'-Djdk.net.unixdomain.tmpdir={lab / "nonexistent-uds-directory"}',
                    f'-Dmuxi.opTestPhase={phase}',f'-Dmuxi.opTestEndpoint=http://127.0.0.1:{bridge.server_port}/api/internal/game/ops-sync/',*launch,'--nogui']
                with (lab/f'boot-phase-{phase}.log').open('w',encoding='utf-8') as log:
                    process=subprocess.Popen(command,cwd=lab,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
                    deadline=time.monotonic()+180;updated=False
                    try:
                        while process.poll() is None:
                            if phase==2 and not updated and (lab/'request-next-event').exists():save(0);updated=True
                            if time.monotonic()>deadline:raise TimeoutError('isolated OP smoke timed out')
                            time.sleep(0.2)
                    finally:
                        if process.poll() is None:process.terminate();process.wait(timeout=20)
                result_path=lab/f'op-smoke-phase-{phase}.json'
                result=json.loads(result_path.read_text()) if result_path.exists() else {'success':False,'error':'no native result; inspect boot log'}
                result['exitCode']=process.returncode;phases.append(result)
                if not result['success'] or process.returncode:raise AssertionError({'lab':str(lab),**result})
            status=store.op_status(10091)
            assert status['desiredLevel']==0 and status['sync']['observedLevel']==0 and status['sync']['state']=='applied',status
            assert not store.permissions(10091)['platformAdmin']
            assert any(t['status']==503 for t in traffic) and any(t['path'].endswith('/ack') and t['status']==200 for t in traffic)
            with store.connect() as db:
                assert db.execute('SELECT count(*) FROM game_op_audit').fetchone()[0]==2
                audit=[dict(row) for row in db.execute('SELECT * FROM game_op_audit')]
            output={'success':True,'lab':str(lab),'phases':phases,'websiteStatus':status,'websiteAudit':audit,
                'httpTraffic':traffic,'coreSha256':hashlib.sha256(core.read_bytes()).hexdigest()}
            (lab/'op-sync-result.json').write_text(json.dumps(output,indent=2),encoding='utf-8')
            print(json.dumps({'success':True,'lab':str(lab),'nativeChecks':sum(len(p['passed']) for p in phases),
                'websiteStatus':status,'coreSha256':output['coreSha256']},indent=2))
        finally:bridge.shutdown();bridge.server_close();worker.join(timeout=5)


if __name__=='__main__':run()
