# Reviewable proposal only. Parent approval/coordination is required before invoking this file.
$ErrorActionPreference='Stop'
$taskName='MuxiSchematicTask23QA'
$root='C:\Users\ranzh\Documents\Codex\schematic-task23-20261002'
$program='C:\Python38\pythonw.exe'
if(!(Test-Path -LiteralPath $program) -or !(Test-Path -LiteralPath (Join-Path $root 'start_visible_131.py'))){throw 'Reviewed entry files are missing.'}
$service=New-Object -ComObject 'Schedule.Service';$service.Connect();$folder=$service.GetFolder('\')
$existing=$null
try{$existing=$folder.GetTask($taskName)}catch{}
if($existing){throw 'The independent entry already exists; refuse to replace it.'}
$definition=$service.NewTask(0)
$definition.RegistrationInfo.Description='Task23 isolated 131 schematic verification. On demand, current desktop user, no global input or production/task14 changes.'
$definition.Principal.UserId='JBC-1'
$definition.Principal.LogonType=3
$definition.Principal.RunLevel=0
$definition.Settings.Enabled=$true
$definition.Settings.AllowDemandStart=$true
$definition.Settings.StartWhenAvailable=$false
$definition.Settings.DisallowStartIfOnBatteries=$false
$definition.Settings.StopIfGoingOnBatteries=$false
$definition.Settings.AllowHardTerminate=$false
$definition.Settings.MultipleInstances=2
$definition.Settings.ExecutionTimeLimit='PT0S'
$action=$definition.Actions.Create(0)
$action.Path=$program
$action.Arguments='-B "C:\Users\ranzh\Documents\Codex\schematic-task23-20261002\start_visible_131.py"'
$action.WorkingDirectory=$root
$registered=$folder.RegisterTaskDefinition($taskName,$definition,2,'JBC-1',$null,3,$null)
@{created=$registered.Name;started=$false;originalMuxiDesktopQAModified=$false} | ConvertTo-Json -Compress
