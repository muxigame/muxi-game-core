from pathlib import Path
import json
root=Path(__file__).resolve().parent.parent
lab=Path(json.loads((root/'transfer-lab/latest-shader-fix-on.json').read_text())['lab'])
out={'lab':str(lab),'joins':[],'routes':[]}
def load(name):
 p=lab/name
 return json.loads(p.read_text(encoding='utf-8')) if p.exists() else {}
for s in load('latency-join-result.json').get('samples',[]):
 before=s.get('shaderStagesBefore',{}).get('totals',{}).get('irisPipelineConstructed',{}).get('calls',0)
 after=s.get('shaderStagesAfter',{}).get('totals',{}).get('irisPipelineConstructed',{}).get('calls',0)
 out['joins'].append({k:s.get(k) for k in ['index','joinKind','clientLoginToVisibleMs','serverObservedMovement','shaderState','dimensionSwapAfter','veilPropertyAbsent','veilDispatchAfter','parsedPackCacheAfterLogout']}|{'pipelineConstructions':after-before})
d=load('transfer-qa-result.json')
out['routeSuccess']=d.get('success');out['error']=d.get('error')
for s in d.get('samples',[]):
 out['routes'].append({k:s.get(k) for k in ['index','target','dimension','visibleAndInputUnblockedMs','serverObservedMovement','shaderState','dimensionShaderSwap','parsedPackCache']})
out['exitCode']=load('run-summary.json').get('exitCode')
print(json.dumps(out,ensure_ascii=False,indent=2))
