$ErrorActionPreference='Stop';$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8
$service=New-Object -ComObject 'Schedule.Service';$service.Connect()
$entries=@()
foreach($task in $service.GetFolder('\').GetTasks(0)){
 if($task.Name -notlike 'Muxi*'){continue}
 $definition=$task.Definition;$actions=@()
 foreach($action in $definition.Actions){
  $arguments=[string]$action.Arguments
  $script=$null
  if($arguments -match '(?i)-File\s+"([^"]+)"'){$script=$Matches[1]}
  elseif($arguments -match '(?i)-File\s+(\S+)'){$script=$Matches[1]}
  $actions+=@{program=$action.Path;fileScript=$script;supportsArgVariables=($arguments.IndexOf('$'+'(Arg') -ge 0)}
 }
 $entries+=@{name=$task.Name;state=$task.State;actions=$actions;user=$definition.Principal.UserId;logonType=$definition.Principal.LogonType;runLevel=$definition.Principal.RunLevel;multipleInstancesPolicy=$definition.Settings.MultipleInstances}
}
@{readOnly=$true;entries=$entries} | ConvertTo-Json -Depth 6 -Compress
