"""Read-only host metrics under the common local_mc_debug owner marker. No launcher/attach."""
from pathlib import Path
import argparse, ctypes, ctypes.wintypes as W, hashlib, json, math, os, re, sys, time, types
import psutil

WORKSPACE=Path(__file__).resolve().parents[5]
DEFAULT_PROJECT=WORKSPACE/'better-mc-remake'
BOOTSTRAP='cpw.mods.bootstraplauncher.BootstrapLauncher'
IDENTITY_FLAGS=('--launchTarget','--version','--versionType','--fml.neoForgeVersion','--fml.mcVersion','--fml.fmlVersion','--fml.neoFormVersion','--assetsDir')
class GuardError(RuntimeError): pass

def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def owned_lab(instance):
    # Execute only the installed common helper, without creating pycache beside it.
    # Its resources() process inventory is NEVER called.
    path=WORKSPACE/'better-mc-remake/scripts/local_mc_runtime.py'
    module=types.ModuleType('_shader_sidecar_common_runtime');module.__file__=str(path)
    exec(compile(path.read_text(encoding='utf-8-sig'),str(path),'exec'),module.__dict__)
    return module.owned_lab(instance)

def absolute(path):
    p=Path(path)
    if not p.is_absolute():raise GuardError('relative-identity-path')
    return p.resolve(strict=True)

def validate_identity(instance,project=DEFAULT_PROJECT,role='host'):
    if role!='host':raise GuardError('host-role-required')
    project=absolute(project)
    if WORKSPACE.resolve() not in project.parents:raise GuardError('project-outside-workspace')
    requested=absolute(instance)
    try:root,marker=owned_lab(requested)
    except (ValueError,KeyError,TypeError,OSError):raise GuardError('invalid-common-owner-marker') from None
    if root!=requested or absolute(marker['projectRoot'])!=project:raise GuardError('project-root-mismatch')
    if absolute(marker['instanceRoot'])!=root:raise GuardError('instance-root-mismatch')
    if (root/'local-mc-owner.json').resolve()!=root/'local-mc-owner.json':raise GuardError('redirected-owner-marker')
    run_id=marker.get('runId')
    if not isinstance(run_id,str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}',run_id):raise GuardError('invalid-run-id')
    if role not in marker['roles']:raise GuardError('role-not-declared')
    entry=marker.get('processes',{}).get(role)
    if not isinstance(entry,dict):raise GuardError('role-process-missing')
    role_root=(root/role).resolve(strict=True)
    if role_root!=root/role or absolute(entry.get('root',''))!=role_root:raise GuardError('role-root-mismatch')
    pid=entry.get('pid');created=entry.get('processCreateTime')
    if type(pid) is not int or pid<=0:raise GuardError('invalid-role-pid')
    if type(created) not in (int,float) or not math.isfinite(created) or created<=0:raise GuardError('missing-process-create-time')
    argfile=role_root/'launch.args'
    if not argfile.is_file() or argfile.resolve()!=argfile:raise GuardError('missing-or-redirected-argfile')
    identity={'project':project,'root':root,'roleRoot':role_root,'role':role,'runId':run_id,'pid':pid,'created':created,
              'argfile':argfile,'argfileSha256':hashlib.sha256(argfile.read_bytes()).hexdigest()}
    tokens=own_launch_tokens(argfile)
    if not launch_identity_matches(identity,tokens):raise GuardError('argfile-identity-mismatch')
    return identity

def own_launch_tokens(path):
    tokens=[]
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        line=line.strip()
        if not line:continue
        if line.startswith('"') and line.endswith('"'):
            token=line[1:-1]
            if '"' in token or chr(92) in token:raise GuardError('unsupported-launch-argument-encoding')
        else:
            if any(c.isspace() for c in line) or '"' in line:raise GuardError('unsupported-launch-argument-layout')
            token=line
        tokens.append(token)
    return tokens

def single_value(tokens,flag):
    if tokens.count(flag)!=1 or any(token.startswith(flag+'=') for token in tokens):raise GuardError('identity-field-missing-or-duplicate')
    index=tokens.index(flag)
    if index+1>=len(tokens):raise GuardError('identity-field-without-value')
    return tokens[index+1]

def property_value(tokens,key):
    prefix='-D'+key+'='
    if '-D'+key in tokens:raise GuardError('qa-property-missing-or-duplicate')
    values=[token[len(prefix):] for token in tokens if token.startswith(prefix)]
    if len(values)!=1:raise GuardError('qa-property-missing-or-duplicate')
    return values[0]

def launch_identity_matches(identity,tokens):
    try:
        if tokens.count(BOOTSTRAP)!=1 or any(token.startswith('@') for token in tokens):return False
        if absolute(property_value(tokens,'qa.local.root'))!=identity['root']:return False
        if property_value(tokens,'qa.local.runId')!=identity['runId']:return False
        if property_value(tokens,'qa.local.role')!=identity['role']:return False
        if absolute(single_value(tokens,'--gameDir'))!=identity['roleRoot']:return False
        if single_value(tokens,'--launchTarget') not in ('neoforgeclient','forgeclient'):return False
        return True
    except (GuardError,ValueError,OSError):return False

def command_matches(identity,command):
    if len(command)==2 and command[1].startswith('@'):
        try:return absolute(command[1][1:])==identity['argfile']
        except (GuardError,ValueError,OSError):return False
    actual=command[1:]
    if not launch_identity_matches(identity,actual):return False
    original=own_launch_tokens(identity['argfile'])
    try:
        for flag in IDENTITY_FLAGS:
            if flag in actual or flag in original:
                if single_value(actual,flag)!=single_value(original,flag):return False
        return True
    except GuardError:return False

def verify_process(identity):
    current=validate_identity(identity['root'],identity['project'],identity['role'])
    if current!=identity:raise GuardError('owner-or-launch-identity-changed')
    process=psutil.Process(identity['pid'])
    with process.oneshot():
        if process.create_time()!=identity['created']:raise GuardError('pid-reused-or-create-time-mismatch')
        if process.name().lower() not in ('java.exe','javaw.exe','java'):raise GuardError('not-java')
        if absolute(process.cwd())!=identity['roleRoot']:raise GuardError('cwd-not-owned-role')
        if not command_matches(identity,process.cmdline()):
            time.sleep(.05)
            retry=psutil.Process(identity['pid'])
            if retry.create_time()!=identity['created']:raise GuardError('pid-reused')
            if not command_matches(identity,retry.cmdline()):raise GuardError('launch-identity-mismatch')
        memory=process.memory_info()
    return process,memory

def evidence_directory(identity):
    output=identity['root']/'coordinator/shader-native/sidecar'/identity['role']
    if output.resolve()!=output or identity['root'] not in output.resolve().parents:raise GuardError('redirected-evidence-directory')
    return output

class ValueUnion(ctypes.Union):
    _fields_=[('longValue',W.LONG),('doubleValue',ctypes.c_double),('largeValue',ctypes.c_longlong),('ansiStringValue',ctypes.c_char_p),('wideStringValue',W.LPWSTR)]
class CounterValue(ctypes.Structure):
    _fields_=[('CStatus',W.DWORD),('value',ValueUnion)]
class CounterItem(ctypes.Structure):
    _fields_=[('szName',W.LPWSTR),('FmtValue',CounterValue)]

class OwnGpuCounters:
    """Wildcard is constrained to one verified PID; results remain separated by adapter LUID/physical index."""
    def __init__(self,pid):
        self.pid=pid;self.query=W.HANDLE();self.counters={};self.failures={};self.closed=False
        self.api=ctypes.WinDLL('pdh')
        self.api.PdhOpenQueryW.argtypes=[W.LPCWSTR,ctypes.c_size_t,ctypes.POINTER(W.HANDLE)]
        self.api.PdhAddEnglishCounterW.argtypes=[W.HANDLE,W.LPCWSTR,ctypes.c_size_t,ctypes.POINTER(W.HANDLE)]
        self.api.PdhCollectQueryData.argtypes=[W.HANDLE]
        self.api.PdhGetFormattedCounterArrayW.argtypes=[W.HANDLE,W.DWORD,ctypes.POINTER(W.DWORD),ctypes.POINTER(W.DWORD),ctypes.c_void_p]
        self.api.PdhCloseQuery.argtypes=[W.HANDLE]
        for name in ['PdhOpenQueryW','PdhAddEnglishCounterW','PdhCollectQueryData','PdhGetFormattedCounterArrayW','PdhCloseQuery']:
            getattr(self.api,name).restype=W.LONG
        code=self.api.PdhOpenQueryW(None,0,ctypes.byref(self.query))
        if code: raise OSError('PDH-open-failed')
        for metric,object_name,counter in [('dedicatedBytes','GPU Process Memory','Dedicated Usage'),('sharedBytes','GPU Process Memory','Shared Usage'),('engineUtilizationPercent','GPU Engine','Utilization Percentage')]:
            handle=W.HANDLE()
            code=self.api.PdhAddEnglishCounterW(self.query,fr'\{object_name}(pid_{pid}_*)\{counter}',0,ctypes.byref(handle))
            if code:self.failures[metric]=hex(code & 0xffffffff)
            else:self.counters[metric]=handle
    def sample(self):
        result={'status':'available','adapters':{},'engines':{},'errors':dict(self.failures)}
        status=self.api.PdhCollectQueryData(self.query)
        if status:result['collectionStatus']=hex(status & 0xffffffff)
        for metric,handle in self.counters.items():
            size=W.DWORD();count=W.DWORD()
            code=self.api.PdhGetFormattedCounterArrayW(handle,0x200,ctypes.byref(size),ctypes.byref(count),None)
            if (code & 0xffffffff)!=0x800007D2 or size.value==0:
                result['errors'][metric]=hex(code & 0xffffffff);continue
            storage=ctypes.create_string_buffer(size.value)
            code=self.api.PdhGetFormattedCounterArrayW(handle,0x200,ctypes.byref(size),ctypes.byref(count),storage)
            if code:result['errors'][metric]=hex(code & 0xffffffff);continue
            items=ctypes.cast(storage,ctypes.POINTER(CounterItem))
            for index in range(count.value):
                item=items[index];name=item.szName or ''
                if metric=='engineUtilizationPercent':
                    match=re.fullmatch(r'pid_(\d+)_luid_(0x[0-9a-f]+)_(0x[0-9a-f]+)_phys_(\d+)_eng_(\d+)_engtype_([A-Za-z0-9_ -]+)',name,re.I)
                    if not match or int(match[1])!=self.pid:
                        result['errors'][metric]='unexpected-instance-shape';continue
                    if item.FmtValue.CStatus not in (0,1):
                        result['errors'][metric]=hex(item.FmtValue.CStatus);continue
                    key=f'luid_{match[2]}_{match[3]}_phys_{match[4]}'
                    engine=f'eng_{match[5]}_engtype_{match[6]}'
                    values=result['engines'].setdefault(key,{})
                    if engine in values:
                        result['errors'][metric]='duplicate-instance-not-summed';continue
                    value=item.FmtValue.value.doubleValue
                    if value<0 or not value<float('inf'):
                        result['errors'][metric]='invalid-value';continue
                    values[engine]=value
                    continue
                match=re.fullmatch(r'pid_(\d+)_luid_(0x[0-9a-f]+)_(0x[0-9a-f]+)_phys_(\d+)(?:#\d+)?',name,re.I)
                if not match or int(match[1])!=self.pid:
                    result['errors']['instance']='unexpected-instance-shape';continue
                if item.FmtValue.CStatus not in (0,1):
                    result['errors'][metric]=hex(item.FmtValue.CStatus);continue
                key=f'luid_{match[2]}_{match[3]}_phys_{match[4]}'
                adapter=result['adapters'].setdefault(key,{})
                if metric in adapter:
                    result['errors']['duplicate']='duplicate-instance-not-summed';continue
                value=item.FmtValue.value.doubleValue
                if value<0 or not value<float('inf'):
                    result['errors'][metric]='invalid-value';continue
                adapter[metric]=int(value)
        if not result['adapters'] and not result['engines']:result['status']='unavailable-or-no-instance'
        elif result['errors']:result['status']='partial'
        return result
    def close(self):
        if not self.closed:self.api.PdhCloseQuery(self.query);self.closed=True

def cpu_snapshot(process):
    result={}
    try:
        own=process.cpu_times()
        result['process']={'status':'available','userSeconds':own.user,'systemSeconds':own.system}
    except (psutil.Error,OSError) as error:
        result['process']={'status':'unavailable','failureClass':type(error).__name__}
    try:
        system=psutil.cpu_times()
        # On Windows interrupt/dpc are already included in system; never double-count.
        result['system']={'status':'available','userSeconds':system.user,'systemSeconds':system.system,
                          'busySeconds':system.user+system.system,'idleSeconds':system.idle,
                          'logicalProcessors':psutil.cpu_count()}
    except (psutil.Error,OSError) as error:
        result['system']={'status':'unavailable','failureClass':type(error).__name__}
    return result

def confirmed_process_absent(pid,expected_start):
    """AccessDenied alone never means exit. Require NoSuchProcess plus PID absence.

    If an accessible replacement exists, explicitly reject PID reuse. A live or
    unreadable process remains a failure; no other process is sampled here.
    """
    try:
        current=psutil.Process(pid).create_time()
    except psutil.NoSuchProcess:
        return not psutil.pid_exists(pid)
    except psutil.AccessDenied:
        return False
    if current!=expected_start:raise GuardError('pid-reused')
    return False

def sample_once(identity,gpu,phase):
    process,memory=verify_process(identity);cpu=cpu_snapshot(process)
    if not process.is_running():
        if confirmed_process_absent(identity['pid'],identity['created']):raise psutil.NoSuchProcess(identity['pid'])
        raise GuardError('process-identity-changed-during-sample')
    if process.create_time()!=identity['created']:raise GuardError('pid-reused')
    return {'phase':phase,'epochMs':time.time_ns()//1_000_000,'monotonicNs':time.monotonic_ns(),
            'runId':identity['runId'],'role':identity['role'],'pid':identity['pid'],'cpu':cpu,
            'workingSetBytes':memory.rss,'privateBytes':getattr(memory,'private',None),
            'gpu':gpu.sample() if gpu else {'status':'pdh-initialization-failed'}}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--instance-root',required=True,type=Path)
    parser.add_argument('--project-root',type=Path,default=DEFAULT_PROJECT)
    parser.add_argument('--role',choices=['host'],required=True)
    parser.add_argument('--duration',type=float,default=900);parser.add_argument('--interval',type=float,default=2)
    parser.add_argument('--phase',default='lifecycle');parser.add_argument('--once',action='store_true');parser.add_argument('--verify-only',action='store_true')
    options=parser.parse_args()
    if not 1<=options.duration<=3600 or not 1<=options.interval<=60 or not re.fullmatch(r'[A-Za-z0-9_-]{1,64}',options.phase):raise GuardError('invalid-bounds')
    identity=validate_identity(options.instance_root,options.project_root,options.role);verify_process(identity)
    if options.verify_only:print(json.dumps({'verified':True,'runId':identity['runId'],'role':identity['role'],'pid':identity['pid'],'readOnly':True}));return
    output_dir=evidence_directory(identity);output_dir.mkdir(parents=True,exist_ok=True)
    if evidence_directory(identity)!=output_dir:raise GuardError('evidence-path-changed')
    output=output_dir/('os-'+options.phase+'-'+str(time.time_ns())+'.jsonl')
    gpu=None;count=0;deadline=time.monotonic()+options.duration
    try:gpu=OwnGpuCounters(identity['pid'])
    except (OSError,AttributeError):pass
    try:
        with output.open('x',encoding='utf-8') as stream:
            while time.monotonic()<deadline:
                try:row=sample_once(identity,gpu,options.phase)
                except (psutil.NoSuchProcess,psutil.ZombieProcess,psutil.AccessDenied):
                    if not confirmed_process_absent(identity['pid'],identity['created']):raise
                    stream.write(json.dumps({'status':'process-ended','runId':identity['runId'],'role':identity['role'],'pid':identity['pid'],'epochMs':time.time_ns()//1_000_000})+'\n');break
                stream.write(json.dumps(row,separators=(',',':'))+'\n');stream.flush();count+=1
                if options.once:break
                time.sleep(min(options.interval,max(0,deadline-time.monotonic())))
    finally:
        if gpu:gpu.close()
    print(json.dumps({'sampleCount':count,'artifact':str(output),'runId':identity['runId'],'role':identity['role'],'minecraftStarted':False}))

if __name__=='__main__':
    try:main()
    except (GuardError,psutil.Error,OSError,ValueError,KeyError,TypeError) as error:
        print(json.dumps({'success':False,'failureClass':type(error).__name__,'reason':str(error) if isinstance(error,GuardError) else 'read-or-parse-failed'}));raise SystemExit(2)
