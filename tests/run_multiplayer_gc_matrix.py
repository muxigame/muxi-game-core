"""Sequential native four-player 2x2 architecture/collector comparison; never starts production."""
from pathlib import Path
from datetime import datetime
import argparse,json,subprocess,sys

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java-home',type=Path,required=True)
    p.add_argument('--players',type=int,choices=[4,6,8],default=4)
    p.add_argument('--heap-gb',type=int,default=20)
    args=p.parse_args()
    output=ROOT/'build'/('multiplayer-gc-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
    output.mkdir();manifest={'players':args.players,'heapGiB':args.heap_gb,'runs':[]}
    runtime=args.java_home.resolve()
    version=subprocess.run([str(runtime/'bin/java.exe'),'-version'],capture_output=True,text=True)
    manifest['javaVersion']=version.stderr.strip()
    common=[sys.executable,str(ROOT/'tests/run_thread_load_e2e.py'),'--java-home',str(runtime),
        '--players',str(args.players),'--heap-gb',str(args.heap_gb),'--client-cpus','1','--full-pack','--profile']
    print(str(output),flush=True)
    # Alternate collector order to avoid interpreting a consistently warmer filesystem cache as a GC benefit.
    for threaded,gc in ((True,'zgc'),(False,'g1'),(False,'zgc'),(True,'g1')):
        label=('parallel' if threaded else 'serial')+'-'+gc
        command=[*common,'--gc',gc]+(['--threaded'] if threaded else [])
        record={'label':label,'command':command,'status':'running'};manifest['runs'].append(record)
        (output/'matrix.json').write_text(json.dumps(manifest,indent=2))
        print('Running '+label,flush=True)
        with (output/(label+'.log')).open('w',encoding='utf-8') as log:
            code=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT).returncode
        lines=(output/(label+'.log')).read_text(encoding='utf-8',errors='replace').splitlines()
        record['lab']=next((l[len('Native load lab: '):] for l in lines if l.startswith('Native load lab: ')),None)
        record.update(status='passed' if code==0 else 'failed',exitCode=code)
        (output/'matrix.json').write_text(json.dumps(manifest,indent=2))
        if code:raise SystemExit('Failed '+label+'; inspect '+str(output))
    print('All four native runs passed: '+str(output),flush=True)

if __name__=='__main__':main()
