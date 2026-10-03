"""No live process reads: common-marker fixtures plus mocked psutil processes."""
from pathlib import Path
from unittest.mock import patch
from types import SimpleNamespace
import contextlib, hashlib, importlib.util, json, tempfile, unittest

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('shader_sidecar',HERE/'sample_owned_memory.py')
s=importlib.util.module_from_spec(spec);spec.loader.exec_module(s)

class GuardTests(unittest.TestCase):
 def setUp(self):
  (HERE/'build').mkdir(exist_ok=True)
  self.temp=tempfile.TemporaryDirectory(dir=HERE/'build',prefix='guard-')
  self.project=Path(self.temp.name)/'project';self.root=self.project/'build/local-mc-debug/fixture';self.host=self.root/'host';self.host.mkdir(parents=True)
  self.marker={'schema':1,'runId':'fixture-run','projectRoot':str(self.project),'instanceRoot':str(self.root),'roles':['server','host'],'processes':{'host':{'pid':1234,'root':str(self.host),'startedAt':100,'processCreateTime':100.25}}}
  self.tokens=['-Dqa.local.root='+str(self.root),'-Dqa.local.runId=fixture-run','-Dqa.local.role=host',s.BOOTSTRAP,'--gameDir',str(self.host),'--launchTarget','neoforgeclient','--version','fixture','--fml.neoForgeVersion','fixture-loader','--fml.mcVersion','fixture-mc']
  self.write_marker();self.write_args()
  self.identity=s.validate_identity(self.root,self.project,'host')
  owner=self
  class Process:
   def __init__(self,pid):owner.assertEqual(pid,1234)
   def oneshot(self):return contextlib.nullcontext()
   def create_time(self):return 100.25
   def name(self):return 'java.exe'
   def cwd(self):return str(owner.host)
   def cmdline(self):return ['java.exe','@'+str(owner.host/'launch.args')]
   def memory_info(self):return SimpleNamespace(rss=1000,private=2000)
   def cpu_times(self):return SimpleNamespace(user=2,system=3)
   def is_running(self):return True
  self.Process=Process
  self.mock=patch.object(s.psutil,'Process',Process);self.mock.start()
 def tearDown(self):self.mock.stop();self.temp.cleanup()
 def write_marker(self):(self.root/'local-mc-owner.json').write_text(json.dumps(self.marker),encoding='utf-8')
 def write_args(self):(self.host/'launch.args').write_text('\n'.join('"'+x.replace('\\','/')+'"' for x in self.tokens),encoding='utf-8')
 def validate(self):return s.validate_identity(self.root,self.project,'host')
 def reject_validate(self):
  with self.assertRaises((s.GuardError,ValueError)):self.validate()
 def test_common_marker_and_argfile(self):s.verify_process(self.identity)
 def test_expanded(self):
  with patch.object(self.Process,'cmdline',lambda p:['java.exe']+[x.replace('\\','/') for x in self.tokens]):s.verify_process(self.identity)
 def test_common_forgeclient_argfile(self):
  self.tokens[self.tokens.index('--launchTarget')+1]='forgeclient';self.write_args();identity=self.validate();s.verify_process(identity)
 def test_common_forgeclient_expanded(self):
  self.tokens[self.tokens.index('--launchTarget')+1]='forgeclient';self.write_args();identity=self.validate()
  with patch.object(self.Process,'cmdline',lambda p:['java.exe']+[x.replace(chr(92),'/') for x in self.tokens]):s.verify_process(identity)
 def test_expanded_other_client_alias_not_equal(self):
  tokens=[x.replace(chr(92),'/') for x in self.tokens];tokens[tokens.index('--launchTarget')+1]='forgeclient';self.assertFalse(s.command_matches(self.identity,['java.exe']+tokens))
 def test_server_target_rejected(self):
  self.tokens[self.tokens.index('--launchTarget')+1]='forgeserver';self.write_args();self.reject_validate()
 def test_unknown_target_rejected(self):
  self.tokens[self.tokens.index('--launchTarget')+1]='unknownclient';self.write_args();self.reject_validate()
 def test_missing_create_time(self):del self.marker['processes']['host']['processCreateTime'];self.write_marker();self.reject_validate()
 def test_wrong_create_time(self):
  with patch.object(self.Process,'create_time',lambda p:101):
   with self.assertRaises(s.GuardError):s.verify_process(self.identity)
 def test_pid_owner_changes(self):
  self.marker['processes']['host']['pid']=9999;self.write_marker()
  with self.assertRaises(s.GuardError):s.verify_process(self.identity)
 def test_project_mismatch(self):self.marker['projectRoot']=str(self.root);self.write_marker();self.reject_validate()
 def test_instance_mismatch(self):self.marker['instanceRoot']=str(self.host);self.write_marker();self.reject_validate()
 def test_role_root_mismatch(self):self.marker['processes']['host']['root']=str(self.root);self.write_marker();self.reject_validate()
 def test_undeclared_host(self):self.marker['roles']=['server'];self.write_marker();self.reject_validate()
 def test_wrong_role_rejected(self):
  with self.assertRaises(s.GuardError):s.validate_identity(self.root,self.project,'guest')
 def test_runid_mismatch(self):self.marker['runId']='different';self.write_marker();self.reject_validate()
 def test_wrong_qa_root(self):self.tokens[0]='-Dqa.local.root='+str(self.host);self.write_args();self.reject_validate()
 def test_duplicate_property(self):self.tokens.append('-Dqa.local.role=host');self.write_args();self.reject_validate()
 def test_bare_property_override(self):self.tokens.append('-Dqa.local.role');self.write_args();self.reject_validate()
 def test_equals_game_dir_override(self):self.tokens.append('--gameDir='+str(self.root));self.write_args();self.reject_validate()
 def test_wrong_game_dir(self):self.tokens[self.tokens.index('--gameDir')+1]=str(self.root);self.write_args();self.reject_validate()
 def test_missing_bootstrap(self):self.tokens.remove(s.BOOTSTRAP);self.write_args();self.reject_validate()
 def test_argfile_changed_after_authentication(self):
  self.tokens.extend(['--versionType','fixture']);self.write_args()
  with self.assertRaises(s.GuardError):s.verify_process(self.identity)
 def test_non_java(self):
  with patch.object(self.Process,'name',lambda p:'other.exe'):
   with self.assertRaises(s.GuardError):s.verify_process(self.identity)
 def test_wrong_cwd(self):
  with patch.object(self.Process,'cwd',lambda p:str(self.root)):
   with self.assertRaises(s.GuardError):s.verify_process(self.identity)
 def test_wrong_launch_path(self):
  with patch.object(self.Process,'cmdline',lambda p:['java.exe','@'+str(self.root/'launch.args')]):
   with self.assertRaises(s.GuardError):s.verify_process(self.identity)
 def test_expanded_wrong_role(self):
  tokens=[x.replace('qa.local.role=host','qa.local.role=guest').replace('\\','/') for x in self.tokens]
  self.assertFalse(s.command_matches(self.identity,['java.exe']+tokens))
 def test_expanded_wrong_version(self):
  tokens=[x.replace('\\','/') for x in self.tokens];tokens[tokens.index('--version')+1]='wrong';self.assertFalse(s.command_matches(self.identity,['java.exe']+tokens))
 def test_argfile_override_rejected(self):self.assertFalse(s.command_matches(self.identity,['java.exe','@'+str(self.host/'launch.args'),'-Dqa.local.role=guest']))
 def test_output_is_business_sidecar_only(self):self.assertEqual(s.evidence_directory(self.identity),self.root/'coordinator/shader-native/sidecar/host')
 def test_live_denied_not_ended(self):
  with patch.object(s.psutil,'Process',side_effect=s.psutil.AccessDenied(1234)):self.assertFalse(s.confirmed_process_absent(1234,100.25))
 def test_ended_requires_absence(self):
  with patch.object(s.psutil,'Process',side_effect=s.psutil.NoSuchProcess(1234)),patch.object(s.psutil,'pid_exists',return_value=False):self.assertTrue(s.confirmed_process_absent(1234,100.25))
 def test_reappeared_pid_not_ended(self):
  with patch.object(s.psutil,'Process',side_effect=s.psutil.NoSuchProcess(1234)),patch.object(s.psutil,'pid_exists',return_value=True):self.assertFalse(s.confirmed_process_absent(1234,100.25))
 def test_reused_pid_not_ended(self):
  with patch.object(self.Process,'create_time',lambda p:200):
   with self.assertRaises(s.GuardError):s.confirmed_process_absent(1234,100.25)
 def test_cpu_no_dpc_double_count(self):
  with patch.object(s.psutil,'cpu_times',return_value=SimpleNamespace(user=10,system=20,idle=30,interrupt=7,dpc=8)),patch.object(s.psutil,'cpu_count',return_value=8):
   data=s.cpu_snapshot(self.Process(1234));self.assertEqual(data['system']['busySeconds'],30);self.assertEqual(data['process']['userSeconds'],2)
 def test_cpu_denied_is_missing(self):
  def denied(p):raise s.psutil.AccessDenied(1234)
  with patch.object(self.Process,'cpu_times',denied),patch.object(s.psutil,'cpu_times',side_effect=OSError()):
   data=s.cpu_snapshot(self.Process(1234));self.assertEqual(data['process']['status'],'unavailable');self.assertNotIn('userSeconds',data['process']);self.assertEqual(data['system']['status'],'unavailable')

if __name__=='__main__':
 suite=unittest.defaultTestLoader.loadTestsFromTestCase(GuardTests)
 result=unittest.TextTestRunner(verbosity=1).run(suite)
 receipt={'testsRun':result.testsRun,'passed':result.wasSuccessful(),'liveProcessSampled':False,'minecraftStarted':False,'commonRuntimeModified':False,'samplerSha256':hashlib.sha256((HERE/'sample_owned_memory.py').read_bytes()).hexdigest()}
 (HERE/'build/test-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
 raise SystemExit(0 if result.wasSuccessful() else 1)
