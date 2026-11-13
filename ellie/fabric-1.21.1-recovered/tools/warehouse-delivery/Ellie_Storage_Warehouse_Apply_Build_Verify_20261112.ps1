[CmdletBinding()]
param([string]$Repo=$env:ELLIE_REPO)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$Baseline='5c58ea10e288cb6e3e67079cfbc3d1332b6f9bab'
$Branch='feature/ellie-waterfill-access-and-reach-20261111'
$Gradle='C:\EllieSrc\.phase3-tools\gradle-8.14\bin\gradle.bat'
$stamp=(Get-Date -Format 'yyyyMMdd_HHmmss')+'_'+[Guid]::NewGuid().ToString('N').Substring(0,6)
$downloads=Join-Path ([Environment]::GetFolderPath('UserProfile')) 'Downloads'
try {
    $known=(Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders').'{374DE290-123F-4565-9164-39C4925E467B}'
    if($known){$downloads=[Environment]::ExpandEnvironmentVariables($known)}
} catch { }
$Output=Join-Path $downloads ('Ellie_Storage_Warehouse_'+$stamp)
$Logs=Join-Path $Output 'logs'
New-Item -ItemType Directory -Force -Path $Logs | Out-Null
Start-Transcript -Path (Join-Path $Logs 'launcher.log') | Out-Null
function Invoke-Checked([string]$Program,[string[]]$Arguments,[string]$Log) {
    $previous=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        & $Program @Arguments 2>&1 | Tee-Object -FilePath $Log -Append | Out-Host
        $result=$LASTEXITCODE
    } finally {$ErrorActionPreference=$previous}
    if($result -ne 0){throw "$Program failed with exit code $result; see $Log"}
}
try {
    if(-not $Repo){
        foreach($candidate in @('C:\EllieSrc\PlayerEngine','C:\EllieSrc')){
            if(Test-Path (Join-Path $candidate '.git')){$Repo=$candidate;break}
        }
    }
    if(-not $Repo){
        Add-Type -AssemblyName System.Windows.Forms
        $dialog=New-Object System.Windows.Forms.FolderBrowserDialog
        $dialog.Description='Select your existing Joelandonly/PlayerEngine Git repository (the original will not be patched)'
        if($dialog.ShowDialog() -ne 'OK'){throw 'Repository selection cancelled'}
        $Repo=$dialog.SelectedPath
    }
    $Repo=(Resolve-Path $Repo).Path
    $remote=& git -C $Repo config --get remote.origin.url
    if($LASTEXITCODE -ne 0 -or $remote -notmatch 'github\.com[/:]Joelandonly/PlayerEngine(?:\.git)?$'){throw 'Expected Joelandonly/PlayerEngine origin'}
    if(-not (Test-Path $Gradle)){throw "Required Gradle launcher missing: $Gradle"}
    $javaCandidates=@()
    if($env:JAVA_HOME){$javaCandidates+=Join-Path $env:JAVA_HOME 'bin\java.exe'}
    $onPath=Get-Command java.exe -ErrorAction SilentlyContinue
    if($onPath){$javaCandidates+=$onPath.Source}
    if(Test-Path 'C:\EllieSrc\.phase3-tools'){$javaCandidates+=@(Get-ChildItem 'C:\EllieSrc\.phase3-tools' -Filter java.exe -Recurse -ErrorAction SilentlyContinue | ForEach-Object FullName)}
    $selectedJava=$null
    foreach($candidate in $javaCandidates){
        if(-not (Test-Path $candidate)){continue}
        $previous=$ErrorActionPreference;$ErrorActionPreference='Continue'
        $versionText=(& $candidate -version 2>&1 | Out-String)
        $ErrorActionPreference=$previous
        if($versionText -match 'version "21[\."]'){$selectedJava=$candidate;break}
    }
    if(-not $selectedJava){throw 'A Java 21 JDK is required; no Java 21 installation was found on PATH, JAVA_HOME, or in .phase3-tools'}
    $env:JAVA_HOME=Split-Path (Split-Path $selectedJava)
    $env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH
    Invoke-Checked 'git' @('-C',$Repo,'fetch','origin',$Branch) (Join-Path $Logs 'git.log')
    Invoke-Checked 'git' @('-C',$Repo,'merge-base','--is-ancestor',$Baseline,'FETCH_HEAD') (Join-Path $Logs 'git.log')
    $Worktree=Join-Path $env:LOCALAPPDATA ('EllieWarehouse\worktrees\warehouse_'+$stamp)
    New-Item -ItemType Directory -Force -Path (Split-Path $Worktree) | Out-Null
    Invoke-Checked 'git' @('-C',$Repo,'worktree','add','--detach',$Worktree,$Baseline) (Join-Path $Logs 'git.log')
    $actual=(& git -C $Worktree rev-parse HEAD | Out-String).Trim()
    if($LASTEXITCODE -ne 0 -or $actual -ne $Baseline){throw 'Isolated worktree baseline mismatch'}
    $Worktree | Set-Content (Join-Path $Output 'WORKTREE.txt')
    & (Join-Path $PSScriptRoot 'Apply-WarehousePatch.ps1') -SourceRoot $Worktree -PackageRoot $PSScriptRoot
    & (Join-Path $PSScriptRoot 'Verify-Warehouse.ps1') -SourceRoot $Worktree -PackageRoot $PSScriptRoot
    & (Join-Path $PSScriptRoot 'Verify-Logic.ps1') -SourceRoot $Worktree -PackageRoot $PSScriptRoot -OutputRoot $Logs
    $project=Join-Path $Worktree 'ellie\fabric-1.21.1-recovered'
    Push-Location $project
    try {Invoke-Checked $Gradle @('--no-daemon','--console=plain',':fabric:build') (Join-Path $Logs 'fabric-build.log')}
    finally {Pop-Location}
    $jars=@(Get-ChildItem (Join-Path $project 'fabric\build\libs') -Filter '*.jar' | Where-Object {$_.Name -notmatch '(sources|dev|shadow|javadoc)'})
    if($jars.Count -ne 1){throw "Expected one remapped Fabric release JAR; found $($jars.Count)"}
    & (Join-Path $PSScriptRoot 'Verify-Warehouse.ps1') -SourceRoot $Worktree -PackageRoot $PSScriptRoot -JarPath $jars[0].FullName
    $destination=Join-Path $Output 'Ellie_Storage_Warehouse_1.21.1_CANDIDATE.jar'
    if(Test-Path $destination){throw 'Refusing to overwrite an existing delivery JAR'}
    Copy-Item $jars[0].FullName $destination
    (Get-FileHash $destination -Algorithm SHA256).Hash | Set-Content (Join-Path $Output 'JAR_SHA256.txt')
    Write-Host "Build and static verification passed: $destination"
    Write-Host 'Gameplay is unverified. Test in a disposable Minecraft 1.21.1 Fabric world first.'
    Stop-Transcript | Out-Null
    exit 0
} catch {
    Write-Host ('FAILED: '+$_.Exception.Message) -ForegroundColor Red
    Write-Host "Logs retained at $Logs"
    Stop-Transcript | Out-Null
    exit 1
}
