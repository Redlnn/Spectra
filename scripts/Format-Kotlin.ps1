param([switch]$Check)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$version = '0.64'
$toolFolder = Join-Path $project '.gradle/tools'
$jar = Join-Path $toolFolder "ktfmt-$version.jar"
if(-not (Test-Path -LiteralPath $jar)) {
    New-Item -ItemType Directory -Force -Path $toolFolder | Out-Null
    Invoke-WebRequest "https://repo.maven.apache.org/maven2/com/facebook/ktfmt/$version/ktfmt-$version-with-dependencies.jar" -OutFile $jar
}
$javaExe = if($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
$sources = @(Get-ChildItem -LiteralPath (Join-Path $project 'app/src') -Recurse -Filter '*.kt' | Select-Object -ExpandProperty FullName)
$sources += @('app/build.gradle.kts','build.gradle.kts','settings.gradle.kts') | ForEach-Object { Join-Path $project $_ }
$arguments = @('--kotlinlang-style')
if($Check) { $arguments += @('--dry-run','--set-exit-if-changed') }
& $javaExe -jar $jar @arguments @sources
if($LASTEXITCODE -ne 0) { throw "Kotlin format check failed (exit $LASTEXITCODE)" }
