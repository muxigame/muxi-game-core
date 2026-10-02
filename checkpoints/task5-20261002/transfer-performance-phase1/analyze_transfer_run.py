from pathlib import Path
import argparse,datetime,importlib.util,json,math,re,statistics,sys
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
old=Path(r'C:\Users\ranzh\Documents\Codex\2026-10-02\task\dev-sync-20261002\muxi-game-core\checkpoints\task23-20261002\transfer-latency\analyze_logs.py')
spec=importlib.util.spec_from_file_location('oldanalysis',old);a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)
p=argparse.ArgumentParser();p.add_argument('name');args=p.parse_args()
out=Path(__file__).resolve().parent/'transfer-lab'
lab=Path(json.loads((out/('latest-'+args.name+'.json')).read_text(encoding='utf-8'))['lab'])
result_path=lab/'run-summary.json'
if not result_path.exists():result_path=lab/'transfer-qa-result.json'
result=json.loads(result_path.read_text(encoding='utf-8'))
events=[]
for line in (lab/'boot.log').read_text(encoding='utf-8',errors='replace').splitlines():
 parsed=a.extract([line])
 stamp=re.match(r'\[(\d\d:\d\d:\d\d)(?:\.\d+)?\]',line)
 for record in parsed:
  if stamp:
   record['_logUtcSeconds']=datetime.datetime.fromisoformat('2026-10-02T'+stamp[1]+'+08:00').timestamp()
  events.append(record)
groups=a.analyze(events)
order={r['transfer']:r['mono_ms'] for r in events if r['event'] in ('request','respawn_receive')}
groups.sort(key=lambda r:order.get(r['transfer'],float('inf')))
cursor={'client':0,'server':0};matched=[]
for sample in result.get('samples',[]):
 row=dict(sample)
 for side in ('client','server'):
  selected=[g for g in groups if g['side']==side]
  for i in range(cursor[side],len(selected)):
   g=selected[i];r=next((r for r in events if r['transfer']==g['transfer'] and r['side']==side),{})
   if r.get('source')==sample['source'] and g['target']==sample['target']:
    if sample.get('requestedUtc'):
     stamp=re.sub(r'(\.\d{6})\d+',r'\1',sample['requestedUtc']).replace('Z','+00:00')
     start=datetime.datetime.fromisoformat(stamp).timestamp()
     end=start+(sample['movementResponseMs']+sample['postTransferObservationMs'])/1000
     bounded=[r for r in events if r['side']==side and r['transfer']==g['transfer'] and start-1<=r.get('_logUtcSeconds',start)<=end+1]
     bounded_groups=a.analyze(bounded)
     if bounded_groups:g=bounded_groups[0]
    row[side+'Probe']=g;cursor[side]=i+1;break
 matched.append(row)
def metric(rows,key):
 values=[r[key] for r in rows if key in r]
 return {'medianMs':statistics.median(values),'p95NearestRankMs':sorted(values)[math.ceil(.95*len(values))-1] if len(values)>1 else None,'minMs':min(values),'maxMs':max(values),'n':len(values)} if values else None
comparison=[r for r in matched if r['index']<8]
warm=[r for r in comparison if not r['coldFirstTargetInProcess']]
summary={'success':result.get('success'),'lab':str(lab),'coldSamples':[r for r in matched if r['coldFirstTargetInProcess']],
         'warmSummary':{key:metric(warm,key) for key in ['visibleAndInputUnblockedMs','movementResponseMs','firstCompiledWorldFrameMs','worstFrameGapMs','serverQueueMs','serverChangeDimensionMs']},
         'samples':matched,'comparisonSamples':comparison,'validationSamples':[r for r in matched if r['index']>=8],'unmatchedProbeGroups':groups,'notes':['Client cold means first target in this process; driver/shared caches were not cleared.','Cold n=1 is an observation, not an estimated population median/P95. Hot P95 uses nearest rank; n=7 is small.','Synthetic platforms, pre-generated landing chunk, actual unchanged loading/sync and shaders.','Server/client probe spans are calculated within each process only; do not add nested Iris/Sodium timings.','Only indices 0..7 enter the matched timing comparison; later samples validate explicit reload and third Core dimension.','Phase events are bounded to each actual QA sample; explicit reload after sample 7 and teardown are excluded using native log UTC+8 timestamps with one-second precision allowance.']}
(lab/'transfer-analysis.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in summary.items() if k not in ['samples','unmatchedProbeGroups','comparisonSamples','validationSamples']},ensure_ascii=False,indent=2))
