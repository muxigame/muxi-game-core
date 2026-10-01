"""Run a real hidden Minecraft client against a disposable loopback-only server. No launcher/relay/production config."""
from __future__ import annotations
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import shlex
import shutil
import socket
import subprocess
import sys
import time
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
import build as core_build

def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--threaded',action='store_true')
    args=parser.parse_args()
    server=ROOT.parent/'bmc5server'
    lab=ROOT/'build'/('dimensions-e2e-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
    lab.mkdir(parents=True);(lab/'mods').mkdir();(lab/'config').mkdir()
    with socket.socket() as listener:
        listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
    (lab/'e2e-address.json').write_text(json.dumps({'host':'127.0.0.1','port':port}),encoding='utf-8')
    (lab/'config/muxi-game-core.json').write_text('{"schema":1,"features":{}}',encoding='utf-8')
    (lab/'config/fml.toml').write_text('versionCheck = false\n',encoding='utf-8')
    (lab/'config/neoforge-server.toml').write_text('advertiseDedicatedServerToLan = false\n',encoding='utf-8')
    (lab/'eula.txt').write_text('eula=true\n',encoding='utf-8')
    (lab/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=qa-world\nlevel-seed=12345\nview-distance=2\nsimulation-distance=2\nmax-players=1\nenable-rcon=false\nenable-query=false\nspawn-protection=0\nmax-tick-time=120000\n',encoding='utf-8')
    release=json.loads((ROOT/'build/release.json').read_text(encoding='utf-8'))
    core=ROOT/'build/libs'/release['artifact'];shutil.copy2(core,lab/'mods'/core.name)
    # Core's current release has a real server-side Waystones integration, so the
    # disposable server must mirror those mandatory runtime dependencies too.
    for pattern in ('balm-neoforge*.jar','waystones-neoforge*.jar'):
        matches=list((server/'mods').glob(pattern))
        if len(matches)!=1:
            raise RuntimeError(f'Expected exactly one runtime dependency for {pattern}, got {matches}')
        shutil.copy2(matches[0],lab/'mods'/matches[0].name)
    compiler,runtime=core_build.java_tools(args.java_home.resolve())
    jars=sorted((server/'libraries').rglob('*.jar'))
    neo=server/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-server.jar'
    mapped=next(p for p in jars if p.name=='server-1.21.1-20240808.144430-srg.jar')
    classes=lab/'test-classes'
    core_build.compile_java(compiler,sorted((ROOT/'tests/dimensions-e2e/java').rglob('*.java')),classes,os.pathsep.join(map(str,[core,neo,mapped,*jars])),lab/'compile.args')
    with zipfile.ZipFile(lab/'mods/muxi-dimensions-e2e-only.jar','w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_dimensions_e2e"\nversion="1.0.0"\ndisplayName="Local dimension E2E fixture"\n')
        for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
    launch=shlex.split((server/'libraries/net/neoforged/neoforge/21.1.250/win_args.txt').read_text(encoding='utf-8'))
    libraries=(server/'libraries').as_posix()
    launch=[s.replace('libraries/',libraries+'/').replace('-DlibraryDirectory=libraries','-DlibraryDirectory='+libraries) for s in launch]
    command=[str(runtime),'-Xms512M','-Xmx2G','-XX:ActiveProcessorCount=4','-Dfile.encoding=UTF-8',*launch,'--nogui']
    if args.threaded:command.insert(1,'-Dmuxi.dimensionThreads=true')
    print('Disposable E2E lab: '+str(lab),flush=True)
    with (lab/'boot.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen(command,cwd=lab,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
        try:
            deadline=time.monotonic()+150
            while not (lab/'e2e-ready').exists():
                if process.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Isolated server failed; inspect '+str(lab/'boot.log'))
                time.sleep(0.5)
            result=subprocess.run([sys.executable,str(ROOT/'tests/run_tasks_client_smoke.py'),'--java-home',str(args.java_home.resolve()),'--dimensions-server',str(lab)],timeout=360,capture_output=True,text=True,encoding='utf-8',errors='replace')
            (lab/'client-run.log').write_text(result.stdout+'\n'+result.stderr,encoding='utf-8')
            print(result.stdout)
            if result.returncode:print(result.stderr);raise RuntimeError('Client E2E failed')
        finally:
            if process.poll() is None:
                process.stdin.write('stop\n');process.stdin.flush()
                try:process.wait(timeout=40)
                except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=15)
    if process.returncode:raise RuntimeError('Local server did not shut down cleanly')
    (lab/'e2e-result.json').write_text(json.dumps({'success':True,'serverExitCode':process.returncode,'loopbackPort':port}),encoding='utf-8')
    print('E2E passed; server stopped. '+str(lab))

if __name__=='__main__':main()
