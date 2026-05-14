param(
    [switch]$PreferMachineInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$localExe = Join-Path $env:LOCALAPPDATA "BamaChatDesktop\BamaChatDesktop.exe"
$machineExe = Join-Path $env:ProgramFiles "BamaChatDesktop\BamaChatDesktop.exe"

$candidates = if ($PreferMachineInstall) {
    @($machineExe, $localExe)
} else {
    @($localExe, $machineExe)
}

$targetExe = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1

if ($null -eq $targetExe) {
    $projectRoot = Split-Path $PSScriptRoot -Parent
    $msiDir = Join-Path $projectRoot "desktopApp\build\compose\binaries\main\msi"
    if (Test-Path $msiDir) {
        $latestMsi = Get-ChildItem $msiDir -Filter "BamaChatDesktop-*.msi" |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($null -ne $latestMsi) {
            Write-Error "BamaChatDesktop ist nicht installiert. Installer gefunden: $($latestMsi.FullName)"
            exit 2
        }
    }
    Write-Error "BamaChatDesktop ist nicht installiert und kein Installer wurde gefunden."
    exit 2
}

Start-Process -FilePath $targetExe | Out-Null
Write-Output "Started: $targetExe"
