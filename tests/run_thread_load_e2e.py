"""Native clients, machines, exploration and save/restart in a fresh loopback lab."""
from __future__ import annotations
import argparse
from datetime import datetime
import json,os,random,shlex,shutil,socket,subprocess,sys,time,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
import build as core_build
from thread_load_metrics import ResourceMonitor, summarize_jfr

def main():
    sys.stdout.reconfigure(encoding='utf-8',errors='replace')
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java-home',type=Path,required=True)
    p.add_argument('--threaded',action='store_true')
    p.add_argument('--travel-disabled',action='store_true',help='Matched baseline with movement admission/prefetch disabled')
    p.add_argument('--stability',action='store_true',help='Mob/boss combat, native construction and extended exploration')
    p.add_argument('--seed',type=int,default=12345)
    p.add_argument('--full-pack',action='store_true')
    p.add_argument('--profile',action='store_true',help='Record native JFR samples for both matched benchmark runs')
    p.add_argument('--players',type=int,choices=[2,4,6,8],default=2)
    p.add_argument('--spacing',type=int,default=2048)
    p.add_argument('--gc',choices=['g1','zgc'],default='g1')
    p.add_argument('--heap-gb',type=int,default=8)
    p.add_argument('--client-cpus',type=int,default=2)
    args=p.parse_args()
    if args.stability and not args.full_pack:p.error('--stability requires --full-pack')
    if not 2<=args.heap_gb<=64 or args.spacing<128:p.error('Heap must be 2..64 GiB and lanes at least 128 blocks apart')
    roles=[base+(str(i+1) if i else '') for i in range(args.players//2) for base in ('Home','Survival')]
    server=ROOT.parent/'bmc5server'
    lab=ROOT/'build'/('thread-load-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    (lab/'mods').mkdir(parents=True);(lab/'config').mkdir()
    release=json.loads((ROOT/'build/release.json').read_text())
    core=ROOT/'build/libs'/release['artifact'];shutil.copy2(core,lab/'mods'/core.name)
    for mod in (server/'mods').glob('*.jar'):
        if mod.name.startswith(('muxi-game-core-','muxi-identity-','c2me-')):continue
        if args.full_pack or 'create-1.21' in mod.name:shutil.copy2(mod,lab/'mods'/mod.name)
    (lab/'config/muxi-game-core.json').write_text('{"schema":1,"features":{}}')
    (lab/'config/fml.toml').write_text('versionCheck = false\n')
    # Match production's disabled background profiler. On Windows Spark falls
    # back to ~100 Hz Java ThreadDump safepoints; JFR already profiles this run.
    (lab/'config/spark').mkdir()
    (lab/'config/spark/config.json').write_text('{"backgroundProfiler":false}\n')
    if args.full_pack:
        shutil.copy2(server/'config/fml.toml',lab/'config/fml.toml')
        (lab/'config/webdisplays_common.toml').write_text('[mini_server]\nminiserv_port = 0\n')
        for name in ('mowziesmobs-common.toml','alexsmobs-common.toml','goblintraders-entities.toml','terrablender.toml','sereneseasons/seasons.toml','blueprint-common.toml','sparsestructures.json5'):
            source=server/'config'/name
            if source.is_file():
                dest=lab/'config'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
        for name in ('iceandfire','alexsmobs','mowziesmobs','biomeswevegone','paxi/datapacks'):
            if (server/'config'/name).is_dir():shutil.copytree(server/'config'/name,lab/'config'/name,dirs_exist_ok=True)
        for name in ('server_scripts','startup_scripts','data'):
            if (server/'kubejs'/name).is_dir():shutil.copytree(server/'kubejs'/name,lab/'kubejs'/name,dirs_exist_ok=True)
    (lab/'config/neoforge-server.toml').write_text('advertiseDedicatedServerToLan = false\n')
    (lab/'eula.txt').write_text('eula=true\n')
    # Avoid Windows' dynamic client-port range: startup takes a minute, during
    # which an OS-selected ephemeral port can be reused by an outbound connection.
    candidates=random.sample(range(15000,25000),100)
    for port in candidates:
        try:
            with socket.socket() as sock:
                if hasattr(socket,'SO_EXCLUSIVEADDRUSE'):sock.setsockopt(socket.SOL_SOCKET,socket.SO_EXCLUSIVEADDRUSE,1)
                sock.bind(('127.0.0.1',port))
            break
        except OSError:continue
    else:raise RuntimeError('No free loopback test port')
    (lab/'e2e-address.json').write_text(json.dumps({'host':'127.0.0.1','port':port}))
    (lab/'load-roles.json').write_text(json.dumps(roles))
    (lab/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=qa-world\nlevel-seed={args.seed}\nview-distance=3\nsimulation-distance=3\nmax-players={args.players}\nenable-rcon=false\nenable-query=false\nspawn-protection=0\nmax-tick-time=180000\n')
    compiler,runtime=core_build.java_tools(args.java_home.resolve())
    jars=sorted((server/'libraries').rglob('*.jar'))
    neo=server/'libraries/net/neoforged/neoforge/21.1.250/neoforge-21.1.250-server.jar'
    mapped=next(j for j in jars if j.name=='server-1.21.1-20240808.144430-srg.jar')
    shutil.copytree(ROOT/'tests/thread-load/java',lab/'fixture-sources')
    classes=lab/'test-classes'
    core_build.compile_java(compiler,sorted((ROOT/'tests/thread-load/java').rglob('*.java')),classes,os.pathsep.join(map(str,[core,neo,mapped,*jars])),lab/'compile.args')
    with zipfile.ZipFile(lab/'mods/muxi-thread-load-only.jar','w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="muxi_thread_load"\nversion="1.0.0"\ndisplayName="Isolated native thread load fixture"\n[[mixins]]\nconfig="muxi_load_diagnostics.mixins.json"\n')
        z.writestr('muxi_load_diagnostics.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'net.muxigame.core.taskssmoke.mixin','compatibilityLevel':'JAVA_21','mixins':['ConnectionDiagnosticMixin'],'injectors':{'defaultRequire':1}}))
        for file in classes.rglob('*.class'):z.write(file,file.relative_to(classes).as_posix())
    launch=shlex.split((server/'libraries/net/neoforged/neoforge/21.1.250/win_args.txt').read_text())
    libraries=(server/'libraries').as_posix()
    launch=[s.replace('libraries/',libraries+'/').replace('-DlibraryDirectory=libraries','-DlibraryDirectory='+libraries) for s in launch]
    gc_flags=['-XX:+UseG1GC'] if args.gc=='g1' else ['-XX:+UseZGC','-XX:+ZGenerational']
    command=[str(runtime),f'-Xms{min(8,args.heap_gb)}G',f'-Xmx{args.heap_gb}G',*gc_flags,'-XX:ActiveProcessorCount=4','-Xlog:gc*,safepoint:file=gc.log:time,uptime,level,tags',f'-Dmuxi.qa.players={args.players}',f'-Dmuxi.qa.spacing={args.spacing}','-Dfile.encoding=UTF-8',*launch,'--nogui']
    if args.stability:
        command.insert(1,'-Dmuxi.qa.stability=true')
        (lab/'stability.json').write_text(json.dumps({'routeBlocks':1024,'routeY':336,'seed':args.seed,'scenarioVersion':6,'arenaX':96}))
    if args.threaded:command.insert(1,'-Dmuxi.dimensionThreads=true')
    if args.travel_disabled:command.insert(1,'-Dmuxi.chunkTravel.disabled=true')
    if args.profile:command.insert(1,'-XX:StartFlightRecording=filename=native-load.jfr,settings=profile,dumponexit=true')
    print('Native load lab: '+str(lab),flush=True)
    clients=[];logs=[]
    with (lab/'boot.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen(command,cwd=lab,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
        monitor=ResourceMonitor(process.pid,lab)
        try:
            deadline=time.monotonic()+300
            while not (lab/'e2e-ready').exists():
                if (lab/'load-result.json').exists():raise RuntimeError('Native fixture failed before clients: '+str(lab/'load-result.json'))
                if any((lab/'crash-reports').glob('crash-*-server.txt')):raise RuntimeError('Native server crashed during startup: '+str(lab))
                if process.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Server fixture failed: '+str(lab/'boot.log'))
                time.sleep(.5)
            for role in roles:
                client_log=(lab/f'client-{role}.log').open('w',encoding='utf-8');logs.append(client_log)
                cmd=[sys.executable,str(ROOT/'tests/run_tasks_client_smoke.py'),'--java-home',str(args.java_home.resolve()),'--dimensions-server',str(lab),'--load-role',role,'--client-cpus',str(args.client_cpus)]
                if args.full_pack:cmd.append('--full-pack')
                clients.append(subprocess.Popen(cmd,stdout=client_log,stderr=subprocess.STDOUT))
            deadline=time.monotonic()+(1800 if args.stability else 900)
            while not (lab/'load-result.json').exists():
                if any((lab/'crash-reports').glob('crash-*-server.txt')):raise RuntimeError('Native server crashed; inspect '+str(lab/'crash-reports'))
                if process.poll() is not None or any(c.poll() not in (None,0) for c in clients):raise RuntimeError('Native load process failed; inspect '+str(lab))
                if time.monotonic()>deadline:raise TimeoutError('Native load timed out')
                monitor.sample([c.pid for c in clients])
                time.sleep(1)
            report=json.loads((lab/'load-result.json').read_text())
            for client in clients:
                if client.wait(timeout=60):raise RuntimeError('Client failed; inspect '+str(lab))
            try:process.wait(timeout=60)
            except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=15)
            report.update({'threaded':args.threaded,'fullPack':args.full_pack,'profile':args.profile,'warmupTicks':600,'gc':args.gc,'heapGiB':args.heap_gb,'initialHeapGiB':min(8,args.heap_gb),'serverActiveProcessors':4,'clientActiveProcessors':args.client_cpus,'core':release,'serverExitCode':process.returncode,'resources':monitor.summary()})
            report['travelDisabled']=args.travel_disabled
            report['stabilityEnabled']=args.stability
            report['seed']=args.seed
            if args.profile and report['success']:report['gcProfile']=summarize_jfr(args.java_home.resolve(),lab,report)
            (lab/'load-result.json').write_text(json.dumps(report,indent=2))
            if not report['success'] or process.returncode:raise RuntimeError('Load checks failed; inspect '+str(lab))
        finally:
            monitor.save()
            # Stop only the local process started here, never a production Java process.
            if process.poll() is None:
                try:process.stdin.write('stop\n');process.stdin.flush();process.wait(timeout=40)
                except (OSError,subprocess.TimeoutExpired):process.terminate();process.wait(timeout=15)
            for client in clients:
                if client.poll() is None:
                    # Client wrapper owns its Java child; give it time to observe disconnect and clean up.
                    try:client.wait(timeout=30)
                    except subprocess.TimeoutExpired:
                        subprocess.run(['taskkill','/PID',str(client.pid),'/T','/F'],capture_output=True)
                        client.wait(timeout=10)
            for log in logs:log.close()
    verify=[arg.replace('file=gc.log:','file=gc-restart.log:') for arg in command if not arg.startswith('-XX:StartFlightRecording=')];verify.insert(1,'-Dmuxi.qa.verifySave=true')
    with (lab/'restart.log').open('w',encoding='utf-8') as log:
        verify_process=subprocess.Popen(verify,cwd=lab,stdout=log,stderr=subprocess.STDOUT)
        try:verify_code=verify_process.wait(timeout=180)
        except subprocess.TimeoutExpired:verify_process.terminate();verify_process.wait(timeout=15);raise
    if verify_code or not (lab/'load-save-result.json').exists():raise RuntimeError('Save restart verification failed: '+str(lab))
    print('Native load and save/restart passed: '+str(lab),flush=True)

if __name__=='__main__':main()
