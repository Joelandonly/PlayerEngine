param(
    [string]$MinecraftDirectory = "$env:APPDATA\.minecraft",
    [int]$Port = 38457
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host 'Node.js 22+ is required. Install the Node.js LTS release, reopen PowerShell, and run this script again.'
    exit 1
}
$version = (& node --version).TrimStart('v').Split('.')[0]
if ([int]$version -lt 22) { throw 'Node.js 22 or newer required.' }
$bridge = Join-Path $PSScriptRoot 'sidecar\bridge.mjs'
if (-not (Test-Path -LiteralPath $bridge)) { throw 'Missing sidecar\bridge.mjs. Keep the extracted ZIP folder structure.' }
Write-Host 'Ellie ChatGPT Interpreter. Type login to authorize, status to check, or quit to exit.'
& node $bridge --minecraft $MinecraftDirectory --port $Port
