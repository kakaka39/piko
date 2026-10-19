$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
& (Join-Path $repoRoot 'tests\keyword-mute\run.ps1')
$outputRoot = Join-Path $repoRoot 'build\keyword-mute-tests'
$jsonJar = Join-Path $outputRoot 'dependencies\json-20240303.jar'
$sourceRoot = Join-Path $repoRoot 'extensions\twitter\src\main\java\app\morphe\extension\twitter\patches'
$sources = @('accountmute\AccountTargets.java','accountmute\AccountExtractor.java','accountmute\AccountMuteStore.java','accountmute\AccountPostCache.java','keywordmute\MuteEntryIds.java') | ForEach-Object { Join-Path $sourceRoot $_ }
$fixtures = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'fixtures') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac -encoding UTF-8 -cp "$outputRoot;$jsonJar" -d $outputRoot @sources @fixtures (Join-Path $PSScriptRoot 'AccountMuteTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Account test compilation failed.' }
& java -cp "$outputRoot;$jsonJar" AccountMuteTest
if ($LASTEXITCODE -ne 0) { throw 'Account tests failed.' }
