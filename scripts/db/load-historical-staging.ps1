param(
    [Parameter(Mandatory=$true)][ValidateSet('plan','provision','load')][string]$Mode,
    [Parameter(Mandatory=$true)][string]$SourceEnv,
    [Parameter(Mandatory=$true)][string]$TargetEnv,
    [Parameter(Mandatory=$true)][string]$WorkDir,
    [Parameter(Mandatory=$true)][string]$Batch,
    [string]$SourceDatabase,
    [string]$ConfirmTarget,
    [string]$ApiUrl,
    [string]$Psql = 'psql'
)
$ErrorActionPreference = 'Stop'
$loaderArguments = @('--mode',$Mode,'--source-env',$SourceEnv,'--target-env',$TargetEnv,
    '--work-dir',$WorkDir,'--batch',$Batch,'--psql',$Psql)
if ($SourceDatabase) { $loaderArguments += @('--source-db',$SourceDatabase) }
if ($ConfirmTarget) { $loaderArguments += @('--confirm-target',$ConfirmTarget) }
if ($ApiUrl) { $loaderArguments += @('--api-url',$ApiUrl) }
& node (Join-Path $PSScriptRoot 'historical-import.js') @loaderArguments
exit $LASTEXITCODE
