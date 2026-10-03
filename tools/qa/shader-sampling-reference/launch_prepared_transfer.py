from pathlib import Path
import ctypes,json,os,subprocess,sys,time
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
out=Path(__file__).resolve().parent/'transfer-lab'
lab=Path(json.loads((out/('latest-'+sys.argv[1]+'.json')).read_text(encoding='utf-8'))['lab']).resolve()
assert out.resolve() in lab.parents
inputs=json.loads((lab/'inputs.json').read_text(encoding='utf-8'))
assert inputs['owner']=='task5-transfer-performance' and not (lab/'pid.json').exists()
session=ctypes.c_ulong();ctypes.windll.kernel32.ProcessIdToSessionId(os.getpid(),ctypes.byref(session))
assert session.value==2 and os.environ.get('COMPUTERNAME')=='JBC_FCRL'
jdk=Path(r'C:\Users\ranzh\Documents\Codex\terminal-integration-task6-20261001\tools\jdk\jdk-21.0.12.1+1')
env=dict(os.environ);env.pop('MUXI_TERMINAL_GAME_CREDENTIAL',None)
with (lab/'boot.log').open('w',encoding='utf-8') as log:
 proc=subprocess.Popen([str(jdk/'bin/java.exe'),'@'+str(lab/'launch.args')],cwd=lab,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 (lab/'pid.json').write_text(json.dumps({'pid':proc.pid}),encoding='utf-8');print(json.dumps({'launched':True,'pid':proc.pid,'lab':str(lab)}),flush=True)
 deadline=time.monotonic()+1500
 while proc.poll() is None:
  if time.monotonic()>deadline-60:(lab/'request-normal-close.json').write_text('{}')
  if time.monotonic()>deadline:raise SystemExit('Deadline; normal close requested, no force kill')
  time.sleep(1)
result=json.loads((lab/'transfer-qa-result.json').read_text(encoding='utf-8')) if (lab/'transfer-qa-result.json').exists() else {'success':False,'error':'No QA result'}
result.update({'exitCode':proc.returncode,'lab':str(lab)})
(lab/'run-summary.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps({'success':result.get('success'),'exitCode':proc.returncode,'samples':len(result.get('samples',[])),'lab':str(lab),'error':result.get('error')}),flush=True)
if not result.get('success'):raise SystemExit(1)
