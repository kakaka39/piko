param([string]$AndroidSdk = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$compiled = 'build\intermediates\javac\release\compileReleaseJavaWithJavac\classes'
$twitter = Join-Path $repoRoot "extensions\twitter\$compiled"
$stub = Join-Path $repoRoot "extensions\twitter\stub\$compiled"
$shared = Join-Path $repoRoot "extensions\shared\library\$compiled"
$androidJar = Get-ChildItem (Join-Path $AndroidSdk 'platforms\android-*\android.jar') | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $androidJar -or -not (Test-Path (Join-Path $twitter 'app\morphe\extension\twitter\patches\accountmute\AccountMute.class'))) {
    throw 'Run buildAndroid first and supply -AndroidSdk if needed.'
}
$outputRoot = Join-Path $repoRoot 'build\account-runtime-tests'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$testClasspath = "$twitter;$stub;$shared;$androidJar;$outputRoot"
& javac -encoding UTF-8 -cp $testClasspath -d $outputRoot (Join-Path $PSScriptRoot 'PatchGuardTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Patch guard compilation failed.' }
& java -cp $testClasspath PatchGuardTest
if ($LASTEXITCODE -ne 0) { throw 'Patch guard tests failed.' }
