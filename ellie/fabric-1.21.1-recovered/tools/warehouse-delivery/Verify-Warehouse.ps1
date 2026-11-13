[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$SourceRoot,[Parameter(Mandatory=$true)][string]$PackageRoot,[string]$JarPath)
$ErrorActionPreference='Stop'
$manifest=Get-Content (Join-Path $PackageRoot 'manifest.json') -Raw | ConvertFrom-Json
foreach($entry in $manifest.files){
    $target=Join-Path $SourceRoot $entry.path
    if(-not (Test-Path $target) -or (Get-FileHash $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.after){throw "Patched-source verification failed: $($entry.path)"}
}
$properties=Get-Content (Join-Path $SourceRoot 'ellie/fabric-1.21.1-recovered/gradle.properties') -Raw
if($properties -notmatch '(?m)^minecraft_version=1\.21\.1\s*$'){throw 'Wrong Minecraft source target'}
if($JarPath){
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        foreach($entry in @('fabric.mod.json','com/player2/playerengine/warehouse/WarehouseManager.class','com/player2/playerengine/warehouse/WarehouseBuildTask.class','com/player2/playerengine/warehouse/WarehouseOrganizeTask.class','com/player2/playerengine/commands/EllieStorageCommand.class','com/player2/playerengine/agentic/AutoInventoryCleanup.class','com/player2/playerengine/tasks/construction/WaterFillTask.class','com/player2/playerengine/safezone/SafeZoneService.class')){
            if(-not $zip.GetEntry($entry)){throw "JAR missing required entry: $entry"}
        }
        $reader=New-Object System.IO.StreamReader($zip.GetEntry('fabric.mod.json').Open())
        try {$metadata=$reader.ReadToEnd() | ConvertFrom-Json} finally {$reader.Dispose()}
        if($metadata.id -ne 'playerengine' -or $metadata.version -notmatch '^1\.21\.1-'){throw 'Unexpected Fabric mod identity/version'}
    } finally {$zip.Dispose()}
    Write-Host ('Verified remapped Fabric JAR: '+$JarPath)
}
Write-Host 'Source hashes and Minecraft 1.21.1 target verified. This is not a gameplay test.'
