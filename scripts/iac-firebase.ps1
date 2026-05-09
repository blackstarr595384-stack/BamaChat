[CmdletBinding()]
param(
    [ValidateSet("dev", "prod")]
    [string]$Environment = "dev",

    [ValidateSet("check", "rules", "indexes", "all")]
    [string]$Action = "check"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot "infra/firebase/environments.json"
$firebaseJson = Join-Path $repoRoot "firebase.json"
$firestoreRules = Join-Path $repoRoot "firestore.rules"
$storageRules = Join-Path $repoRoot "storage.rules"
$firestoreIndexes = Join-Path $repoRoot "firestore.indexes.json"

function Invoke-Firebase {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args
    )

    $command = @("firebase-tools") + $Args
    & npx @command
    if ($LASTEXITCODE -ne 0) {
        throw "Firebase-Befehl fehlgeschlagen: npx $($command -join ' ')"
    }
}

if (-not (Test-Path $envFile)) {
    throw "Env-Datei fehlt: $envFile"
}

if (-not (Test-Path $firebaseJson)) {
    throw "firebase.json fehlt: $firebaseJson"
}

if (-not (Test-Path $firestoreRules)) {
    throw "firestore.rules fehlt: $firestoreRules"
}

if (-not (Test-Path $storageRules)) {
    throw "storage.rules fehlt: $storageRules"
}

if (-not (Test-Path $firestoreIndexes)) {
    throw "firestore.indexes.json fehlt: $firestoreIndexes"
}

$envMap = Get-Content $envFile | ConvertFrom-Json
$projectId = $envMap.$Environment
if ([string]::IsNullOrWhiteSpace($projectId)) {
    throw "Keine Project-ID für Umgebung '$Environment' in $envFile."
}

Write-Host "Umgebung: $Environment"
Write-Host "Firebase-Projekt: $projectId"

switch ($Action) {
    "check" {
        Invoke-Firebase -Args @("--version")
        Write-Host "Check OK: Dateien und Firebase-CLI sind verfuegbar."
    }
    "rules" {
        Invoke-Firebase -Args @(
            "deploy",
            "--project", $projectId,
            "--only", "firestore:rules,storage"
        )
        Write-Host "Rules-Deploy abgeschlossen."
    }
    "indexes" {
        Invoke-Firebase -Args @(
            "deploy",
            "--project", $projectId,
            "--only", "firestore:indexes"
        )
        Write-Host "Index-Deploy abgeschlossen."
    }
    "all" {
        Invoke-Firebase -Args @(
            "deploy",
            "--project", $projectId,
            "--only", "firestore:rules,firestore:indexes,storage"
        )
        Write-Host "Vollständiger IaC-Deploy abgeschlossen."
    }
}
