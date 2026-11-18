$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output = Join-Path $repo 'build/newx-mute-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac).Source }
$executableSuffix = if ([IO.Path]::DirectorySeparatorChar -eq '\') { '.exe' } else { '' }
$json = Join-Path $output 'json-20240303.jar'
if (-not (Test-Path -LiteralPath $json)) {
    Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar' -OutFile $json
}
if ((Get-FileHash -LiteralPath $json -Algorithm SHA256).Hash -ne '3CF6CD6892E32E2B4C1C39E0F52F5248A2F5B37646FDFBB79A66B46B618414ED') { throw 'Unexpected test JSON library checksum.' }
$sources = @(Get-ChildItem (Join-Path $repo 'extensions/newx/src/main/java/app/morphe/extension/newx/mute/keywordmute') -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem (Join-Path $repo 'extensions/newx/src/main/java/app/morphe/extension/newx/mute/accountmute') -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem (Join-Path $PSScriptRoot 'stubs') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem $PSScriptRoot -Filter '*Test.java' | ForEach-Object FullName)
$sources += Join-Path $repo 'extensions/newx/src/main/java/app/morphe/extension/newx/timeline/ScheduledMute.java'
$sources += Join-Path $repo 'extensions/newx/src/main/java/app/morphe/extension/newx/timeline/TimelineModelAccess.java'
& (Join-Path $javaBin "javac$executableSuffix") -encoding UTF-8 -cp $json -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
foreach ($test in @('KeywordMuteTest', 'KeywordMuteStoreTest', 'AccountStoreTest', 'app.morphe.extension.newx.timeline.ScheduledMuteTest')) {
    & (Join-Path $javaBin "java$executableSuffix") -cp "$output$([IO.Path]::PathSeparator)$json" $test
    if ($LASTEXITCODE -ne 0) { throw "$test failed." }
}
