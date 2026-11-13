[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$SourceRoot,[Parameter(Mandatory=$true)][string]$PackageRoot,[Parameter(Mandatory=$true)][string]$OutputRoot)
$ErrorActionPreference='Stop'
$classes=Join-Path $OutputRoot 'logic-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources=@(Get-ChildItem (Join-Path $PackageRoot 'fixtures') -Filter '*.java' -Recurse | ForEach-Object FullName)
$base=Join-Path $SourceRoot 'ellie/fabric-1.21.1-recovered/common/src/main/java/com/player2/playerengine'
foreach($relative in @('warehouse/WarehouseLayout.java','warehouse/WarehouseLedger.java','warehouse/WarehousePolicy.java','warehouse/WarehouseManager.java','agentic/AutoInventoryCleanup.java','agentic/MaterialReservationService.java','tasks/container/BoundedContainerTransferTask.java','tasks/base/TrackedTaskOutcomeProvider.java','warehouse/WarehouseOrganizeTask.java','warehouse/WarehouseBuildTask.java')){$sources+=Join-Path $base $relative}
$argumentFile=Join-Path $OutputRoot 'logic-javac-args.txt'
$arguments=@('--release','21','-d',('"'+$classes.Replace('\','/')+'"'))+@($sources | ForEach-Object {'"'+$_.Replace('\','/')+'"'})
[System.IO.File]::WriteAllLines($argumentFile,$arguments,(New-Object System.Text.UTF8Encoding($false)))
$previous=$ErrorActionPreference
try {
    $ErrorActionPreference='Continue'
    & javac ('@'+$argumentFile) 2>&1 | Tee-Object -FilePath (Join-Path $OutputRoot 'logic-compile.log') | Out-Host
    $compileResult=$LASTEXITCODE
} finally {$ErrorActionPreference=$previous}
if($compileResult -ne 0){throw "Logic fixture compilation failed ($compileResult)"}
try {
    $ErrorActionPreference='Continue'
    & java -ea -cp $classes warehousefixture.WarehouseTests 2>&1 | Tee-Object -FilePath (Join-Path $OutputRoot 'logic-tests.log') | Out-Host
    $testResult=$LASTEXITCODE
} finally {$ErrorActionPreference=$previous}
if($testResult -ne 0){throw "Logic fixture tests failed ($testResult)"}
Write-Host 'In-memory fixture checks passed. Fabric build and gameplay are separate validation gates.'
