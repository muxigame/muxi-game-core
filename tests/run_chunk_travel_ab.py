"""Sequential, fresh-world, native four-player admission/prefetch A/B on loopback."""
from pathlib import Path
from datetime import datetime
import argparse,json,subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java-home',type=Path,required=True)
    args=p.parse_args()
    out=ROOT/'build'/('chunk-travel-ab-'+datetime.now().strftime('%Y%m%d-%H%M%S'))
    out.mkdir()
    manifest={'runs':[],'method':'same artifact, parallel dimensions, G1, fresh seed 12345, four native clients, one pair'}
    print('A/B results: '+str(out),flush=True)
    for label,extra in [('baseline',['--travel-disabled']),('travel',[])]:
        command=[sys.executable,str(ROOT/'tests/run_thread_load_e2e.py'),'--java-home',str(args.java_home.resolve()),'--threaded','--full-pack','--profile','--players','4','--heap-gb','20','--client-cpus','1',*extra]
        log=out/(label+'.log')
        row={'label':label,'command':command,'status':'running'};manifest['runs'].append(row)
        (out/'matrix.json').write_text(json.dumps(manifest,indent=2))
        print('Starting '+label,flush=True)
        with log.open('w',encoding='utf-8') as output:code=subprocess.call(command,stdout=output,stderr=subprocess.STDOUT)
        lines=log.read_text(encoding='utf-8',errors='replace').splitlines()
        row['lab']=next((line.split('Native load lab: ',1)[1] for line in lines if line.startswith('Native load lab: ')),None)
        row['exitCode']=code;row['status']='passed' if code==0 else 'failed'
        (out/'matrix.json').write_text(json.dumps(manifest,indent=2))
        print(label+': '+row['status']+' '+str(row['lab']),flush=True)
        if code:raise SystemExit(code)
if __name__=='__main__':main()
