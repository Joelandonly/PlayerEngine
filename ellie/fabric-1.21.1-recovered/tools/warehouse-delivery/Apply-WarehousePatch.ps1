[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$SourceRoot,[Parameter(Mandatory=$true)][string]$PackageRoot)
$ErrorActionPreference='Stop'
$manifest=Get-Content (Join-Path $PackageRoot 'manifest.json') -Raw | ConvertFrom-Json
$head=(& git -C $SourceRoot rev-parse HEAD | Out-String).Trim()
if($LASTEXITCODE -ne 0 -or $head -ne $manifest.baseline){throw 'Patch requires exact expected baseline'}
# Check every file before changing any file. Reruns accept only exact payload bytes.
foreach($entry in $manifest.files){
    $source=Join-Path (Join-Path $PackageRoot 'payload') $entry.path
    $target=Join-Path $SourceRoot $entry.path
    if((Get-FileHash $source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.after){throw "Payload integrity failure: $($entry.path)"}
    if(Test-Path $target){
        $hash=(Get-FileHash $target -Algorithm SHA256).Hash.ToLowerInvariant()
        if($hash -ne $entry.after -and $hash -ne $entry.before){throw "Unrecognized local edits; refusing overwrite: $($entry.path)"}
    } elseif($entry.before){throw "Missing baseline file: $($entry.path)"}
}
foreach($entry in $manifest.files){
    $source=Join-Path (Join-Path $PackageRoot 'payload') $entry.path
    $target=Join-Path $SourceRoot $entry.path
    if((Test-Path $target) -and (Get-FileHash $target -Algorithm SHA256).Hash.ToLowerInvariant() -eq $entry.after){continue}
    New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
    Copy-Item $source $target
}
Write-Host "Applied $($manifest.files.Count) verified source files idempotently. Original repository unchanged."
