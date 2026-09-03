param(
    [Parameter(Mandatory = $true)][string]$MsixPath,
    [Parameter(Mandatory = $true)][string]$StoreContract
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$expectedIdentityName = "MamadouDianBald.BamaFlow"
$expectedPublisher = "CN=2279D882-BC23-4831-AA4E-D384F8EFCD9A"

function Get-WindowsSdkTool([string]$ToolName) {
    $roots = @()
    foreach ($registryPath in @(
        "HKLM:\SOFTWARE\Microsoft\Windows Kits\Installed Roots",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows Kits\Installed Roots"
    )) {
        if (Test-Path -LiteralPath $registryPath) {
            $root = (Get-ItemProperty -LiteralPath $registryPath).KitsRoot10
            if ($root) {
                $roots += $root
            }
        }
    }
    $candidates = foreach ($root in ($roots | Sort-Object -Unique)) {
        $binDirectory = Join-Path $root "bin"
        if (-not (Test-Path -LiteralPath $binDirectory -PathType Container)) {
            continue
        }
        Get-ChildItem -LiteralPath $binDirectory -Directory | ForEach-Object {
            $candidate = Join-Path $_.FullName "x64\$ToolName"
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                Get-Item -LiteralPath $candidate
            }
        }
    }
    $selected = $candidates | Sort-Object {
        try { [version]$_.Directory.Parent.Name } catch { [version]"0.0" }
    } | Select-Object -Last 1
    if ($null -eq $selected) {
        throw "$ToolName fehlt im installierten Windows SDK."
    }
    return $selected.FullName
}

function Remove-CurrentUserTestCertificate([string]$Thumbprint) {
    $certificatePath = "Cert:\CurrentUser\My\$Thumbprint"
    if (Test-Path -LiteralPath $certificatePath) {
        Remove-Item -LiteralPath $certificatePath -Force
    }
}

function Remove-TemporaryDirectory([string]$Path) {
    if (-not [IO.Directory]::Exists($Path)) {
        return
    }
    Get-ChildItem -LiteralPath $Path -Recurse -Force -File | ForEach-Object {
        $_.Attributes = [IO.FileAttributes]::Normal
    }
    [IO.Directory]::Delete($Path, $true)
}

function Quote-ProcessArgument([string]$Value) {
    return '"' + $Value.Replace('"', '\"') + '"'
}

if ($PSVersionTable.PSEdition -eq "Core" -and -not $IsWindows) {
    throw "Der lokale MSIX-Signier- und Installationstest ist ausschließlich unter Windows verfügbar."
}

$unsignedMsix = [IO.Path]::GetFullPath($MsixPath)
if (-not (Test-Path -LiteralPath $unsignedMsix -PathType Leaf)) {
    throw "Die zu testende Store-MSIX-Datei fehlt."
}
$contractPath = [IO.Path]::GetFullPath($StoreContract)
if (-not (Test-Path -LiteralPath $contractPath -PathType Leaf)) {
    throw "Der Store-Paketvertrag fehlt."
}
$contract = Get-Content -LiteralPath $contractPath -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not [string]::Equals($contract.identityName, $expectedIdentityName, [StringComparison]::Ordinal) -or
    -not [string]::Equals($contract.publisher, $expectedPublisher, [StringComparison]::Ordinal)) {
    throw "Der Store-Paketvertrag stimmt nicht mit der verbindlichen Identität überein."
}

$existingPackages = @(Get-AppxPackage -Name $expectedIdentityName -ErrorAction Stop)
if ($existingPackages.Count -gt 0) {
    throw "Ein BamaFlow-Paket mit derselben Identity ist bereits für CurrentUser installiert; der Test wurde unverändert gestoppt."
}

$signTool = Get-WindowsSdkTool "signtool.exe"
$unsignedHashBefore = (Get-FileHash -LiteralPath $unsignedMsix -Algorithm SHA256).Hash
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("BamaFlow-MsixTest-" + [Guid]::NewGuid().ToString("N"))
$signedMsix = Join-Path $temporaryRoot "BamaFlow-local-test.msix"
$certificateFile = Join-Path $temporaryRoot "BamaFlow-local-test.cer"
$pfxFile = Join-Path $temporaryRoot "BamaFlow-local-test.pfx"
$elevatedResultFile = Join-Path $temporaryRoot "elevated-result.json"
$elevatedHelper = Join-Path $PSScriptRoot "test-store-msix-elevated.ps1"
$createdThumbprint = $null
$pfxPasswordPlain = $null
$elevatedResult = $null
$testFailure = $null
$cleanupFailures = [Collections.Generic.List[string]]::new()

try {
    if (-not (Test-Path -LiteralPath $elevatedHelper -PathType Leaf)) {
        throw "Der erhöhte MSIX-Vertrauens-Helper fehlt."
    }
    [IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
    Copy-Item -LiteralPath $unsignedMsix -Destination $signedMsix

    $certificate = New-SelfSignedCertificate `
        -Type Custom `
        -KeyUsage DigitalSignature `
        -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3", "2.5.29.19={text}") `
        -Subject $expectedPublisher `
        -CertStoreLocation "Cert:\CurrentUser\My" `
        -KeyAlgorithm RSA `
        -KeyLength 2048 `
        -HashAlgorithm SHA256 `
        -KeyExportPolicy Exportable `
        -NotAfter (Get-Date).AddDays(1)
    $createdThumbprint = $certificate.Thumbprint
    if (-not [string]::Equals($certificate.Subject, $expectedPublisher, [StringComparison]::Ordinal)) {
        throw "Das temporäre Testzertifikat besitzt nicht den exakten Store-Publisher."
    }
    if (-not $certificate.HasPrivateKey) {
        throw "Das temporäre CurrentUser-Testzertifikat besitzt keinen privaten Signierschlüssel."
    }

    $passwordBytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($passwordBytes)
    $pfxPasswordPlain = [Convert]::ToBase64String($passwordBytes)
    [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    $pfxPassword = ConvertTo-SecureString -String $pfxPasswordPlain -AsPlainText -Force
    Export-PfxCertificate -Cert $certificate -FilePath $pfxFile -Password $pfxPassword | Out-Null
    Export-Certificate -Cert $certificate -FilePath $certificateFile -Type CERT | Out-Null

    & $signTool sign /fd SHA256 /f $pfxFile /p $pfxPasswordPlain $signedMsix
    if ($LASTEXITCODE -ne 0) { throw "SignTool konnte die temporäre MSIX-Testkopie nicht signieren." }
    $signature = Get-AuthenticodeSignature -FilePath $signedMsix
    if ($null -eq $signature.SignerCertificate -or
        -not [string]::Equals($signature.SignerCertificate.Thumbprint, $createdThumbprint, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Die temporäre MSIX-Testkopie enthält nicht die erwartete Testsignatur."
    }

    $argumentList = @(
        "-NoProfile",
        "-NonInteractive",
        "-ExecutionPolicy", "Bypass",
        "-File", (Quote-ProcessArgument $elevatedHelper),
        "-SignedMsix", (Quote-ProcessArgument $signedMsix),
        "-PublicCertificate", (Quote-ProcessArgument $certificateFile),
        "-ExpectedThumbprint", $createdThumbprint,
        "-ExpectedIdentityName", $expectedIdentityName,
        "-ExpectedPublisher", $expectedPublisher,
        "-ExpectedVersion", "1.0.1.0",
        "-TemporaryRoot", (Quote-ProcessArgument $temporaryRoot),
        "-ResultFile", (Quote-ProcessArgument $elevatedResultFile)
    )
    try {
        $elevatedProcess = Start-Process `
            -FilePath "powershell.exe" `
            -Verb RunAs `
            -ArgumentList $argumentList `
            -Wait `
            -PassThru
    } catch {
        throw "Die Windows-Administratorabfrage wurde abgelehnt oder konnte nicht gestartet werden: $($_.Exception.Message)"
    }
    if (Test-Path -LiteralPath $elevatedResultFile -PathType Leaf) {
        $elevatedResult = Get-Content -LiteralPath $elevatedResultFile -Raw -Encoding UTF8 | ConvertFrom-Json
    }
    if ($elevatedProcess.ExitCode -ne 0) {
        $detail = if ($null -ne $elevatedResult -and $elevatedResult.errorMessage) {
            $elevatedResult.errorMessage
        } else {
            "Der erhöhte Helper lieferte ExitCode $($elevatedProcess.ExitCode) ohne Ergebnisdatei."
        }
        throw "Der lokale MSIX-Vertrauenstest ist fehlgeschlagen: $detail"
    }
    if ($null -eq $elevatedResult -or -not $elevatedResult.success) {
        throw "Der erhöhte MSIX-Vertrauenstest lieferte kein erfolgreiches Ergebnis."
    }
    Write-Host "InstalledPackageFullName=$($elevatedResult.packageFullName)"
    Write-Host "InstalledPackageFamilyName=$($elevatedResult.packageFamilyName)"
} catch {
    $testFailure = $_
} finally {
    if (-not [string]::IsNullOrWhiteSpace($createdThumbprint)) {
        try {
            Remove-CurrentUserTestCertificate $createdThumbprint
        } catch {
            $cleanupFailures.Add("Testzertifikat blieb in CurrentUser\My zurück: $($_.Exception.Message)")
        }
    }
    if ([IO.Directory]::Exists($temporaryRoot)) {
        try {
            Remove-TemporaryDirectory $temporaryRoot
        } catch {
            $cleanupFailures.Add("Temporäres Testverzeichnis konnte nicht entfernt werden: $($_.Exception.Message)")
        }
    }
    $pfxPasswordPlain = $null
    $pfxPassword = $null
}

$remainingPackages = @(Get-AppxPackage -Name $expectedIdentityName -ErrorAction Stop)
if ($remainingPackages.Count -gt 0) {
    $cleanupFailures.Add("Testpaket ist nach Cleanup noch installiert: $($remainingPackages.PackageFullName -join ', ')")
}
if (-not [string]::IsNullOrWhiteSpace($createdThumbprint)) {
    if (Test-Path -LiteralPath "Cert:\CurrentUser\My\$createdThumbprint") {
        $cleanupFailures.Add("Testzertifikat ist nach Cleanup noch in CurrentUser\My vorhanden: $createdThumbprint")
    }
    if (Test-Path -LiteralPath "Cert:\LocalMachine\TrustedPeople\$createdThumbprint") {
        $cleanupFailures.Add("Öffentliches Testzertifikat ist nach Cleanup noch in LocalMachine\TrustedPeople vorhanden: $createdThumbprint")
    }
    foreach ($rootStore in @("Cert:\CurrentUser\Root", "Cert:\LocalMachine\Root")) {
        if (Test-Path -LiteralPath "$rootStore\$createdThumbprint") {
            $cleanupFailures.Add("Unerlaubter Root-Store-Rückstand: $rootStore\$createdThumbprint")
        }
    }
}
if ([IO.Directory]::Exists($temporaryRoot)) {
    $cleanupFailures.Add("Temporäres Testverzeichnis ist nach Cleanup noch vorhanden: $temporaryRoot")
}
$unsignedHashAfter = (Get-FileHash -LiteralPath $unsignedMsix -Algorithm SHA256).Hash
if (-not [string]::Equals($unsignedHashBefore, $unsignedHashAfter, [StringComparison]::Ordinal)) {
    $cleanupFailures.Add("Die unveränderte Store-MSIX-Ausgabe wurde während des lokalen Tests verändert.")
}

if ($cleanupFailures.Count -gt 0) {
    throw ($cleanupFailures -join [Environment]::NewLine)
}
if ($null -ne $testFailure) {
    throw $testFailure
}

Write-Host "LocalTestSigning=PASS"
Write-Host "LocalMsixInstall=PASS"
Write-Host "LocalMsixUninstall=PASS"
Write-Host "PackageIdentityMatch=True"
Write-Host "LocalMachineTrustedPeopleUsed=True"
Write-Host "TrustedRootUsed=False"
Write-Host "PrivateKeyImportedToLocalMachine=False"
Write-Host "CurrentUserCertificateRemoved=True"
Write-Host "LocalMachineCertificateRemoved=True"
Write-Host "TemporaryPfxRemoved=True"
Write-Host "TemporaryCerRemoved=True"
Write-Host "TemporaryPackageRemoved=True"
Write-Host "TemporaryDirectoryRemoved=True"
