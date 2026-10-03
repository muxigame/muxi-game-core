from pathlib import Path
import json,hashlib,datetime
root=Path(__file__).resolve().parent.parent
lab=Path(json.loads((root/'transfer-lab/latest-shader-fix-on.json').read_text())['lab'])
def read(name):return json.loads((lab/name).read_text(encoding='utf-8'))
def constructs(s,before,after):
 def count(k):return s[k]['totals'].get('irisPipelineConstructed',{}).get('calls',0)
 return count(after)-count(before)
join=read('latency-join-result.json');routes=read('transfer-qa-result.json');run=read('run-summary.json')
assert join['success'] and join['normalLogout'] and len(join['samples'])==3
assert routes['success'] and routes['normalLogout'] and len(routes['samples'])==8 and run['exitCode']==0
counts=[]
for s in join['samples']:
 assert s['veilPropertyAbsent'] and s['veilDispatchAfter']['enabled'] and not s['veilDispatchAfter']['disabledAfterFailure']
 assert s['shaderState']['enabled'] and not s['shaderState']['fallback'] and s['serverObservedMovement']
 assert s['parsedPackCacheAfterLogout']=={'parsedPacks':0,'capturedPackRoot':False,'pendingLevel':False}
 counts.append(constructs(s,'shaderStagesBefore','shaderStagesAfter'))
assert counts==[1,2,1],counts
swap=join['samples'][2]['dimensionSwapAfter']
assert swap['reconnecting'] and swap['reusedCurrentPack']
visited=set();transfers=[]
for s in routes['samples']:
 assert s['veilDispatchAfter']['enabled'] and not s['veilDispatchAfter']['disabledAfterFailure']
 assert s['serverObservedMovement'] and s['shaderStateAfterObservation']['enabled'] and not s['shaderStateAfterObservation']['fallback']
 visited.add(s['target']);count=constructs(s,'nativeStagesBefore','nativeStagesAfter')
 if s['managedShaderTransition']:
  assert count==1,count
  snap=s['dimensionShaderSwap'];assert snap['dimension']==s['target']
  expected='CURRENT_EUPHORIA_PATCHES_DIMENSION_'+s['target'].replace('minecraft:','').replace(':','_').upper()
  assert snap['dimensionMacro']==expected
 else:assert count==0,count
 transfers.append({'index':s['index'],'source':s['source'],'target':s['target'],'managedShaderTransition':s['managedShaderTransition'],'pipelineConstructions':count,'visibleMs':s['visibleAndInputUnblockedMs'],'serverObservedMovement':True,'shaderFallback':False})
assert visited=={'minecraft:overworld','muxi_game_core:adventure','muxi_game_core:overworld'}
assert routes['shaderCacheAfterLogout']=={'parsedPacks':0,'capturedPackRoot':False,'pendingLevel':False}
candidate=json.loads((root/'binary-audit/shader-fix-candidate.json').read_text())
assert hashlib.sha256((lab/'mods/dev-core-baseline.jar').read_bytes()).hexdigest()==candidate['sha256']
args=(lab/'launch.args').read_text();assert 'muxi.veilShaderEventDispatch=' not in args
result={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'passed':True,'lab':str(lab),'candidateSha256':candidate['sha256'],'defaultVeilEnabledWithoutProperty':True,'binaryCacheEnabled':False,'loggingOptimizationEnabled':False,'joinPipelineConstructionCounts':counts,'joins':[{'index':s['index'],'clientLoginToVisibleMs':s['clientLoginToVisibleMs'],'swap':s['dimensionSwapAfter']} for s in join['samples']],'transfers':transfers,'logoutCachesClear':True,'normalExit':True,'exitCode':0,'excludedLegacyGames':[p.name for p in (lab/'excluded-legacy-games').glob('*.jar')],'limits':['Cold first reconnect still retains native FIRST_LOADED fallback; second reconnect is optimized.','Private offline world exercises native client/server login; production authentication/resource synchronization is not covered.','Dependency alignment and concurrent owners mean these timings are not a controlled before/after benchmark.','Game startup and local server boot are excluded from reported login metrics.']}
(root/'binary-audit/shader-fix-native-verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
