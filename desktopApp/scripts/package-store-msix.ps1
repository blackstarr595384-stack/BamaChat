param(
    [Parameter(Mandatory = $true)][string]$AppImageDirectory,
    [Parameter(Mandatory = $true)][string]$ManifestTemplate,
    [Parameter(Mandatory = $true)][string]$StoreContract,
    [Parameter(Mandatory = $true)][string]$SourceIcon,
    [Parameter(Mandatory = $true)][string]$BuildDirectory,
    [Parameter(Mandatory = $true)][string]$DesktopVersion,
    [Parameter(Mandatory = $true)][string]$OutputMsix
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$expectedIdentityName = "MamadouDianBald.BamaFlow"
$expectedPublisher = "CN=2279D882-BC23-4831-AA4E-D384F8EFCD9A"
$expectedPublisherDisplayName = "Mamadou Dian Bald" + [char]0x00E9
$expectedDisplayName = "BamaFlow"
$expectedApplicationId = "BamaFlow"
$expectedArchitecture = "x64"
$expectedStoreId = "9P61V47KR1Z8"
$foundationNamespace = "http://schemas.microsoft.com/appx/manifest/foundation/windows10"
$uapNamespace = "http://schemas.microsoft.com/appx/manifest/uap/windows10"
$uap10Namespace = "http://schemas.microsoft.com/appx/manifest/uap/windows10/10"
$restrictedCapabilityNamespace =
    "http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"

function Assert-Exact([string]$Actual, [string]$Expected, [string]$Label) {
    if (-not [string]::Equals($Actual, $Expected, [StringComparison]::Ordinal)) {
        throw "$Label stimmt nicht exakt überein."
    }
}

function Convert-ToMsixVersion([string]$Version) {
    $components = $Version.Split('.')
    if ($components.Count -ne 3) {
        throw "Desktop packageVersion muss für MSIX exakt drei numerische Komponenten besitzen."
    }
    $numbers = @()
    for ($index = 0; $index -lt $components.Count; $index++) {
        $component = $components[$index]
        if ($component -notmatch '^[0-9]+$') {
            throw "Desktop packageVersion-Komponente $($index + 1) ist nicht numerisch."
        }
        $parsed = 0
        if (-not [int]::TryParse($component, [ref]$parsed) -or $parsed -lt 0 -or $parsed -gt 65535) {
            throw "Desktop packageVersion-Komponente $($index + 1) liegt außerhalb 0..65535."
        }
        $numbers += $parsed
    }
    if ($numbers[0] -le 0) {
        throw "Die erste MSIX-Versionskomponente muss größer als 0 sein."
    }
    return "$($numbers[0]).$($numbers[1]).$($numbers[2]).0"
}

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

function Get-PathWithin([string]$Path, [string]$Root, [string]$Label) {
    $fullPath = [IO.Path]::GetFullPath($Path)
    $fullRoot = [IO.Path]::GetFullPath($Root).TrimEnd('\') + '\'
    if (-not $fullPath.StartsWith($fullRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label muss innerhalb von $Root liegen."
    }
    return $fullPath
}

function Reset-Directory([string]$Path, [string]$Root) {
    $safePath = Get-PathWithin $Path $Root "Temporäres MSIX-Verzeichnis"
    if ([IO.Directory]::Exists($safePath)) {
        Remove-SafeDirectory $safePath $Root
    }
    [IO.Directory]::CreateDirectory($safePath) | Out-Null
    return $safePath
}

function Remove-SafeDirectory([string]$Path, [string]$Root) {
    $safePath = Get-PathWithin $Path $Root "Temporäres MSIX-Verzeichnis"
    if (-not [IO.Directory]::Exists($safePath)) {
        return
    }
    Get-ChildItem -LiteralPath $safePath -Recurse -Force -File | ForEach-Object {
        $_.Attributes = [IO.FileAttributes]::Normal
    }
    [IO.Directory]::Delete($safePath, $true)
}

function New-LogoAsset(
    [System.Drawing.Bitmap]$SourceBitmap,
    [int]$Size,
    [string]$Destination
) {
    $target = [System.Drawing.Bitmap]::new(
        $Size,
        $Size,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($target)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $graphics.CompositingQuality =
                [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $graphics.InterpolationMode =
                [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
            $scale = [Math]::Min(
                $Size / [double]$SourceBitmap.Width,
                $Size / [double]$SourceBitmap.Height
            )
            $width = [Math]::Max(1, [int][Math]::Round($SourceBitmap.Width * $scale))
            $height = [Math]::Max(1, [int][Math]::Round($SourceBitmap.Height * $scale))
            $left = [int](($Size - $width) / 2)
            $top = [int](($Size - $height) / 2)
            $graphics.DrawImage($SourceBitmap, $left, $top, $width, $height)
        } finally {
            $graphics.Dispose()
        }
        $target.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $target.Dispose()
    }
}

function Read-BestIcoBitmap([string]$Path) {
    $bytes = [IO.File]::ReadAllBytes($Path)
    $stream = [IO.MemoryStream]::new($bytes, $false)
    $reader = [IO.BinaryReader]::new($stream)
    try {
        $reserved = $reader.ReadUInt16()
        $imageType = $reader.ReadUInt16()
        $imageCount = $reader.ReadUInt16()
        if ($reserved -ne 0 -or $imageType -ne 1 -or $imageCount -lt 1) {
            throw "Desktop-Logoquelle ist keine gültige ICO-Datei."
        }
        $entries = for ($index = 0; $index -lt $imageCount; $index++) {
            $widthByte = $reader.ReadByte()
            $heightByte = $reader.ReadByte()
            $reader.ReadByte() | Out-Null
            $reader.ReadByte() | Out-Null
            $reader.ReadUInt16() | Out-Null
            $bitDepth = $reader.ReadUInt16()
            $byteCount = $reader.ReadUInt32()
            $offset = $reader.ReadUInt32()
            [pscustomobject]@{
                Width = if ($widthByte -eq 0) { 256 } else { [int]$widthByte }
                Height = if ($heightByte -eq 0) { 256 } else { [int]$heightByte }
                BitDepth = [int]$bitDepth
                ByteCount = [long]$byteCount
                Offset = [long]$offset
            }
        }
        $selected = $entries | Sort-Object `
            @{ Expression = { $_.Width * $_.Height }; Descending = $true }, `
            @{ Expression = { $_.BitDepth }; Descending = $true } | Select-Object -First 1
        if ($selected.Width -lt 256 -or $selected.Height -lt 256) {
            throw "Desktop-Logoquelle ist kleiner als 256x256 Pixel."
        }
        if ($selected.Offset -lt 0 -or $selected.ByteCount -le 0 -or
            $selected.Offset + $selected.ByteCount -gt $bytes.LongLength) {
            throw "Der größte ICO-Frame besitzt ungültige Grenzen."
        }
        $frameBytes = [byte[]]::new([int]$selected.ByteCount)
        [Array]::Copy($bytes, [int]$selected.Offset, $frameBytes, 0, $frameBytes.Length)
        $pngSignature = [byte[]](137, 80, 78, 71, 13, 10, 26, 10)
        for ($index = 0; $index -lt $pngSignature.Length; $index++) {
            if ($frameBytes[$index] -ne $pngSignature[$index]) {
                throw "Der größte ICO-Frame ist keine verlustfreie PNG-Logoquelle."
            }
        }
        $frameStream = [IO.MemoryStream]::new($frameBytes, $false)
        try {
            $image = [System.Drawing.Image]::FromStream($frameStream, $true, $true)
            try {
                return [System.Drawing.Bitmap]::new($image)
            } finally {
                $image.Dispose()
            }
        } finally {
            $frameStream.Dispose()
        }
    } finally {
        $reader.Dispose()
        $stream.Dispose()
    }
}

function Assert-ImageDimensions([string]$Path, [int]$Width, [int]$Height) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "MSIX-Logo fehlt: $([IO.Path]::GetFileName($Path))"
    }
    $image = [System.Drawing.Image]::FromFile($Path)
    try {
        if ($image.Width -ne $Width -or $image.Height -ne $Height) {
            throw "MSIX-Logo hat falsche Dimensionen: $([IO.Path]::GetFileName($Path))"
        }
    } finally {
        $image.Dispose()
    }
}

function Assert-Manifest(
    [string]$ManifestPath,
    [pscustomobject]$Contract,
    [string]$MsixVersion,
    [string]$PackageRoot
) {
    [xml]$manifest = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8
    $namespaces = [Xml.XmlNamespaceManager]::new($manifest.NameTable)
    $namespaces.AddNamespace("f", $foundationNamespace)
    $namespaces.AddNamespace("uap", $uapNamespace)
    $namespaces.AddNamespace("uap10", $uap10Namespace)
    $namespaces.AddNamespace("rescap", $restrictedCapabilityNamespace)

    $identity = $manifest.SelectSingleNode("/f:Package/f:Identity", $namespaces)
    if ($null -eq $identity) { throw "MSIX-Manifest enthält keine Identity." }
    Assert-Exact $identity.GetAttribute("Name") $expectedIdentityName "Identity Name"
    Assert-Exact $identity.GetAttribute("Publisher") $expectedPublisher "Identity Publisher"
    Assert-Exact $identity.GetAttribute("Version") $MsixVersion "Identity Version"
    Assert-Exact $identity.GetAttribute("ProcessorArchitecture") $expectedArchitecture "Identity ProcessorArchitecture"
    if ($identity.GetAttribute("Version") -notmatch '^[0-9]+\.[0-9]+\.[0-9]+\.0$') {
        throw "MSIX-Version ist nicht vierteilig mit vierter Stelle 0."
    }

    $displayName = $manifest.SelectSingleNode("/f:Package/f:Properties/f:DisplayName", $namespaces)
    $publisherDisplayName = $manifest.SelectSingleNode(
        "/f:Package/f:Properties/f:PublisherDisplayName",
        $namespaces
    )
    Assert-Exact $displayName.InnerText $expectedDisplayName "DisplayName"
    Assert-Exact $publisherDisplayName.InnerText $expectedPublisherDisplayName "PublisherDisplayName"

    $targetDeviceFamily = $manifest.SelectSingleNode(
        "/f:Package/f:Dependencies/f:TargetDeviceFamily",
        $namespaces
    )
    Assert-Exact $targetDeviceFamily.GetAttribute("Name") "Windows.Desktop" "TargetDeviceFamily"

    $application = $manifest.SelectSingleNode("/f:Package/f:Applications/f:Application", $namespaces)
    Assert-Exact $application.GetAttribute("Id") $expectedApplicationId "Application Id"
    Assert-Exact $application.GetAttribute("Executable") $Contract.executable "Executable"
    Assert-Exact $application.GetAttribute("RuntimeBehavior", $uap10Namespace) "packagedClassicApp" "RuntimeBehavior"
    Assert-Exact $application.GetAttribute("TrustLevel", $uap10Namespace) "mediumIL" "TrustLevel"
    if (-not (Test-Path -LiteralPath (Join-Path $PackageRoot $Contract.executable) -PathType Leaf)) {
        throw "Das im Manifest konfigurierte Desktop-Executable fehlt."
    }

    $capabilities = @($manifest.SelectNodes("/f:Package/f:Capabilities/*", $namespaces))
    if ($capabilities.Count -ne 1) {
        throw "MSIX darf ausschließlich eine notwendige Capability deklarieren."
    }
    Assert-Exact $capabilities[0].NamespaceURI $restrictedCapabilityNamespace "Capability Namespace"
    Assert-Exact $capabilities[0].GetAttribute("Name") "runFullTrust" "Capability"

    $visualElements = $manifest.SelectSingleNode(
        "/f:Package/f:Applications/f:Application/uap:VisualElements",
        $namespaces
    )
    Assert-Exact $visualElements.GetAttribute("Square44x44Logo") "Assets\Square44x44Logo.png" "Square44x44Logo"
    Assert-Exact $visualElements.GetAttribute("Square150x150Logo") "Assets\Square150x150Logo.png" "Square150x150Logo"
    $packageLogo = $manifest.SelectSingleNode("/f:Package/f:Properties/f:Logo", $namespaces)
    Assert-Exact $packageLogo.InnerText "Assets\StoreLogo.png" "Package Logo"

    $manifestText = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8
    if ($manifestText.Contains($expectedStoreId)) {
        throw "Store-ID darf nicht als Package Identity verwendet werden."
    }
}

function Assert-SafePackageContent([string]$PackageRoot, [string]$ExecutableName) {
    $forbiddenExactNames = @(
        "settings.properties",
        "local.properties",
        "apikeys.properties",
        "session_salt.bin"
    )
    $textExtensions = @(".properties", ".json", ".xml", ".txt", ".cfg", ".config", ".ini", ".yml", ".yaml")
    $secretPatterns = @(
        'sk-[A-Za-z0-9_-]{20,}',
        'AIza[0-9A-Za-z_-]{20,}',
        'ya29\.[0-9A-Za-z_-]+',
        'gh[pousr]_[0-9A-Za-z]{20,}',
        '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----'
    )
    foreach ($file in Get-ChildItem -LiteralPath $PackageRoot -Recurse -File) {
        $name = $file.Name.ToLowerInvariant()
        $relativePath = $file.FullName.Substring($PackageRoot.Length).TrimStart('\')
        if ($forbiddenExactNames -contains $name -or
            $name -eq ".env" -or
            $name.StartsWith(".env.") -or
            $name -match '^desktop-state-.*\.json$' -or
            $name.Contains(".recovery-") -or
            $name.Contains("quarantine") -or
            $file.Extension.ToLowerInvariant() -in @(".pfx", ".p12", ".cer", ".pem", ".key", ".log", ".msi")) {
            throw "Verbotene Datei im MSIX: $relativePath"
        }
        if ($file.DirectoryName -eq $PackageRoot -and
            $file.Extension -ieq ".exe" -and
            -not [string]::Equals($file.Name, $ExecutableName, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Ein EXE-Installer darf nicht in das MSIX eingebettet werden: $relativePath"
        }
        if ($textExtensions -contains $file.Extension.ToLowerInvariant() -and $file.Length -le 5MB) {
            $text = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
            foreach ($pattern in $secretPatterns) {
                if ($text -match $pattern) {
                    throw "Potenzielles Secret im MSIX-Inhalt: $relativePath"
                }
            }
        }
    }
}

if ($PSVersionTable.PSEdition -eq "Core" -and -not $IsWindows) {
    throw "package-store-msix.ps1 kann ausschließlich unter Windows ausgeführt werden."
}

$buildRoot = [IO.Path]::GetFullPath($BuildDirectory)
$appImage = Get-PathWithin $AppImageDirectory $buildRoot "Compose-App-Image"
$outputPath = Get-PathWithin $OutputMsix $buildRoot "MSIX-Ausgabe"
if (-not (Test-Path -LiteralPath $appImage -PathType Container)) {
    throw "Compose-App-Image fehlt: $appImage"
}
foreach ($requiredFile in @($ManifestTemplate, $StoreContract, $SourceIcon)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Erforderliche MSIX-Quelldatei fehlt: $requiredFile"
    }
}

$contract = Get-Content -LiteralPath $StoreContract -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Exact $contract.identityName $expectedIdentityName "Store Identity Name"
Assert-Exact $contract.publisher $expectedPublisher "Store Publisher"
Assert-Exact $contract.publisherDisplayName $expectedPublisherDisplayName "Store PublisherDisplayName"
Assert-Exact $contract.displayName $expectedDisplayName "Store DisplayName"
Assert-Exact $contract.applicationId $expectedApplicationId "Store ApplicationId"
Assert-Exact $contract.architecture $expectedArchitecture "Store Architecture"
Assert-Exact $contract.storeId $expectedStoreId "Store ID"

$msixVersion = Convert-ToMsixVersion $DesktopVersion
$makeAppx = Get-WindowsSdkTool "makeappx.exe"
$workRoot = Join-Path $buildRoot "tmp\storeMsix"
$stagingDirectory = Join-Path $workRoot "staging"
$verificationDirectory = Join-Path $workRoot "verification"
$success = $false

try {
    $stagingDirectory = Reset-Directory $stagingDirectory $buildRoot
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($outputPath)) | Out-Null
    if ([IO.File]::Exists($outputPath)) {
        [IO.File]::Delete($outputPath)
    }
    Copy-Item -Path (Join-Path $appImage "*") -Destination $stagingDirectory -Recurse -Force
    if (-not (Test-Path -LiteralPath (Join-Path $stagingDirectory $contract.executable) -PathType Leaf)) {
        throw "Compose-App-Image enthält nicht $($contract.executable)."
    }

    Add-Type -AssemblyName System.Drawing
    $assetDirectory = Join-Path $stagingDirectory "Assets"
    [IO.Directory]::CreateDirectory($assetDirectory) | Out-Null
    $sourceBitmap = Read-BestIcoBitmap $SourceIcon
    try {
        New-LogoAsset $sourceBitmap 44 (Join-Path $assetDirectory "Square44x44Logo.png")
        New-LogoAsset $sourceBitmap 150 (Join-Path $assetDirectory "Square150x150Logo.png")
        New-LogoAsset $sourceBitmap 50 (Join-Path $assetDirectory "StoreLogo.png")
    } finally {
        $sourceBitmap.Dispose()
    }

    $template = Get-Content -LiteralPath $ManifestTemplate -Raw -Encoding UTF8
    $tokens = [ordered]@{
        "{{IDENTITY_NAME}}" = $contract.identityName
        "{{PUBLISHER}}" = $contract.publisher
        "{{MSIX_VERSION}}" = $msixVersion
        "{{ARCHITECTURE}}" = $contract.architecture
        "{{DISPLAY_NAME}}" = $contract.displayName
        "{{PUBLISHER_DISPLAY_NAME}}" = $contract.publisherDisplayName
        "{{TARGET_DEVICE_FAMILY}}" = $contract.targetDeviceFamily
        "{{MINIMUM_WINDOWS_VERSION}}" = $contract.minimumWindowsVersion
        "{{MAXIMUM_WINDOWS_VERSION_TESTED}}" = $contract.maximumWindowsVersionTested
        "{{APPLICATION_ID}}" = $contract.applicationId
        "{{EXECUTABLE}}" = $contract.executable
    }
    foreach ($token in $tokens.Keys) {
        $escapedValue = [Security.SecurityElement]::Escape([string]$tokens[$token])
        $template = $template.Replace($token, $escapedValue)
    }
    if ($template.Contains("{{")) {
        throw "AppxManifest.xml enthält nicht ersetzte Tokens."
    }
    $manifestPath = Join-Path $stagingDirectory "AppxManifest.xml"
    [IO.File]::WriteAllText($manifestPath, $template, [Text.UTF8Encoding]::new($false))

    Assert-Manifest $manifestPath $contract $msixVersion $stagingDirectory
    Assert-ImageDimensions (Join-Path $assetDirectory "Square44x44Logo.png") 44 44
    Assert-ImageDimensions (Join-Path $assetDirectory "Square150x150Logo.png") 150 150
    Assert-ImageDimensions (Join-Path $assetDirectory "StoreLogo.png") 50 50
    Assert-SafePackageContent $stagingDirectory $contract.executable

    & $makeAppx pack /d $stagingDirectory /p $outputPath /o
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $outputPath -PathType Leaf)) {
        throw "MakeAppx konnte das MSIX nicht erzeugen."
    }

    $verificationDirectory = Reset-Directory $verificationDirectory $buildRoot
    & $makeAppx unpack /p $outputPath /d $verificationDirectory /o
    if ($LASTEXITCODE -ne 0) {
        throw "MakeAppx konnte das erzeugte MSIX nicht entpacken."
    }
    Assert-Manifest (Join-Path $verificationDirectory "AppxManifest.xml") $contract $msixVersion $verificationDirectory
    Assert-ImageDimensions (Join-Path $verificationDirectory "Assets\Square44x44Logo.png") 44 44
    Assert-ImageDimensions (Join-Path $verificationDirectory "Assets\Square150x150Logo.png") 150 150
    Assert-ImageDimensions (Join-Path $verificationDirectory "Assets\StoreLogo.png") 50 50
    Assert-SafePackageContent $verificationDirectory $contract.executable

    $success = $true
    Write-Output "STORE_MSIX_PATH=$outputPath"
    Write-Output "STORE_MSIX_VERSION=$msixVersion"
    Write-Output "MAKEAPPX_PATH=$makeAppx"
} finally {
    foreach ($temporaryDirectory in @($stagingDirectory, $verificationDirectory)) {
        if ($temporaryDirectory -and [IO.Directory]::Exists($temporaryDirectory)) {
            Remove-SafeDirectory $temporaryDirectory $buildRoot
        }
    }
    if (-not $success -and [IO.File]::Exists($outputPath)) {
        [IO.File]::Delete($outputPath)
    }
}
