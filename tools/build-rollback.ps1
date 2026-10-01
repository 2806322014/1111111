param([string]$Ref = 'shiguang-v1.0', [string]$Output = "$PSScriptRoot\..\dist")
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath("$PSScriptRoot\..")
$buildRoot = Join-Path $projectRoot ('verification\rollback-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $buildRoot -Force | Out-Null
Push-Location $projectRoot
try {
    & git archive --format=zip --output="$buildRoot\source.zip" $Ref
    if ($LASTEXITCODE -ne 0) { throw 'V1 source ref is unavailable.' }
    Expand-Archive -LiteralPath "$buildRoot\source.zip" -DestinationPath "$buildRoot\src"
    $gradlePath = "$buildRoot\src\app\build.gradle.kts"
    $text = Get-Content -LiteralPath $gradlePath -Raw
    if (!$text.Contains('versionCode = 100')) { throw 'Unexpected V1 source version; preserve it and inspect before building.' }
    $text = $text.Replace('versionCode = 100', 'versionCode = 200').Replace('versionName = "1.0.0"', 'versionName = "1.0.0-rollback"')
    [IO.File]::WriteAllText($gradlePath, $text, [Text.UTF8Encoding]::new($false))
    $sdkPath = "$env:USERPROFILE/.cache/shiguang-build/sdk".Replace('\','/')
    [IO.File]::WriteAllText("$buildRoot\src\local.properties", "sdk.dir=$sdkPath", [Text.UTF8Encoding]::new($false))
    & "$buildRoot\src\tools\build-release.ps1" -Output "$buildRoot\output"
    if ($LASTEXITCODE -ne 0) { throw 'Rollback APK build failed.' }
    New-Item -ItemType Directory -Path $Output -Force | Out-Null
    Copy-Item -LiteralPath "$buildRoot\output\拾光主题-V1.0.apk" -Destination "$Output\拾光主题-V1.0-回退包.apk"
} finally { Pop-Location }
