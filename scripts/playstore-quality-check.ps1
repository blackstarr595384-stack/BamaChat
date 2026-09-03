param(
    [switch]$SkipReleaseBuild,
    [switch]$SkipFirebaseCheck
)

$ErrorActionPreference = "Stop"

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Command
    )

    Write-Host ""
    Write-Host "== $Name ==" -ForegroundColor Cyan
    & $Command
}

Invoke-Step "Repository hygiene" {
    $trackedRiskyFiles = git ls-files |
        Where-Object {
            $_ -match '(^|/)(\.env|keystore\.properties|local\.properties)$' -or
            $_ -match '\.(jks|keystore|p12|apk|aab|zip)$' -or
            $_ -match '(^|/)(node_modules|app/build|build|backups|playstore_reports)(/|$)' -or
            $_ -match '(^|/)(hs_err_pid.*\.log|replay_pid.*\.log)$'
        }

    if ($trackedRiskyFiles) {
        Write-Host "Tracked release-risk files found:" -ForegroundColor Red
        $trackedRiskyFiles | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
        throw "Repository hygiene check failed."
    }

    Write-Host "No tracked secrets, release artifacts, build outputs, or crash logs found."
}

Invoke-Step "Core stability check" {
    .\gradlew.bat :app:stabilityCheck
}

Invoke-Step "Android UI test APK" {
    .\gradlew.bat :app:assembleDebugAndroidTest
}

if (-not $SkipReleaseBuild) {
    Invoke-Step "Release bundle" {
        .\gradlew.bat :app:bundleRelease
    }
}

if (-not $SkipFirebaseCheck) {
    Invoke-Step "Firebase rules/index check" {
        .\scripts\iac-firebase.ps1 -Environment dev -Action check
    }
}

Write-Host ""
Write-Host "Play Store quality gate completed." -ForegroundColor Green
