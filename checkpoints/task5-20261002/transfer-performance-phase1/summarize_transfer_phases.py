from pathlib import Path
import json,statistics
out=Path(__file__).resolve().parent/'transfer-lab';summary={}
for case in ['on-baseline-control','on-fix-extended']:
 lab=Path(json.loads((out/('latest-'+case+'.json')).read_text())['lab']);a=json.loads((lab/'transfer-analysis.json').read_text());rows=a['comparisonSamples'];warm=rows[1:]
 summary[case]={'visibleWarm':a['warmSummary']['visibleAndInputUnblockedMs'],'coldVisibleMs':rows[0]['visibleAndInputUnblockedMs'],'pipelineSpans':[], 'reloadSpans':[], 'perTransfer':[]}
 for r in rows:
  probe=r.get('clientProbe',{});pipeline=probe.get('iris_pipeline',{}).get('completed_ms',[]);reloads=probe.get('iris_reload',{}).get('completed_ms',[])
  summary[case]['perTransfer'].append({'index':r['index'],'visibleMs':r['visibleAndInputUnblockedMs'],'irisPipelineMs':pipeline,'irisReloadMs':reloads,'sodiumResetMs':probe.get('sodium_reset',{}).get('completed_ms',[]),'jitCompilationMs':r.get('jitCompilationDeltaMs'),'gcCollectionMs':r.get('gcCollectionDeltaMs')})
 (lab/'phase-summary.json').write_text(json.dumps(summary[case],indent=2),encoding='utf-8')
summary['visibleWarmReductionPercent']=100*(1-summary['on-fix-extended']['visibleWarm']['medianMs']/summary['on-baseline-control']['visibleWarm']['medianMs'])
(out/'matched-first-fix-summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8');print(json.dumps(summary,indent=2))
