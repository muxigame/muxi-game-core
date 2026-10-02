"""Preserve task23 sources and pause evidence only. Never build, test, launch, stop or push."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import re
import shutil
import sys

ROOT=Path(__file__).resolve().parent
DEST=ROOT/'task23-checkpoint'
DEST.mkdir(exist_ok=True)
copied=[]

def copy(source,relative):
 target=DEST/relative
 if source.resolve()==target.resolve():return
 target.parent.mkdir(parents=True,exist_ok=True)
 shutil.copy2(source,target)
 copied.append({'path':target.relative_to(DEST).as_posix(),'size':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})

for file in sorted(ROOT.glob('*.py')):copy(file,Path('workspace-scripts')/file.name)
for name in ('RUNBOOK.txt','DIMENSION-TRANSFER-DIAGNOSIS.txt','NPC-SLEEP-DIAGNOSIS.txt','TRANSFER-LATENCY-FOLLOWUP.txt','WORLD-ROLE-MIGRATION-PLAN.txt'):
 copy(ROOT/name,Path('notes')/name)
candidate=ROOT/'schematic-client-candidate'
for file in sorted(candidate.iterdir()):
 if file.is_file() and file.suffix in ('.py','.ps1'):copy(file,Path('projection')/file.name)
for folder in ('runtime-src','normal-close-agent'):
 for file in sorted((candidate/folder).rglob('*.java')):copy(file,Path('projection')/file.relative_to(candidate))
for name in ('client-additions.json','static-verification.json','download-receipt.json','source-context-receipt.json','overlay-receipt.json','existing-at-hits.json','HANDOFF.txt','HANDOFF-runtime-current.txt','HANDOFF-static-only-historical.txt','EXISTING-ENTRY-PARAMETERS.txt','ENTRY-COORDINATION-REQUEST.txt','runtime-inputs/formal-mods.json','runtime-inputs/optional-selection.json','runtime-build/build-receipt.json','client/config/litematica.json','client/schematics/task23-test-floor.litematic'):
 copy(candidate/name,Path('projection')/name)
for file in sorted((candidate/'redistribution/licenses').glob('*.txt')):copy(file,Path('projection')/file.relative_to(candidate))
copy(candidate/'redistribution/NOTICE.txt',Path('projection/redistribution/NOTICE.txt'))
for name in ('131-pause-normal-close.json','131-retry-thread-inspection.json','131-retry-generation-threads.json','retry-evidence-download.json','runtime-evidence-retry/exit.json','runtime-evidence-retry/run-inputs.json'):
 if (candidate/name).is_file():copy(candidate/name,Path('projection/evidence')/name)

status={
 'savedUtc':datetime.now(timezone.utc).isoformat(),'task':'task23','purpose':'Pause-period source checkpoint; not a tested/promotable release',
 'testingPaused':True,'newTestsBuildsOrProcessesStarted':False,'pushPerformed':False,
 'firstRun':{'pid':33816,'lab':r'C:\Users\ranzh\Documents\Codex\schematic-task23-20261002\runs\projection-20261002-044944-d9394440','result':'Stage1 failed, ghosts/actual/drawnChunks zero; normal saved exit observed, exit code unavailable'},
 'retry':{'pid':4204,'session':2,'startedUtc':'2026-10-02T05:46:17.700147Z','lab':r'C:\Users\ranzh\Documents\Codex\schematic-task23-20261002\runs\projection-20261002-053523-f39cfd49','lastObservedUtc':'2026-10-02T05:55:40.8316258Z','lastObservedAlive':True,'stage':'World initialization, server awaiting PasterDream lamp-shadow chunk; not WMI','normalExitRequested':True,'normalExitConfirmed':False,'latestUserReport':'Parent says user interrupted this test; no further process command issued and current PID was not rechecked'},
 'runtimeVerified':False,'promotable':False,'productionMutation':False,
 'resume':'After parent authorization on131, first verify old PID/active-run/normal-exit flag and preserve evidence. Do not repeat kill or launch. Diagnose initialization if still alive, then resume Task23 projection/UI/material/Iris-Sodium-YSM/normal-exit validation in own instance.',
 'rawEvidence':'Original logs/receipts/screenshots stay in task23 schematic-client-candidate and exact private131 runs; raw logs not committed to avoid credentials/player/production data.',
 'pendingExcluded':'Generated JAR/class/ZIP artifacts, official dependency binaries/source JARs, caches, backup worlds, production/player snapshots and raw logs excluded from source Git checkpoint.'
}
exit_file=candidate/'runtime-evidence-retry/exit.json'
if exit_file.exists():
 exit_result=json.loads(exit_file.read_text(encoding='utf-8-sig'))
 status['retry'].update({'processStillRunning':exit_result['processStillRunning'],'exitCode':exit_result.get('exitCode'),'finishedUtc':exit_result.get('finishedUtc'),'driverCompleted':exit_result.get('driverCompleted'),'normalStopRequested':exit_result.get('normalStopRequested'),'cleanExit':exit_result.get('cleanExit'),'exitReceiptSaved':True,'currentPidNotRecheckedAfterUserInterruption':True})
 status['retry']['latestUserReport']='User interrupted test. Launcher receipt confirms process exit0 at05:55:57.777219UTC, driver incomplete. No repeat kill, runtime test, or launch.'
(DEST/'PAUSED-CHECKPOINT.json').write_text(json.dumps(status,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
readme='''Task23 pause checkpoint — source only\n\nThis local repository has no remote and is not public. The sole publishing owner assigns an approved repository/remote and pushes once; this agent does not push or change main/dev/tags.\n\nProjection runtime validation remains incomplete. See PAUSED-CHECKPOINT.json before any continuation. No test, build, client launch or process termination is authorized by this snapshot. All commands stored here are preserved source, not instructions to execute now. Historical entry-registration proposal is unexecuted and superseded by the fixed shared-entry owner route.\n\nLayout: workspace-scripts preserves this task’s standalone source; projection preserves the QA driver/build/launch/inspection scripts, official hash/config/fixture metadata and LGPL/GPL/Apache notices. notes retains task-specific engineering handoffs. Existing portal-exact-core and transfer-probe repositories are preserved independently via Git bundles and a delivery manifest outside this repository.\n\nRuntime QA helpers, recovery agents and test data must not enter a production release. Dependencies and compiled outputs are deliberately excluded and are available in their original task folders or reproducible from the official hashes.\n\nThese source files preserve original008 paths for reproducibility. Execution remains paused; any path adaptation or migration to131 requires parent coordination after authorization.\n'''
(DEST/'README.txt').write_text(readme,encoding='utf-8')
(DEST/'.gitignore').write_text('__pycache__/\n*.pyc\n*.class\n*.jar\n*.zip\n*.bundle\n.env*\n',encoding='utf-8')
(DEST/'SOURCE-INVENTORY.json').write_text(json.dumps({'files':copied,'fileCount':len(copied),'totalCopiedBytes':sum(x['size'] for x in copied)},indent=2)+'\n',encoding='utf-8')

# No matched content is printed; scanner reports paths/rules only.
rules={
 'private-key':rb'-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----',
 'github-token':rb'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b',
 'aws-access-key':rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b',
 'jwt-token':rb'\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b',
 'literal-password':rb'(?i)(?:password|rcon_password|access_token|api_key|secret_key)\s*[:=]\s*[\x22\x27][^\x22\x27\r\n]{8,}[\x22\x27]',
 'credential-url':rb'https?://[^\s/\x22\x27]{2,}:[^\s/\x22\x27]{2,}@',
}
findings=[]
for base in (DEST,ROOT/'portal-exact-core'):
 for file in base.rglob('*'):
  if not file.is_file() or any(p in ('.git','build','__pycache__','portal-exact-core') for p in file.relative_to(base).parts):continue
  if file.suffix not in ('.py','.ps1','.java','.json','.txt','.md','.patch'):continue
  content=file.read_bytes()
  for rule,pattern in rules.items():
   if re.search(pattern,content):findings.append({'path':str(file.relative_to(ROOT)),'rule':rule})
report={'sourcesCopied':len(copied),'copiedBytes':sum(x['size'] for x in copied),'secretScanFindings':findings,'rawLogsExcluded':True,'functionalTestsRun':False,'compileRun':False}
(ROOT/'checkpoint-source-scan.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report))
if findings:raise SystemExit(2)
