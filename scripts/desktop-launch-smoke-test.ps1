param(
    [int]$WaitSeconds = 6
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$candidates = @(
    (Join-Path $env:LOCALAPPDATA "BamaChatDesktop\BamaChatDesktop.exe"),
    (Join-Path $env:ProgramFiles "BamaChatDesktop\BamaChatDesktop.exe")
)

$targetExe = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($null -eq $targetExe) {
    Write-Error "Kein installiertes BamaChatDesktop gefunden."
    exit 2
}

$process = Start-Process -FilePath $targetExe -PassThru
Start-Sleep -Seconds $WaitSeconds

if ($process.HasExited) {
    Write-Error "Desktop-Start fehlgeschlagen. Prozess beendet mit ExitCode=$($process.ExitCode)."
    exit 1
}

Stop-Process -Id $process.Id -Force
Write-Output "Desktop-Start erfolgreich (Smoke-Test): $targetExe"
