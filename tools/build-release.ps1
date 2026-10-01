param([string]$Toolchain = "$env:USERPROFILE\.cache\shiguang-build", [string]$Output = "$PSScriptRoot\..\dist")
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = (Get-ChildItem -LiteralPath "$Toolchain\jdk" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = "$Toolchain\sdk"
$signDir = "$env:USERPROFILE\.codex\signing\shiguang-theme"
New-Item -ItemType Directory -Force -Path $signDir,$Output | Out-Null
$keyPath = "$signDir\shiguang-release.jks"
$passPath = "$signDir\password.dpapi"
if (!(Test-Path -LiteralPath $keyPath)) {
    $password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    ConvertTo-SecureString $password -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $passPath
} else {
    if (!(Test-Path -LiteralPath $passPath)) { throw 'Signing password is missing. Preserve the existing key; do not replace it.' }
    $secret = Get-Content -LiteralPath $passPath | ConvertTo-SecureString
    $handle = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($handle) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($handle) }
}
$env:SHIGUANG_KEYSTORE = $keyPath
$env:SHIGUANG_STORE_PASSWORD = $password
$env:SHIGUANG_KEY_PASSWORD = $password
try {
    if (!(Test-Path -LiteralPath $keyPath)) {
        & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore $keyPath -storetype JKS -storepass:env SHIGUANG_STORE_PASSWORD -keypass:env SHIGUANG_KEY_PASSWORD -alias shiguang -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Shiguang, O=Shiguang Theme, C=CN'
        if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed.' }
    }
    Push-Location "$PSScriptRoot\.."
    try {
        & "$Toolchain\gradle\gradle-8.9\bin\gradle.bat" assembleRelease lintRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
        Copy-Item -LiteralPath 'app\build\outputs\apk\release\app-release.apk' -Destination "$Output\拾光主题-V2.0.1.apk"
        & "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs "$Output\拾光主题-V2.0.1.apk"
        if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    } finally { Pop-Location }
} finally {
    Remove-Item Env:SHIGUANG_STORE_PASSWORD,Env:SHIGUANG_KEY_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
}
