param([string]$Ref = '822390417772d3003a61a09ae5f9ae1c638dedb1', [string]$Output = "$PSScriptRoot\..\dist")
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath("$PSScriptRoot\..")
$buildRoot = Join-Path $projectRoot ('verification\rollback-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $buildRoot -Force | Out-Null
Push-Location $projectRoot
try {
    & git archive --format=zip --output="$buildRoot\source.zip" $Ref
    if ($LASTEXITCODE -ne 0) { throw 'V2 source ref is unavailable.' }
    Expand-Archive -LiteralPath "$buildRoot\source.zip" -DestinationPath "$buildRoot\src"
    $gradlePath = "$buildRoot\src\app\build.gradle.kts"
    $text = Get-Content -LiteralPath $gradlePath -Raw
    if (!$text.Contains('versionCode = 200')) { throw 'Unexpected V2 source version; preserve it and inspect before building.' }
    $text = $text.Replace('versionCode = 200', 'versionCode = 201').Replace('versionName = "2.0.0"', 'versionName = "2.0.0-rollback"')
    [IO.File]::WriteAllText($gradlePath, $text, [Text.UTF8Encoding]::new($false))
    $sdkPath = "$env:USERPROFILE/.cache/shiguang-build/sdk".Replace('\','/')
    [IO.File]::WriteAllText("$buildRoot\src\local.properties", "sdk.dir=$sdkPath", [Text.UTF8Encoding]::new($false))
    & "$buildRoot\src\tools\build-release.ps1" -Output "$buildRoot\output"
    if ($LASTEXITCODE -ne 0) { throw 'Rollback APK build failed.' }
    New-Item -ItemType Directory -Path $Output -Force | Out-Null
    Copy-Item -LiteralPath "$buildRoot\output\拾光主题-V2.0.apk" -Destination "$Output\拾光主题-V2.0-回退包.apk"
} finally { Pop-Location }
