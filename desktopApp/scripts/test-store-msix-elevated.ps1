param(
    [Parameter(Mandatory = $true)][string]$SignedMsix,
    [Parameter(Mandatory = $true)][string]$PublicCertificate,
    [Parameter(Mandatory = $true)][string]$ExpectedThumbprint,
    [Parameter(Mandatory = $true)][string]$ExpectedIdentityName,
    [Parameter(Mandatory = $true)][string]$ExpectedPublisher,
    [Parameter(Mandatory = $true)][string]$ExpectedVersion,
    [Parameter(Mandatory = $true)][string]$TemporaryRoot,
    [Parameter(Mandatory = $true)][string]$ResultFile
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-PathWithin([string]$Path, [string]$Root, [string]$Label) {
    $fullPath = [IO.Path]::GetFullPath($Path)
    $fullRoot = [IO.Path]::GetFullPath($Root).TrimEnd('\') + '\'
    if (-not $fullPath.StartsWith($fullRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label muss innerhalb des temporären Testverzeichnisses liegen."
    }
    return $fullPath
}

function Test-RootCertificate([string]$Thumbprint) {
    foreach ($rootStore in @("Cert:\CurrentUser\Root", "Cert:\LocalMachine\Root")) {
        if (Test-Path -LiteralPath "$rootStore\$Thumbprint") {
            return $true
        }
    }
    return $false
}

function Get-X509Certificate2BaseObject([object]$ProviderObject) {
    $baseObject = $null
    if ($null -ne $ProviderObject) {
        $baseObject = $ProviderObject.PSObject.BaseObject
        while ($baseObject -is [System.Management.Automation.PSObject]) {
            $nextBaseObject = $baseObject.BaseObject
            if ([object]::ReferenceEquals($baseObject, $nextBaseObject)) {
                break
            }
            $baseObject = $nextBaseObject
        }
    }
    if ($baseObject -is [Security.Cryptography.X509Certificates.X509Certificate2]) {
        return $baseObject
    }

    $actualTypeName = if ($null -eq $baseObject) {
        "<null>"
    } else {
        $baseObject.GetType().FullName
    }
    $typeException = [IO.InvalidDataException]::new("Unerwarteter Zertifikattyp.")
    $typeException.Data["ActualTypeName"] = $actualTypeName
    throw $typeException
}

function Set-CertificateHashStage([string]$Stage, [object]$Receiver) {
    $script:diagnosticStage = $Stage
    $script:diagnosticReceiverType = if ($null -eq $Receiver) {
        "<null>"
    } else {
        $Receiver.GetType().FullName
    }
    $script:diagnosticReceiverIsX509Certificate2 =
        $Receiver -is [Security.Cryptography.X509Certificates.X509Certificate2]
    if (-not $script:diagnosticReceiverIsX509Certificate2) {
        throw [IO.InvalidDataException]::new("Der Hash-Receiver ist kein X509Certificate2.")
    }
}

$temporaryRootPath = [IO.Path]::GetFullPath($TemporaryRoot)
$signedMsixPath = Get-PathWithin $SignedMsix $temporaryRootPath "Signierte MSIX-Testkopie"
$publicCertificatePath = Get-PathWithin $PublicCertificate $temporaryRootPath "Öffentliches Testzertifikat"
$resultPath = Get-PathWithin $ResultFile $temporaryRootPath "Ergebnisdatei"
$normalizedThumbprint = ($ExpectedThumbprint -replace '\s', '').ToUpperInvariant()
if ($normalizedThumbprint.Length -ne 40 -or $normalizedThumbprint -notmatch '^[0-9A-F]{40}$') {
    throw "Der erwartete Zertifikat-Thumbprint ist ungültig."
}
$trustedPeopleStore = "Cert:\LocalMachine\TrustedPeople"
$trustedPeoplePath = "$trustedPeopleStore\$normalizedThumbprint"
$installedPackageFullName = $null
$packageFamilyName = $null
$certificateImported = $false
$packageInstalled = $false
$packageIdentityMatched = $false
$packageUninstalled = $false
$certificateRemoved = $false
$privateKeyImported = $false
$rootStoreUsed = $false
$failureMessage = $null
$diagnosticStage = "NONE"
$diagnosticReceiverType = "NONE"
$diagnosticReceiverIsX509Certificate2 = $false
$failureExceptionType = "NONE"
$failureScriptName = "NONE"
$failureScriptLine = 0
$cleanupFailures = [Collections.Generic.List[string]]::new()

try {
    if (-not (Test-Path -LiteralPath $signedMsixPath -PathType Leaf)) {
        throw "Die signierte MSIX-Testkopie fehlt."
    }
    if (-not (Test-Path -LiteralPath $publicCertificatePath -PathType Leaf)) {
        throw "Das öffentliche Testzertifikat fehlt."
    }
    if (@(Get-AppxPackage -Name $ExpectedIdentityName -ErrorAction Stop).Count -ne 0) {
        throw "Vor dem Import ist bereits ein Paket mit der Test-Identity installiert."
    }
    if (Test-Path -LiteralPath $trustedPeoplePath) {
        throw "Der neue Thumbprint ist bereits in LocalMachine\TrustedPeople vorhanden."
    }
    if (Test-RootCertificate $normalizedThumbprint) {
        throw "Der neue Thumbprint ist unerwartet in einem Root-Speicher vorhanden."
    }

    $publicCertificateObject = [Security.Cryptography.X509Certificates.X509Certificate2]::new($publicCertificatePath)
    Set-CertificateHashStage -Stage "PUBLIC_CERTIFICATE_HASH" -Receiver $publicCertificateObject
    $publicCertificateThumbprint = ($publicCertificateObject.GetCertHashString() -replace '\s', '').ToUpperInvariant()
    if (-not [string]::Equals($publicCertificateThumbprint, $normalizedThumbprint, [StringComparison]::Ordinal) -or
        -not [string]::Equals($publicCertificateObject.Subject, $ExpectedPublisher, [StringComparison]::Ordinal)) {
        throw "Die CER stimmt nicht mit Thumbprint und Publisher des Testzertifikats überein."
    }
    if ($publicCertificateObject.HasPrivateKey) {
        throw "Die CER enthält unerwartet einen privaten Schlüssel."
    }

    Import-Certificate `
        -FilePath $publicCertificatePath `
        -CertStoreLocation $trustedPeopleStore | Out-Null
    $certificateImported = $true
    $matchingCertificates = @(Get-ChildItem -LiteralPath $trustedPeopleStore | Where-Object {
        $candidateCertificate = Get-X509Certificate2BaseObject $_
        Set-CertificateHashStage -Stage "STORE_CANDIDATE_HASH" -Receiver $candidateCertificate
        $candidateThumbprint = ($candidateCertificate.GetCertHashString() -replace '\s', '').ToUpperInvariant()
        $candidateThumbprint -eq $normalizedThumbprint
    })
    if ($matchingCertificates.Count -ne 1) {
        throw "LocalMachine\TrustedPeople enthält nach Import nicht exakt ein Zertifikat mit dem neuen Thumbprint."
    }
    $exactPathResults = @(Get-Item -LiteralPath $trustedPeoplePath -ErrorAction Stop)
    if ($exactPathResults.Count -ne 1) {
        throw "Der exakte LocalMachine\TrustedPeople-Pfad lieferte nicht genau ein Zertifikat."
    }
    $importedCertificate = Get-X509Certificate2BaseObject $exactPathResults[0]
    Set-CertificateHashStage -Stage "EXACT_PATH_CERTIFICATE_HASH" -Receiver $importedCertificate
    $importedThumbprint = ($importedCertificate.GetCertHashString() -replace '\s', '').ToUpperInvariant()
    if (-not [string]::Equals($importedThumbprint, $normalizedThumbprint, [StringComparison]::Ordinal) -or
        -not [string]::Equals($importedCertificate.Subject, $ExpectedPublisher, [StringComparison]::Ordinal)) {
        throw "Das importierte LocalMachine-Zertifikat stimmt nicht exakt mit der CER überein."
    }
    if ($importedCertificate.HasPrivateKey) {
        $privateKeyImported = $true
        throw "LocalMachine\TrustedPeople enthält unerwartet einen privaten Schlüssel."
    }
    if (Test-RootCertificate $normalizedThumbprint) {
        $rootStoreUsed = $true
        throw "Während des Tests wurde unerwartet ein Root-Speicher verwendet."
    }

    Add-AppxPackage -Path $signedMsixPath -ErrorAction Stop
    $installedPackages = @(Get-AppxPackage -Name $ExpectedIdentityName -ErrorAction Stop)
    if ($installedPackages.Count -ne 1) {
        throw "Die lokale Installation ergab nicht exakt ein Testpaket."
    }
    $installedPackage = $installedPackages[0]
    $installedPackageFullName = $installedPackage.PackageFullName
    $packageFamilyName = $installedPackage.PackageFamilyName
    $packageInstalled = $true
    if (-not [string]::Equals($installedPackage.Name, $ExpectedIdentityName, [StringComparison]::Ordinal) -or
        -not [string]::Equals($installedPackage.Publisher, $ExpectedPublisher, [StringComparison]::Ordinal) -or
        -not [string]::Equals($installedPackage.Version.ToString(), $ExpectedVersion, [StringComparison]::Ordinal)) {
        throw "Name, Publisher oder Version des installierten Testpakets stimmen nicht exakt überein."
    }
    if ([string]::IsNullOrWhiteSpace($packageFamilyName)) {
        throw "Das installierte Testpaket besitzt keinen Package Family Name."
    }
    $packageIdentityMatched = $true
} catch {
    $failureExceptionType = $_.Exception.GetType().FullName
    $failureScriptName = if ($_.InvocationInfo.ScriptName) {
        [IO.Path]::GetFileName($_.InvocationInfo.ScriptName)
    } else {
        [IO.Path]::GetFileName($PSCommandPath)
    }
    $failureScriptLine = $_.InvocationInfo.ScriptLineNumber
    $actualTypeName = $_.Exception.Data["ActualTypeName"]
    if ($actualTypeName) {
        $baseFailureMessage = "Unerwarteter Zertifikattyp: $actualTypeName"
    } else {
        $baseFailureMessage = $_.Exception.Message
    }
    $failureMessage = "$baseFailureMessage; Stage=$diagnosticStage; ReceiverType=$diagnosticReceiverType; ReceiverIsX509Certificate2=$diagnosticReceiverIsX509Certificate2; ExceptionType=$failureExceptionType; Skript=$failureScriptName; Zeile=$failureScriptLine"
} finally {
    if ([string]::IsNullOrWhiteSpace($installedPackageFullName)) {
        try {
            $packagesAfterFailure = @(Get-AppxPackage -Name $ExpectedIdentityName -ErrorAction Stop)
            if ($packagesAfterFailure.Count -eq 1) {
                $installedPackageFullName = $packagesAfterFailure[0].PackageFullName
            } elseif ($packagesAfterFailure.Count -gt 1) {
                $cleanupFailures.Add("Mehr als ein Testpaket ist installiert; kein pauschaler Cleanup wurde ausgeführt.")
            }
        } catch {
            $cleanupFailures.Add("Paketstatus konnte beim Cleanup nicht ermittelt werden: $($_.Exception.Message)")
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($installedPackageFullName)) {
        try {
            Remove-AppxPackage -Package $installedPackageFullName -ErrorAction Stop
            $packageUninstalled = $true
        } catch {
            $cleanupFailures.Add("Testpaket blieb installiert: $installedPackageFullName; $($_.Exception.Message)")
        }
    }
    if (Test-Path -LiteralPath $trustedPeoplePath) {
        try {
            Remove-Item -LiteralPath $trustedPeoplePath -Force
            $certificateRemoved = $true
        } catch {
            $cleanupFailures.Add("Testzertifikat blieb in LocalMachine\TrustedPeople zurück: $normalizedThumbprint; $($_.Exception.Message)")
        }
    } elseif ($certificateImported) {
        $certificateRemoved = $true
    }

    try {
        $remainingPackages = @(Get-AppxPackage -Name $ExpectedIdentityName -ErrorAction Stop)
        if ($remainingPackages.Count -gt 0) {
            $cleanupFailures.Add("Installiertes Testpaket verbleibt: $($remainingPackages.PackageFullName -join ', ')")
        }
    } catch {
        $cleanupFailures.Add("Paket-Restzustand konnte nicht geprüft werden: $($_.Exception.Message)")
    }
    if (Test-Path -LiteralPath $trustedPeoplePath) {
        $cleanupFailures.Add("Zertifikat verbleibt in LocalMachine\TrustedPeople: $normalizedThumbprint")
    }
    if (Test-RootCertificate $normalizedThumbprint) {
        $rootStoreUsed = $true
        $cleanupFailures.Add("Thumbprint wurde in einem Root-Speicher gefunden: $normalizedThumbprint")
    }

    if ($cleanupFailures.Count -gt 0) {
        $cleanupText = $cleanupFailures -join " | "
        $failureMessage = if ($failureMessage) { "$failureMessage | $cleanupText" } else { $cleanupText }
    }
    $success = $null -eq $failureMessage
    $result = [ordered]@{
        success = $success
        errorMessage = $failureMessage
        publicCertificateImportedToLocalMachine = $certificateImported
        localMachineTrustedPeopleUsed = $certificateImported
        trustedRootUsed = $rootStoreUsed
        privateKeyImportedToLocalMachine = $privateKeyImported
        packageInstalled = $packageInstalled
        packageIdentityMatched = $packageIdentityMatched
        packageUninstalled = $packageUninstalled
        localMachineCertificateRemoved = $certificateRemoved
        packageFullName = $installedPackageFullName
        packageFamilyName = $packageFamilyName
        failureStage = if ($success) { "NONE" } else { $diagnosticStage }
        failureReceiverType = if ($success) { "NONE" } else { $diagnosticReceiverType }
        failureReceiverIsX509Certificate2 = if ($success) { $false } else { $diagnosticReceiverIsX509Certificate2 }
        failureExceptionType = if ($success) { "NONE" } else { $failureExceptionType }
        failureScriptName = if ($success) { "NONE" } else { $failureScriptName }
        failureScriptLine = if ($success) { 0 } else { $failureScriptLine }
    }
    [IO.File]::WriteAllText(
        $resultPath,
        ($result | ConvertTo-Json -Compress),
        [Text.UTF8Encoding]::new($false)
    )
}

if ($null -ne $failureMessage) {
    Write-Error $failureMessage
    exit 1
}
exit 0
