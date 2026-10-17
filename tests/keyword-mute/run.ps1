$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$sourceRoot = Join-Path $repoRoot 'extensions\twitter\src\main\java\app\morphe\extension\twitter\patches\keywordmute'
$outputRoot = Join-Path $repoRoot 'build\keyword-mute-tests'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
& javac -encoding UTF-8 -d $outputRoot (Join-Path $sourceRoot 'KeywordMuteRule.java') (Join-Path $sourceRoot 'KeywordMuteEngine.java') (Join-Path $PSScriptRoot 'KeywordMuteTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& java -cp $outputRoot KeywordMuteTest
if ($LASTEXITCODE -ne 0) { throw 'Keyword mute regression tests failed.' }

# Test-only JSON runtime; no changes to the app dependencies or persisted credentials.
$dependencyRoot = Join-Path $outputRoot 'dependencies'
New-Item -ItemType Directory -Force -Path $dependencyRoot | Out-Null
$jsonJar = Join-Path $dependencyRoot 'json-20240303.jar'
if (-not (Test-Path -LiteralPath $jsonJar)) {
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar' -OutFile $jsonJar
}
if ((Get-FileHash -LiteralPath $jsonJar -Algorithm SHA256).Hash -ne '3CF6CD6892E32E2B4C1C39E0F52F5248A2F5B37646FDFBB79A66B46B618414ED') {
    throw 'Unexpected JSON test library checksum.'
}
$testStubs = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'stubs') -Filter '*.java' -Recurse | ForEach-Object FullName)
& javac -encoding UTF-8 -cp "$outputRoot;$jsonJar" -d $outputRoot @testStubs (Join-Path $sourceRoot 'KeywordMuteStore.java') (Join-Path $PSScriptRoot 'KeywordMuteStoreTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Storage test compilation failed.' }
& java -cp "$outputRoot;$jsonJar" KeywordMuteStoreTest
if ($LASTEXITCODE -ne 0) { throw 'Keyword mute storage tests failed.' }
