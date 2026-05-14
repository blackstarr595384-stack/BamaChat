Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

$machineInstalls = @(Get-ItemProperty "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*" -ErrorAction SilentlyContinue |
    Where-Object {
        $displayNameProperty = $_.PSObject.Properties["DisplayName"]
        $installLocationProperty = $_.PSObject.Properties["InstallLocation"]
        if ($null -eq $displayNameProperty -or $null -eq $installLocationProperty) {
            return $false
        }
        $displayName = [string]$displayNameProperty.Value
        $installLocation = [string]$installLocationProperty.Value
        $displayName -eq "BamaChatDesktop" -and $installLocation -like "$env:ProgramFiles*"
    } |
    Select-Object DisplayVersion, PSChildName, InstallLocation)

if ($null -eq $machineInstalls -or $machineInstalls.Count -eq 0) {
    Write-Output "Keine Legacy-Machine-Installation gefunden."
    exit 0
}

if (-not (Test-IsAdministrator)) {
    Write-Error "Legacy-Machine-Installation gefunden. Bitte PowerShell als Administrator starten und Script erneut ausfuehren."
    $machineInstalls | ForEach-Object {
        Write-Output "Gefunden: Version=$($_.DisplayVersion), ProductCode=$($_.PSChildName), InstallLocation=$($_.InstallLocation)"
    }
    exit 2
}

foreach ($install in $machineInstalls) {
    $code = $install.PSChildName
    $log = Join-Path $env:TEMP "BamaChatDesktop-remove-$($install.DisplayVersion)-$code.log"
    Write-Output "Entferne Machine-Installation Version $($install.DisplayVersion) ($code) ..."
    Start-Process msiexec.exe -ArgumentList "/x$code /qn /norestart /L*v `"$log`"" -Wait -PassThru | Out-Null
    Write-Output "Entfernt. Log: $log"
}
